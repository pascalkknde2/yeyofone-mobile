use super::*;
use yeyofone_application::calls::{CallMachine, CallObservation, CallStatus, call_destination};
struct Session {
    token: u64,
    machine: CallMachine,
}
pub(super) struct Calls {
    sessions: BTreeMap<String, Session>,
    next: u64,
    origin: Instant,
    pub(super) audio: bool,
}
impl Calls {
    pub fn new() -> Self {
        Self {
            sessions: BTreeMap::new(),
            next: 1,
            origin: Instant::now(),
            audio: true,
        }
    }
    fn now(&self) -> u64 {
        self.origin.elapsed().as_millis() as u64
    }
    pub fn active_account(&self, id: &str) -> bool {
        self.sessions
            .values()
            .any(|s| s.machine.status.account_id == id && !s.machine.terminal())
    }
    pub(super) fn snapshot(&mut self) -> Vec<CallStatus> {
        let now = self.now();
        self.sessions
            .values_mut()
            .map(|s| {
                s.machine.refresh(now);
                s.machine.status.clone()
            })
            .collect()
    }
    pub fn command(
        &mut self,
        native: &mut ffi::Native,
        registrations: &Registrations,
        op: Operation,
    ) -> Result<Vec<CallStatus>, EngineError> {
        let now = self.now();
        match op {
            Operation::Calls => {}
            Operation::Control(id, action, input, consult_id) => {
                let s = self.sessions.get(&id).ok_or(EngineError::Unavailable)?;
                if s.machine.status.state != "connected" || s.machine.status.transfer_pending {
                    return Err(EngineError::InvalidTransition);
                }
                let token = s.token;
                let account = s.machine.status.account_id.clone();
                match action.as_str() {
                    "hold" | "resume" => {
                        if self.sessions.values().any(|x| {
                            !x.machine.terminal()
                                && x.machine.status.consult_parent_id.as_deref() == Some(&id)
                        }) {
                            return Err(EngineError::Busy);
                        }
                        native
                            .hold(token, action == "hold")
                            .map_err(EngineError::Native)?;
                    }
                    "transfer" | "consult_start" => {
                        if self.sessions.values().any(|x| {
                            (!x.machine.terminal() && x.machine.status.id != id)
                                || x.machine.status.consult_parent_id.as_deref() == Some(&id)
                        }) {
                            return Err(EngineError::Busy);
                        }
                        let registration = registrations
                            .accounts
                            .get(&account)
                            .ok_or(EngineError::Unavailable)?;
                        let config = registration
                            .account
                            .as_ref()
                            .ok_or(EngineError::Unavailable)?;
                        let uri = call_destination(config, &input)
                            .map_err(|_| EngineError::Native(-6))?;
                        if uri.len() > 512 {
                            return Err(EngineError::Native(-6));
                        }
                        if action == "transfer" {
                            native
                                .transfer(token, &uri, 0)
                                .map_err(EngineError::Native)?;
                        } else {
                            if consult_id.is_empty()
                                || consult_id.len() > 64
                                || !consult_id
                                    .bytes()
                                    .all(|b| b.is_ascii_alphanumeric() || b == b'-')
                                || self.sessions.contains_key(&consult_id)
                                || self.sessions.len() >= 128
                            {
                                return Err(EngineError::Native(-6));
                            }
                            native.hold(token, true).map_err(EngineError::Native)?;
                            let child_token = self.next;
                            self.next = self.next.checked_add(1).ok_or(EngineError::Unavailable)?;
                            if let Err(code) =
                                native.dial(registration.token, child_token, &uri, self.audio)
                            {
                                let mut machine =
                                    CallMachine::new(consult_id.clone(), account, input, now);
                                machine.status.consult_parent_id = Some(id);
                                machine.finish("native_failure", now);
                                self.sessions.insert(
                                    consult_id,
                                    Session {
                                        token: child_token,
                                        machine,
                                    },
                                );
                                self.reconcile_consultations(native, now);
                                return Err(EngineError::Native(code));
                            }
                            let mut machine =
                                CallMachine::new(consult_id.clone(), account, input, now);
                            machine.status.consult_parent_id = Some(id);
                            self.sessions.insert(
                                consult_id,
                                Session {
                                    token: child_token,
                                    machine,
                                },
                            );
                        }
                    }
                    "consult_complete" | "consult_cancel" => {
                        let child = self
                            .sessions
                            .get(&consult_id)
                            .ok_or(EngineError::Unavailable)?;
                        if child.machine.status.consult_parent_id.as_deref() != Some(&id)
                            || child.machine.terminal()
                        {
                            return Err(EngineError::InvalidTransition);
                        }
                        let child_token = child.token;
                        if action == "consult_complete" {
                            if child.machine.status.state != "connected" {
                                return Err(EngineError::InvalidTransition);
                            }
                            native
                                .transfer(token, "", child_token)
                                .map_err(EngineError::Native)?;
                        } else {
                            native.hangup(child_token).map_err(EngineError::Native)?;
                            self.sessions
                                .get_mut(&consult_id)
                                .unwrap()
                                .machine
                                .cancel(now);
                            // Resume after the consultation actually disconnects in poll().
                        }
                    }
                    _ => return Err(EngineError::InvalidTransition),
                }
            }
            Operation::Dial(id, account, input) => {
                if id.is_empty()
                    || id.len() > 64
                    || !id.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-')
                {
                    return Err(EngineError::Native(-6));
                }
                if let Some(s) = self.sessions.get(&id) {
                    if s.machine.status.account_id != account
                        || s.machine.status.destination != input
                    {
                        return Err(EngineError::Native(-6));
                    }
                    return Ok(self.snapshot());
                }
                if self.sessions.values().any(|s| !s.machine.terminal())
                    || self.sessions.len() >= 128
                {
                    return Err(EngineError::Busy);
                }
                let registration = registrations
                    .accounts
                    .get(&account)
                    .ok_or(EngineError::Unavailable)?;
                if registration.token == 0
                    || registration
                        .machine
                        .snapshot(&account, registrations.now())
                        .state
                        != "registered"
                {
                    return Err(EngineError::Unavailable);
                }
                let config = registration
                    .account
                    .as_ref()
                    .ok_or(EngineError::Unavailable)?;
                let uri = call_destination(config, &input).map_err(|_| EngineError::Native(-6))?;
                if uri.len() > 512 {
                    return Err(EngineError::Native(-6));
                }
                let token = self.next;
                self.next = self.next.checked_add(1).ok_or(EngineError::Unavailable)?;
                let mut machine = CallMachine::new(id.clone(), account, input, now);
                if let Err(code) = native.dial(registration.token, token, &uri, self.audio) {
                    machine.finish(
                        if code == -11 {
                            "audio_unavailable"
                        } else {
                            "native_failure"
                        },
                        now,
                    );
                }
                self.sessions.insert(id, Session { token, machine });
            }
            Operation::Hangup(id) => {
                let s = self.sessions.get_mut(&id).ok_or(EngineError::Unavailable)?;
                if !s.machine.terminal() && s.machine.cancelling.is_none() {
                    native.hangup(s.token).map_err(EngineError::Native)?;
                    s.machine.cancel(now);
                }
            }
            Operation::Answer(id) => {
                let s = self.sessions.get_mut(&id).ok_or(EngineError::Unavailable)?;
                if s.machine.status.direction != "incoming" || s.machine.status.state != "incoming"
                {
                    return Err(EngineError::InvalidTransition);
                }
                native.answer(s.token).map_err(EngineError::Native)?;
            }
            Operation::Reject(id) => {
                let s = self.sessions.get_mut(&id).ok_or(EngineError::Unavailable)?;
                if s.machine.status.direction != "incoming" || s.machine.terminal() {
                    return Err(EngineError::InvalidTransition);
                }
                native.reject(s.token, 603).map_err(EngineError::Native)?;
                s.machine.status.reason = Some("declined".into());
                s.machine.cancel(now);
            }

            Operation::RecordStart(id, path) => {
                let s = self.sessions.get_mut(&id).ok_or(EngineError::Unavailable)?;
                if s.machine.terminal()
                    || s.machine.status.state != "connected"
                    || s.machine.status.recording
                {
                    return Err(EngineError::InvalidTransition);
                }
                native
                    .start_recording(s.token, &path)
                    .map_err(EngineError::Native)?;
                s.machine.status.recording = true;
            }
            Operation::RecordStop(id) => {
                let s = self.sessions.get_mut(&id).ok_or(EngineError::Unavailable)?;
                if !s.machine.status.recording {
                    return Err(EngineError::InvalidTransition);
                }
                native
                    .stop_recording(s.token)
                    .map_err(EngineError::Native)?;
                s.machine.status.recording = false;
            }
            Operation::Dtmf(id, digit) => {
                let s = self.sessions.get(&id).ok_or(EngineError::Unavailable)?;
                if s.machine.status.state != "connected" || s.machine.status.held {
                    return Err(EngineError::InvalidTransition);
                }
                native.dtmf(s.token, digit).map_err(EngineError::Native)?;
            }
            Operation::Mute(id, value) => {
                let s = self.sessions.get_mut(&id).ok_or(EngineError::Unavailable)?;
                if !s.machine.terminal() && s.machine.status.muted != value {
                    native.mute(s.token, value).map_err(EngineError::Native)?;
                    s.machine.status.muted = value;
                }
            }
            _ => return Err(EngineError::InvalidTransition),
        }
        self.poll(native, registrations);
        Ok(self.snapshot())
    }
    pub fn poll(&mut self, native: &mut ffi::Native, registrations: &Registrations) {
        let now = self.now();
        let seen = self
            .sessions
            .values()
            .map(|s| s.token)
            .collect::<std::collections::HashSet<_>>();
        if let Ok(native_calls) = native.calls() {
            for event in native_calls {
                if event.incoming != 0 && !seen.contains(&event.token) {
                    let account_id = registrations
                        .accounts
                        .iter()
                        .find(|(_, a)| a.token == event.account_token)
                        .map(|(id, _)| id.clone());
                    if let Some(account_id) = account_id {
                        let id = format!("incoming-{:016x}", event.token);
                        let caller = std::str::from_utf8(
                            &event.caller[..(event.caller_len.max(0) as usize).min(65)],
                        )
                        .ok()
                        .filter(|s| !s.is_empty())
                        .unwrap_or("Unknown caller")
                        .to_string();
                        let mut machine =
                            CallMachine::incoming(id.clone(), account_id, caller, now);
                        machine.observe(
                            CallObservation {
                                state: event.state,
                                code: event.sip_code,
                                audio: false,
                                error: false,
                                muted: false,
                                connected_ms: 0,
                                media_active: false,
                                recording: false,
                                failure_kind: 0,
                                incoming: true,
                                caller: String::new(),
                            },
                            now,
                        );
                        self.sessions.insert(
                            id,
                            Session {
                                token: event.token,
                                machine,
                            },
                        );
                    }
                }
            }
        }
        for s in self.sessions.values_mut() {
            if s.machine.terminal() {
                continue;
            }
            match native.call(s.token) {
                Ok(e) => s.machine.observe(
                    CallObservation {
                        state: e.state,
                        code: e.sip_code,
                        audio: e.audio_active != 0,
                        error: e.audio_error != 0,
                        muted: e.muted != 0,
                        connected_ms: e.connected_ms,
                        media_active: e.media_active != 0,
                        recording: e.recording != 0,
                        failure_kind: e.failure_kind,
                        incoming: e.incoming != 0,
                        caller: std::str::from_utf8(
                            &e.caller[..(e.caller_len.max(0) as usize).min(65)],
                        )
                        .unwrap_or("")
                        .to_string(),
                    },
                    now,
                ),
                Err(_) => s.machine.finish("engine_failure", now),
            }
            if !s.machine.terminal()
                && let Ok(c) = native.controls(s.token)
            {
                s.machine.status.held = c.held != 0;
                s.machine.status.transfer_pending = c.transfer_pending != 0;
                s.machine.status.transfer_code = c.transfer_code;
                if !s.machine.status.transfer_pending
                    && (200..300).contains(&c.transfer_code)
                    && s.machine.cancelling.is_none()
                    && native.hangup(s.token).is_ok()
                {
                    s.machine.cancel(now);
                }
            }
            if s.machine.status.direction == "incoming"
                && s.machine.status.state == "incoming"
                && now.saturating_sub(s.machine.started) >= 30_000
            {
                let _ = native.reject(s.token, 480);
                s.machine.status.reason = Some("timeout".into());
                s.machine.cancel(now);
            }
            if s.machine.status.direction == "outgoing"
                && !s.machine.terminal()
                && s.machine.cancelling.is_none()
                && s.machine.status.state != "connected"
                && now.saturating_sub(s.machine.started) >= 120_000
            {
                let _ = native.hangup(s.token);
                s.machine.cancel(now);
                s.machine.status.reason = Some("timeout".into());
            }
            if s.machine
                .cancelling
                .is_some_and(|start| now.saturating_sub(start) >= 5000)
            {
                s.machine.finish("termination_timeout", now);
            }
            if s.machine.terminal() {
                let _ = native.release_call(s.token);
            }
        }
        self.reconcile_consultations(native, now);
    }
    fn reconcile_consultations(&mut self, native: &mut ffi::Native, now: u64) {
        let pairs: Vec<_> = self
            .sessions
            .iter()
            .filter_map(|(id, s)| {
                s.machine
                    .status
                    .consult_parent_id
                    .clone()
                    .map(|parent| (id.clone(), parent))
            })
            .collect();
        for (id, parent_id) in pairs {
            let Some(parent) = self.sessions.get(&parent_id) else {
                continue;
            };
            let parent_ended = parent.machine.terminal() || parent.machine.cancelling.is_some();
            let parent_token = parent.token;
            let parent_held = parent.machine.status.held;
            let child = self.sessions.get_mut(&id).unwrap();
            if (parent_ended || !parent_held)
                && !child.machine.terminal()
                && child.machine.cancelling.is_none()
                && native.hangup(child.token).is_ok()
            {
                child.machine.cancel(now);
            }
            if child.machine.terminal() {
                if parent_ended || !parent_held {
                    child.machine.status.consult_parent_id = None;
                } else if native.hold(parent_token, false).is_ok() {
                    child.machine.status.consult_parent_id = None;
                    self.sessions
                        .get_mut(&parent_id)
                        .unwrap()
                        .machine
                        .status
                        .held = false;
                }
            }
        }
    }
    pub fn shutdown(&mut self, native: &mut ffi::Native, registrations: &Registrations) {
        for s in self.sessions.values_mut() {
            if !s.machine.terminal() {
                let _ = native.hangup(s.token);
            }
        }
        let until = Instant::now() + Duration::from_millis(1000);
        while Instant::now() < until && self.sessions.values().any(|s| !s.machine.terminal()) {
            let _ = native.pump();
            self.poll(native, registrations);
            thread::sleep(Duration::from_millis(10));
        }
        let now = self.now();
        for s in self.sessions.values_mut() {
            if !s.machine.terminal() {
                let _ = native.release_call(s.token);
                s.machine.finish("engine_stopped", now);
            }
        }
    }
}
