//! Serialized PJSUA2 engine, registration and outgoing calls.
mod calls;
#[allow(unsafe_code)]
mod ffi;
use std::collections::BTreeMap;
use std::time::Instant;
use std::{
    sync::{Arc, Mutex, mpsc},
    thread,
    time::Duration,
};
use yeyofone_application::ApplicationState;
use yeyofone_application::{
    accounts::Account,
    registration::{
        RegistrationFailure, RegistrationMachine, RegistrationObservation, RegistrationStatus,
    },
};
use yeyofone_domain::SipCredentials;
use yeyofone_domain::{DomainError, EngineState, SipEngine, TransportEvent, TransportState};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EngineError {
    Busy,
    Unavailable,
    Timeout,
    Native(i32),
    InvalidTransition,
}
impl std::fmt::Display for EngineError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "Engine error: {self:?}")
    }
}
impl std::error::Error for EngineError {}
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Snapshot {
    pub state: EngineState,
    pub sequence: u64,
    pub last_transport: Option<TransportEvent>,
}
enum Operation {
    Start,
    Stop,
    Snapshot,
    Shutdown,
    Configure {
        account: Account,
        credentials: SipCredentials,
    },
    Registrations,
    Register(String),
    Unregister(String),
    Remove(String),
    AccountFailure(String, RegistrationFailure),
    Calls,
    Dial(String, String, String),
    Hangup(String),
    Mute(String, bool),
    Answer(String),
    Reject(String),
    RecordStart(String, String),
    RecordStop(String),
    Dtmf(String, char),
    Control(String, String, String, String),
}
enum Reply {
    Engine(Snapshot),
    Registrations(Vec<RegistrationStatus>),
    Calls(Vec<yeyofone_application::calls::CallStatus>),
}
struct Message {
    operation: Operation,
    reply: mpsc::Sender<Result<Reply, EngineError>>,
}
struct Control {
    sender: mpsc::SyncSender<Message>,
    worker: Mutex<Option<thread::JoinHandle<()>>>,
}
impl Drop for Control {
    fn drop(&mut self) {
        let (reply, _) = mpsc::channel();
        let _ = self.sender.send(Message {
            operation: Operation::Shutdown,
            reply,
        });
        if let Ok(worker) = self.worker.get_mut()
            && let Some(worker) = worker.take()
        {
            let _ = worker.join();
        }
    }
}
#[derive(Clone)]
pub struct PjsipEngine {
    control: Arc<Control>,
}
impl PjsipEngine {
    pub fn new() -> Result<Self, EngineError> {
        Self::spawn(0)
    }
    fn spawn(port: u16) -> Result<Self, EngineError> {
        let (sender, receiver) = mpsc::sync_channel::<Message>(16);
        let (ready, ready_result) = mpsc::channel();
        let worker = thread::Builder::new()
            .name("yeyofone-voip".into())
            .spawn(move || {
                let native = ffi::Native::create().map_err(EngineError::Native);
                match native {
                    Ok(native) => {
                        let _ = ready.send(Ok(()));
                        owner_loop(native, receiver, port);
                    }
                    Err(error) => {
                        let _ = ready.send(Err(error));
                    }
                }
            })
            .map_err(|_| EngineError::Unavailable)?;
        match ready_result
            .recv_timeout(Duration::from_secs(10))
            .map_err(|_| EngineError::Unavailable)?
        {
            Ok(()) => Ok(Self {
                control: Arc::new(Control {
                    sender,
                    worker: Mutex::new(Some(worker)),
                }),
            }),
            Err(error) => {
                let _ = worker.join();
                Err(error)
            }
        }
    }
    fn request(&self, operation: Operation) -> Result<Reply, EngineError> {
        let (reply, response) = mpsc::channel();
        self.control
            .sender
            .try_send(Message { operation, reply })
            .map_err(|error| match error {
                mpsc::TrySendError::Full(_) => EngineError::Busy,
                mpsc::TrySendError::Disconnected(_) => EngineError::Unavailable,
            })?;
        response
            .recv_timeout(Duration::from_secs(10))
            .map_err(|error| match error {
                mpsc::RecvTimeoutError::Timeout => EngineError::Timeout,
                mpsc::RecvTimeoutError::Disconnected => EngineError::Unavailable,
            })?
    }
    fn engine_request(&self, operation: Operation) -> Result<Snapshot, EngineError> {
        match self.request(operation)? {
            Reply::Engine(s) => Ok(s),
            _ => Err(EngineError::Unavailable),
        }
    }
    fn registration_request(
        &self,
        operation: Operation,
    ) -> Result<Vec<RegistrationStatus>, EngineError> {
        match self.request(operation)? {
            Reply::Registrations(s) => Ok(s),
            _ => Err(EngineError::Unavailable),
        }
    }
    pub fn call_control(
        &self,
        id: &str,
        action: &str,
        destination: &str,
        consultation: &str,
    ) -> Result<Vec<yeyofone_application::calls::CallStatus>, EngineError> {
        self.call_request(Operation::Control(
            id.into(),
            action.into(),
            destination.into(),
            consultation.into(),
        ))
    }
    pub fn calls(&self) -> Result<Vec<yeyofone_application::calls::CallStatus>, EngineError> {
        self.call_request(Operation::Calls)
    }
    pub fn dial(
        &self,
        id: &str,
        account: &str,
        destination: &str,
    ) -> Result<Vec<yeyofone_application::calls::CallStatus>, EngineError> {
        self.call_request(Operation::Dial(
            id.into(),
            account.into(),
            destination.into(),
        ))
    }
    pub fn hangup(
        &self,
        id: &str,
    ) -> Result<Vec<yeyofone_application::calls::CallStatus>, EngineError> {
        self.call_request(Operation::Hangup(id.into()))
    }
    pub fn answer(
        &self,
        id: &str,
    ) -> Result<Vec<yeyofone_application::calls::CallStatus>, EngineError> {
        self.call_request(Operation::Answer(id.into()))
    }
    pub fn reject(
        &self,
        id: &str,
    ) -> Result<Vec<yeyofone_application::calls::CallStatus>, EngineError> {
        self.call_request(Operation::Reject(id.into()))
    }
    pub fn mute(
        &self,
        id: &str,
        muted: bool,
    ) -> Result<Vec<yeyofone_application::calls::CallStatus>, EngineError> {
        self.call_request(Operation::Mute(id.into(), muted))
    }
    pub fn start_recording(
        &self,
        id: &str,
        path: &str,
    ) -> Result<Vec<yeyofone_application::calls::CallStatus>, EngineError> {
        self.call_request(Operation::RecordStart(id.into(), path.into()))
    }
    pub fn stop_recording(
        &self,
        id: &str,
    ) -> Result<Vec<yeyofone_application::calls::CallStatus>, EngineError> {
        self.call_request(Operation::RecordStop(id.into()))
    }
    pub fn send_dtmf(
        &self,
        id: &str,
        digit: char,
    ) -> Result<Vec<yeyofone_application::calls::CallStatus>, EngineError> {
        self.call_request(Operation::Dtmf(id.into(), digit))
    }
    fn call_request(
        &self,
        op: Operation,
    ) -> Result<Vec<yeyofone_application::calls::CallStatus>, EngineError> {
        match self.request(op)? {
            Reply::Calls(rows) => Ok(rows),
            _ => Err(EngineError::Unavailable),
        }
    }
    pub fn configure_account(
        &self,
        account: Account,
        credentials: SipCredentials,
    ) -> Result<Vec<RegistrationStatus>, EngineError> {
        account.validate().map_err(|_| EngineError::Native(-6))?;
        self.registration_request(Operation::Configure {
            account,
            credentials,
        })
    }
    pub fn registrations(&self) -> Result<Vec<RegistrationStatus>, EngineError> {
        self.registration_request(Operation::Registrations)
    }
    pub fn register_account(&self, id: &str) -> Result<Vec<RegistrationStatus>, EngineError> {
        self.registration_request(Operation::Register(id.into()))
    }
    pub fn unregister_account(&self, id: &str) -> Result<Vec<RegistrationStatus>, EngineError> {
        self.registration_request(Operation::Unregister(id.into()))
    }
    pub fn remove_account(&self, id: &str) -> Result<Vec<RegistrationStatus>, EngineError> {
        self.registration_request(Operation::Remove(id.into()))
    }
    pub fn account_failure(
        &self,
        id: &str,
        error: RegistrationFailure,
    ) -> Result<Vec<RegistrationStatus>, EngineError> {
        self.registration_request(Operation::AccountFailure(id.into(), error))
    }
    pub fn snapshot(&self) -> Result<Snapshot, EngineError> {
        self.engine_request(Operation::Snapshot)
    }
    pub fn start_engine(&self) -> Result<Snapshot, EngineError> {
        self.engine_request(Operation::Start)
    }
    pub fn stop_engine(&self) -> Result<Snapshot, EngineError> {
        self.engine_request(Operation::Stop)
    }
    pub fn shutdown(&self) -> Result<Snapshot, EngineError> {
        let result = self.engine_request(Operation::Shutdown);
        if let Ok(mut worker) = self.control.worker.lock()
            && let Some(worker) = worker.take()
        {
            worker.join().map_err(|_| EngineError::Unavailable)?;
        }
        result
    }
}
impl SipEngine for PjsipEngine {
    fn start(&mut self) -> Result<(), DomainError> {
        self.start_engine()
            .map(|_| ())
            .map_err(|_| DomainError::Unavailable)
    }
    fn stop(&mut self) -> Result<(), DomainError> {
        self.stop_engine()
            .map(|_| ())
            .map_err(|_| DomainError::Unavailable)
    }
}
struct Owner {
    application: ApplicationState,
    sequence: u64,
    last_transport: Option<TransportEvent>,
    registrations: Registrations,
    calls: calls::Calls,
}
impl Owner {
    fn transition(&mut self, next: EngineState) -> Result<(), EngineError> {
        self.application
            .apply_engine_state(next)
            .map_err(|_| EngineError::InvalidTransition)?;
        self.sequence += 1;
        Ok(())
    }
    fn snapshot(&self) -> Snapshot {
        Snapshot {
            state: self.application.engine(),
            sequence: self.sequence,
            last_transport: self.last_transport,
        }
    }
    fn stop(&mut self, native: &mut ffi::Native) -> Result<Snapshot, EngineError> {
        if self.application.engine() == EngineState::Stopped {
            return Ok(self.snapshot());
        }
        if self.application.engine() == EngineState::Failed {
            native.stop().map_err(EngineError::Native)?;
        }
        self.calls.shutdown(native, &self.registrations);
        self.registrations.shutdown(native);
        self.transition(EngineState::Stopping)?;
        if let Err(code) = native.stop() {
            self.transition(EngineState::Failed)?;
            return Err(EngineError::Native(code));
        }
        self.transition(EngineState::Stopped)?;
        Ok(self.snapshot())
    }
}
fn owner_loop(mut native: ffi::Native, receiver: mpsc::Receiver<Message>, port: u16) {
    let mut owner = Owner {
        application: ApplicationState::default(),
        sequence: 0,
        last_transport: None,
        registrations: Registrations::new(),
        calls: calls::Calls::new(),
    };
    loop {
        match receiver.recv_timeout(Duration::from_millis(20)) {
            Ok(message) => {
                let shutdown = matches!(&message.operation, Operation::Shutdown);
                let result = match message.operation {
                    Operation::Snapshot => Ok(Reply::Engine(owner.snapshot())),
                    Operation::Start => {
                        if owner.application.engine() == EngineState::Running {
                            Ok(Reply::Engine(owner.snapshot()))
                        } else {
                            owner.last_transport = None;
                            owner.transition(EngineState::Starting).and_then(|()| {
                                match native.start(port) {
                                    Ok(()) => {
                                        owner.registrations = Registrations::new();
                                        owner.transition(EngineState::Running)?;
                                        Ok(Reply::Engine(owner.snapshot()))
                                    }
                                    Err(code) => {
                                        owner.transition(EngineState::Failed)?;
                                        Err(EngineError::Native(code))
                                    }
                                }
                            })
                        }
                    }
                    Operation::Stop | Operation::Shutdown => {
                        owner.stop(&mut native).map(Reply::Engine)
                    }
                    op @ (Operation::Calls
                    | Operation::Dial(..)
                    | Operation::Hangup(..)
                    | Operation::Mute(..)
                    | Operation::Answer(..)
                    | Operation::Reject(..)
                    | Operation::RecordStart(..)
                    | Operation::RecordStop(..)
                    | Operation::Control(..)
                    | Operation::Dtmf(..)) => {
                        if owner.application.engine() != EngineState::Running {
                            Err(EngineError::Unavailable)
                        } else {
                            owner
                                .calls
                                .command(&mut native, &owner.registrations, op)
                                .map(Reply::Calls)
                        }
                    }
                    op => {
                        if owner.application.engine() != EngineState::Running {
                            Err(EngineError::Unavailable)
                        } else {
                            let affected = match &op {
                                Operation::Configure { account, .. } => Some(account.id.as_str()),
                                Operation::Remove(id)
                                | Operation::Unregister(id)
                                | Operation::AccountFailure(id, _) => Some(id.as_str()),
                                _ => None,
                            };
                            if affected.is_some_and(|id| owner.calls.active_account(id)) {
                                Err(EngineError::Busy)
                            } else {
                                owner
                                    .registrations
                                    .command(&mut native, op)
                                    .map(Reply::Registrations)
                            }
                        }
                    }
                };
                let _ = message.reply.send(result);
                if shutdown {
                    break;
                }
            }
            Err(mpsc::RecvTimeoutError::Disconnected) => break,
            Err(mpsc::RecvTimeoutError::Timeout) => {}
        }
        if owner.application.engine() == EngineState::Running {
            owner.calls.poll(&mut native, &owner.registrations);
            owner.registrations.poll(&mut native);
            if native.pump().is_err() {
                owner.calls.shutdown(&mut native, &owner.registrations);
                let _ = owner.transition(EngineState::Failed);
                continue;
            }
            for _ in 0..16 {
                match native.next_event() {
                    Ok(Some(event)) => match map_transport(event) {
                        Ok(event) => {
                            owner.last_transport = Some(event);
                            owner.sequence += 1;
                        }
                        Err(_) => {
                            let _ = owner.transition(EngineState::Failed);
                            break;
                        }
                    },
                    Ok(None) => break,
                    Err(_) => {
                        let _ = owner.transition(EngineState::Failed);
                        break;
                    }
                }
            }
        }
    }
    let _ = native.stop(); // All native destruction, including unwinding, stays on this owner thread.
}

struct ManagedRegistration {
    account: Option<Account>,
    token: u64,
    last_sequence: u64,
    machine: RegistrationMachine,
}
struct Registrations {
    accounts: BTreeMap<String, ManagedRegistration>,
    origin: Instant,
    next_token: u64,
    network_check: u64,
    online: bool,
}
impl Registrations {
    fn new() -> Self {
        Self {
            accounts: BTreeMap::new(),
            origin: Instant::now(),
            next_token: 1,
            network_check: 0,
            online: ffi::Native::network_online().unwrap_or(true),
        }
    }
    fn now(&self) -> u64 {
        self.origin.elapsed().as_millis().min(u128::from(u64::MAX)) as u64
    }
    fn snapshot(&self) -> Vec<RegistrationStatus> {
        let now = self.now();
        self.accounts
            .iter()
            .map(|(id, a)| a.machine.snapshot(id, now))
            .collect()
    }
    fn command(
        &mut self,
        native: &mut ffi::Native,
        operation: Operation,
    ) -> Result<Vec<RegistrationStatus>, EngineError> {
        let now = self.now();
        if let Operation::Register(ref id) = operation {
            return self.intent(native, id, true);
        }
        if let Operation::Unregister(ref id) = operation {
            return self.intent(native, id, false);
        }
        match operation {
            Operation::Configure {
                account,
                credentials,
            } => {
                if self.accounts.len() >= 32 && !self.accounts.contains_key(&account.id) {
                    return Err(EngineError::Native(-9));
                }
                if let Some(old) = self.accounts.remove(&account.id) {
                    native
                        .remove_account(old.token)
                        .map_err(EngineError::Native)?;
                }
                let public_account = account.clone();
                let token = self.next_token;
                self.next_token = self
                    .next_token
                    .checked_add(1)
                    .ok_or(EngineError::Unavailable)?;
                let seed = token
                    ^ std::time::SystemTime::now()
                        .duration_since(std::time::UNIX_EPOCH)
                        .unwrap_or_default()
                        .subsec_nanos() as u64;
                let mut machine = RegistrationMachine::new(seed);
                machine.network(self.online, now);
                let create = native.add_account(token, &account, &credentials);
                if let Err(status) = create {
                    machine.register(now);
                    machine.failed(
                        if status == -9 {
                            RegistrationFailure::Capacity
                        } else {
                            RegistrationFailure::EngineUnavailable
                        },
                        status,
                        0,
                        now,
                    );
                    self.accounts.insert(
                        account.id,
                        ManagedRegistration {
                            account: Some(public_account.clone()),
                            token: 0,
                            last_sequence: 0,
                            machine,
                        },
                    );
                } else {
                    if account.enabled
                        && machine.register(now)
                        && let Err(status) = native.register(token, true)
                    {
                        machine.failed(
                            RegistrationFailure::from_native(
                                ffi::Native::failure_kind(status, 0),
                                status,
                                0,
                            ),
                            status,
                            0,
                            now,
                        );
                    }
                    self.accounts.insert(
                        account.id,
                        ManagedRegistration {
                            account: Some(public_account),
                            token,
                            last_sequence: 0,
                            machine,
                        },
                    );
                }
            }
            Operation::Registrations => {}
            Operation::Register(id) | Operation::Unregister(id) => {
                // Dispatch is handled below by preserving the specific operation before matching.
                let _ = id;
                return Err(EngineError::InvalidTransition);
            }
            Operation::Remove(id) => {
                if let Some(a) = self.accounts.remove(&id)
                    && a.token != 0
                {
                    native
                        .remove_account(a.token)
                        .map_err(EngineError::Native)?;
                }
            }
            Operation::AccountFailure(id, error) => {
                if let Some(previous) = self.accounts.remove(&id)
                    && previous.token != 0
                {
                    native
                        .remove_account(previous.token)
                        .map_err(EngineError::Native)?;
                }
                let mut machine = RegistrationMachine::new(self.next_token);
                machine.register(now);
                machine.failed(error, 0, 0, now);
                self.accounts.insert(
                    id,
                    ManagedRegistration {
                        account: None,
                        token: 0,
                        last_sequence: 0,
                        machine,
                    },
                );
            }
            _ => return Err(EngineError::InvalidTransition),
        }
        Ok(self.snapshot())
    }
    fn intent(
        &mut self,
        native: &mut ffi::Native,
        id: &str,
        renew: bool,
    ) -> Result<Vec<RegistrationStatus>, EngineError> {
        let now = self.now();
        let a = self.accounts.get_mut(id).ok_or(EngineError::Unavailable)?;
        if a.token == 0 {
            return Err(EngineError::Unavailable);
        }
        let send = if renew {
            a.machine.register(now)
        } else {
            a.machine.unregister(now)
        };
        if send && let Err(status) = native.register(a.token, renew) {
            a.machine.failed(
                RegistrationFailure::from_native(ffi::Native::failure_kind(status, 0), status, 0),
                status,
                0,
                now,
            );
        }
        Ok(self.snapshot())
    }
    fn poll(&mut self, native: &mut ffi::Native) {
        let now = self.now();
        if now >= self.network_check {
            self.network_check = now.saturating_add(1000);
            if let Some(online) = ffi::Native::network_online() {
                self.online = online;
                for a in self.accounts.values_mut() {
                    if a.machine.network(online, now) && a.token != 0 {
                        let _ = native.register(a.token, false);
                    }
                }
            }
        }
        for a in self.accounts.values_mut() {
            if a.token == 0 {
                continue;
            }
            if let Ok(event) = native.registration(a.token)
                && event.token == a.token
                && event.sequence > a.last_sequence
            {
                a.last_sequence = event.sequence;
                a.machine.event(
                    RegistrationObservation {
                        phase: event.phase,
                        renew: event.renew != 0,
                        status: event.status,
                        code: event.sip_code,
                        expires: event.expires,
                        kind: event.failure_kind,
                    },
                    now,
                );
                if event.phase == 2
                    && event.status == 0
                    && (200..300).contains(&event.sip_code)
                    && event.expires > 0
                    && (!a.machine.wants_registration() || !self.online)
                {
                    if !a.machine.wants_registration() {
                        a.machine.unregister(now);
                    }
                    let _ = native.register(a.token, false);
                }
            }
            if a.machine.tick(now)
                && let Err(status) = native.register(a.token, true)
            {
                a.machine.failed(
                    RegistrationFailure::from_native(
                        ffi::Native::failure_kind(status, 0),
                        status,
                        0,
                    ),
                    status,
                    0,
                    now,
                );
            }
        }
    }
    fn shutdown(&mut self, native: &mut ffi::Native) {
        for a in self.accounts.values_mut() {
            if a.token != 0 {
                let _ = native.register(a.token, false);
            }
        }
        // Bounded graceful unregistration window. No timeout is claimed as an ACK.
        let until = Instant::now() + Duration::from_millis(1000);
        while Instant::now() < until
            && self.accounts.values().any(|a| {
                a.token != 0
                    && native
                        .registration(a.token)
                        .is_ok_and(|e| e.phase != 2 || e.expires != 0)
            })
        {
            let _ = native.pump();
            thread::sleep(Duration::from_millis(10));
        }
        self.accounts.clear();
    }
}

fn map_transport(event: ffi::NativeEvent) -> Result<TransportEvent, EngineError> {
    let state = match event.state {
        0 => TransportState::Connected,
        1 => TransportState::Disconnected,
        2 => TransportState::ShuttingDown,
        3 => TransportState::Destroyed,
        _ => return Err(EngineError::Native(-8)),
    };
    Ok(TransportEvent {
        state,
        error_code: event.error_code,
        dropped: event.dropped,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    static SERIAL: Mutex<()> = Mutex::new(());
    #[test]
    fn callback_mapping_copies_owned_data_and_rejects_unknown_state() {
        let event = map_transport(ffi::NativeEvent {
            state: 1,
            error_code: 70001,
            dropped: 4,
        })
        .unwrap();
        assert_eq!(event.state, TransportState::Disconnected);
        assert_eq!(event.error_code, 70001);
        assert_eq!(event.dropped, 4);
        assert_eq!(
            map_transport(ffi::NativeEvent {
                state: 99,
                ..Default::default()
            }),
            Err(EngineError::Native(-8))
        );
    }
    #[test]
    fn repeated_lifecycles_and_concurrent_reads() {
        let _serial = SERIAL.lock().unwrap();
        let engine = PjsipEngine::new().unwrap();
        assert_eq!(engine.snapshot().unwrap().sequence, 0);
        for cycle in 0..20 {
            let started = engine.start_engine().unwrap();
            assert_eq!(started.state, EngineState::Running);
            assert_eq!(started.sequence, cycle * 4 + 2);
            assert_eq!(engine.start_engine().unwrap(), started);
            let clones: Vec<_> = (0..4)
                .map(|_| {
                    let engine = engine.clone();
                    thread::spawn(move || engine.snapshot().unwrap())
                })
                .collect();
            for worker in clones {
                assert_eq!(worker.join().unwrap(), started);
            }
            let stopped = engine.stop_engine().unwrap();
            assert_eq!(stopped.state, EngineState::Stopped);
            assert_eq!(stopped.sequence, cycle * 4 + 4);
            assert_eq!(engine.stop_engine().unwrap(), stopped);
        }
        engine.shutdown().unwrap();
        assert_eq!(engine.snapshot(), Err(EngineError::Unavailable));
    }
    #[test]
    fn failed_transport_start_cleans_up_and_can_restart() {
        let _serial = SERIAL.lock().unwrap();
        let socket = std::net::UdpSocket::bind("127.0.0.1:0").unwrap();
        let engine = PjsipEngine::spawn(socket.local_addr().unwrap().port()).unwrap();
        assert!(matches!(engine.start_engine(), Err(EngineError::Native(_))));
        assert_eq!(engine.snapshot().unwrap().state, EngineState::Failed);
        drop(socket);
        assert_eq!(engine.start_engine().unwrap().state, EngineState::Running);
        engine.shutdown().unwrap();
        // A new PJSUA2 singleton can be allocated after disposal.
        let replacement = PjsipEngine::new().unwrap();
        replacement.start_engine().unwrap();
        replacement.shutdown().unwrap();
    }
    #[test]
    fn stop_releases_transport_and_singleton_is_guarded() {
        let _serial = SERIAL.lock().unwrap();
        let reservation = std::net::UdpSocket::bind("127.0.0.1:0").unwrap();
        let port = reservation.local_addr().unwrap().port();
        drop(reservation);
        let engine = PjsipEngine::spawn(port).unwrap();
        engine.start_engine().unwrap();
        assert!(matches!(PjsipEngine::new(), Err(EngineError::Native(-5))));
        assert!(std::net::UdpSocket::bind(("127.0.0.1", port)).is_err());
        engine.stop_engine().unwrap();
        let released = std::net::UdpSocket::bind(("127.0.0.1", port)).unwrap();
        drop(released);
        engine.shutdown().unwrap();
    }
    #[test]
    fn drop_stops_and_disposes_a_running_engine() {
        let _serial = SERIAL.lock().unwrap();
        {
            let engine = PjsipEngine::new().unwrap();
            engine.start_engine().unwrap();
        }
        let mut replacement = PjsipEngine::new().unwrap();
        SipEngine::start(&mut replacement).unwrap();
        SipEngine::stop(&mut replacement).unwrap();
    }
    fn test_account(id: &str, port: u16) -> Account {
        Account {
            id: id.into(),
            label: "Test".into(),
            username: "1005".into(),
            host: "127.0.0.1".into(),
            port,
            transport: "udp".into(),
            enabled: true,
            stun_server: None,
            ice: false,
            turn_server: None,
            turn_username: None,
        }
    }
    fn wait_state(engine: &PjsipEngine, id: &str, state: &str) -> RegistrationStatus {
        let end = Instant::now() + Duration::from_secs(5);
        loop {
            let status = engine
                .registrations()
                .unwrap()
                .into_iter()
                .find(|s| s.account_id == id)
                .unwrap();
            if status.state == state {
                return status;
            }
            assert!(
                Instant::now() < end,
                "state did not arrive: {}",
                status.state
            );
            thread::sleep(Duration::from_millis(20));
        }
    }
    fn registrar(
        auth_failure: bool,
    ) -> (
        u16,
        Arc<std::sync::atomic::AtomicBool>,
        Arc<std::sync::atomic::AtomicUsize>,
        thread::JoinHandle<()>,
    ) {
        use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
        let socket = std::net::UdpSocket::bind("127.0.0.1:0").unwrap();
        socket
            .set_read_timeout(Some(Duration::from_millis(100)))
            .unwrap();
        let port = socket.local_addr().unwrap().port();
        let stop = Arc::new(AtomicBool::new(false));
        let done = stop.clone();
        let counter = Arc::new(AtomicUsize::new(0));
        let requests = counter.clone();
        let worker = thread::spawn(move || {
            let mut buffer = [0u8; 16000];
            while !done.load(Ordering::Relaxed) {
                let Ok((length, peer)) = socket.recv_from(&mut buffer) else {
                    continue;
                };
                let request = String::from_utf8_lossy(&buffer[..length]);
                if !request.starts_with("REGISTER ") {
                    continue;
                }
                requests.fetch_add(1, Ordering::Relaxed);
                let header = |name: &str| {
                    request
                        .lines()
                        .find_map(|l| {
                            l.split_once(':')
                                .filter(|(key, _)| key.eq_ignore_ascii_case(name))
                                .map(|(_, value)| value.trim().to_owned())
                        })
                        .unwrap_or_default()
                };
                let expires = header("Expires");
                let status = if auth_failure {
                    if header("Authorization").is_empty() {
                        "401 Unauthorized"
                    } else {
                        "403 Forbidden"
                    }
                } else {
                    "200 OK"
                };
                let challenge = if status.starts_with("401") {
                    "WWW-Authenticate: Digest realm=\"test\",nonce=\"test-nonce\",algorithm=MD5,qop=\"auth\"\r\n"
                } else {
                    ""
                };
                let response = format!(
                    "SIP/2.0 {status}\r\nVia: {}\r\nFrom: {}\r\nTo: {};tag=fixture\r\nCall-ID: {}\r\nCSeq: {}\r\n{challenge}Contact: {}\r\nExpires: {}\r\nContent-Length: 0\r\n\r\n",
                    header("Via"),
                    header("From"),
                    header("To"),
                    header("Call-ID"),
                    header("CSeq"),
                    header("Contact"),
                    if expires == "0" { "0" } else { "60" }
                );
                socket.send_to(response.as_bytes(), peer).unwrap();
            }
        });
        (port, stop, counter, worker)
    }
    #[test]
    fn real_udp_registrar_register_refresh_unregister_and_cancel() {
        use std::sync::atomic::Ordering;
        let _serial = SERIAL.lock().unwrap();
        let (port, stop, counter, worker) = registrar(false);
        let engine = PjsipEngine::new().unwrap();
        engine.start_engine().unwrap();
        engine
            .configure_account(
                test_account("one", port),
                SipCredentials::new(b"test-only-password".to_vec()).unwrap(),
            )
            .unwrap();
        assert_eq!(wait_state(&engine, "one", "registered").sip_code, Some(200));
        let before = counter.load(Ordering::Relaxed);
        engine.register_account("one").unwrap();
        wait_state(&engine, "one", "registered");
        assert!(counter.load(Ordering::Relaxed) > before);
        engine.unregister_account("one").unwrap();
        assert_eq!(
            wait_state(&engine, "one", "unregistered").sip_code,
            Some(200)
        );
        engine.register_account("one").unwrap();
        engine.unregister_account("one").unwrap();
        wait_state(&engine, "one", "unregistered");
        thread::sleep(Duration::from_millis(150));
        assert_eq!(engine.registrations().unwrap()[0].state, "unregistered");
        engine.remove_account("one").unwrap();
        assert!(engine.registrations().unwrap().is_empty());
        engine.shutdown().unwrap();
        stop.store(true, Ordering::Relaxed);
        worker.join().unwrap();
    }
    #[test]
    fn real_auth_rejection_is_typed_and_does_not_schedule_a_retry() {
        use std::sync::atomic::Ordering;
        let _serial = SERIAL.lock().unwrap();
        let (port, stop, counter, worker) = registrar(true);
        let engine = PjsipEngine::new().unwrap();
        engine.start_engine().unwrap();
        engine
            .configure_account(
                test_account("one", port),
                SipCredentials::new(b"deliberately-wrong-test-password".to_vec()).unwrap(),
            )
            .unwrap();
        let result = wait_state(&engine, "one", "failed");
        assert_eq!(result.failure, Some(RegistrationFailure::SipAuthentication));
        assert_eq!(result.sip_code, Some(403));
        assert_eq!(result.retry_in_seconds, None);
        assert!(counter.load(Ordering::Relaxed) >= 2);
        engine.shutdown().unwrap();
        stop.store(true, Ordering::Relaxed);
        worker.join().unwrap();
    }
    #[test]
    fn native_account_capacity_is_explicit_and_failure_disposes_cleanly() {
        let _serial = SERIAL.lock().unwrap();
        let engine = PjsipEngine::new().unwrap();
        engine.start_engine().unwrap();
        for i in 0..8 {
            let mut account = test_account(&format!("test-{i}"), 9);
            account.enabled = false;
            engine
                .configure_account(account, SipCredentials::new(b"test-only".to_vec()).unwrap())
                .unwrap();
        }
        let result = engine.registrations().unwrap();
        assert_eq!(
            result
                .iter()
                .filter(|s| s.failure == Some(RegistrationFailure::Capacity))
                .count(),
            1
        );
        engine.remove_account("test-0").unwrap();
        let mut account = test_account("test-7", 9);
        account.enabled = false;
        engine
            .configure_account(account, SipCredentials::new(b"test-only".to_vec()).unwrap())
            .unwrap();
        assert!(
            engine
                .registrations()
                .unwrap()
                .iter()
                .all(|s| s.failure.is_none())
        );
        engine.shutdown().unwrap();
    }

    fn call_fixture() -> (
        u16,
        Arc<std::sync::atomic::AtomicBool>,
        Arc<std::sync::atomic::AtomicUsize>,
        thread::JoinHandle<()>,
    ) {
        use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
        let socket = std::net::UdpSocket::bind("127.0.0.1:0").unwrap();
        socket
            .set_read_timeout(Some(Duration::from_millis(20)))
            .unwrap();
        let port = socket.local_addr().unwrap().port();
        let stop = Arc::new(AtomicBool::new(false));
        let done = stop.clone();
        let calls = Arc::new(AtomicUsize::new(0));
        let count = calls.clone();
        let worker = thread::spawn(move || {
            let header = |request: &str, name: &str| {
                request
                    .lines()
                    .find_map(|l| {
                        l.split_once(':')
                            .filter(|(key, _)| key.eq_ignore_ascii_case(name))
                            .map(|(_, v)| v.trim().to_owned())
                    })
                    .unwrap_or_default()
            };
            let respond = |request: &str, peer, status: &str, body: &str| {
                let to = header(request, "To");
                let to = if to.contains(";tag=") {
                    to
                } else {
                    format!("{to};tag=call-fixture")
                };
                let contact = format!("<sip:fixture@127.0.0.1:{port}>");
                let response = format!(
                    "SIP/2.0 {status}\r\nVia: {}\r\nFrom: {}\r\nTo: {to}\r\nCall-ID: {}\r\nCSeq: {}\r\nContact: {}\r\nExpires: {}\r\n{}Content-Length: {}\r\n\r\n{body}",
                    header(request, "Via"),
                    header(request, "From"),
                    header(request, "Call-ID"),
                    header(request, "CSeq"),
                    if request.starts_with("REGISTER ") {
                        header(request, "Contact")
                    } else {
                        contact
                    },
                    if header(request, "Expires") == "0" {
                        "0"
                    } else {
                        "60"
                    },
                    if body.is_empty() {
                        ""
                    } else {
                        "Content-Type: application/sdp\r\n"
                    },
                    body.len()
                );
                socket.send_to(response.as_bytes(), peer).unwrap();
            };
            let sdp = "v=0\r\no=fixture 1 1 IN IP4 127.0.0.1\r\ns=fixture\r\nc=IN IP4 127.0.0.1\r\nt=0 0\r\nm=audio 19000 RTP/AVP 0 8\r\na=rtpmap:0 PCMU/8000\r\na=rtpmap:8 PCMA/8000\r\na=sendrecv\r\n";
            let mut pending: Option<(String, std::net::SocketAddr, Option<Instant>)> = None;
            let mut seen = std::collections::HashSet::new();
            let mut buffer = [0u8; 16000];
            while !done.load(Ordering::Relaxed) {
                if pending
                    .as_ref()
                    .is_some_and(|(_, _, deadline)| deadline.is_some_and(|d| Instant::now() >= d))
                {
                    let (request, peer, _) = pending.take().unwrap();
                    respond(&request, peer, "200 OK", sdp);
                }
                let Ok((length, peer)) = socket.recv_from(&mut buffer) else {
                    continue;
                };
                let request = String::from_utf8_lossy(&buffer[..length]).into_owned();
                if request.starts_with("REGISTER ") {
                    respond(&request, peer, "200 OK", "");
                } else if request.starts_with("INVITE ") {
                    if seen.insert(header(&request, "Call-ID")) {
                        count.fetch_add(1, Ordering::Relaxed);
                    }
                    if header(&request, "To").contains(";tag=") {
                        let answer =
                            if request.contains("a=sendonly") || request.contains("a=inactive") {
                                sdp.replace("a=sendrecv", "a=recvonly")
                            } else {
                                sdp.to_string()
                            };
                        respond(&request, peer, "200 OK", &answer);
                    } else if request.starts_with("INVITE sip:1001@") {
                        respond(&request, peer, "486 Busy Here", "");
                    } else if request.starts_with("INVITE sip:1002@") {
                        respond(&request, peer, "183 Session Progress", sdp);
                        pending = Some((
                            request,
                            peer,
                            Some(Instant::now() + Duration::from_millis(600)),
                        ));
                    } else {
                        respond(&request, peer, "180 Ringing", "");
                        pending = Some((request, peer, None));
                    }
                } else if request.starts_with("CANCEL ") {
                    respond(&request, peer, "200 OK", "");
                    if let Some((original, remote, _)) = pending.take() {
                        respond(&original, remote, "487 Request Terminated", "");
                    }
                } else if request.starts_with("REFER ") {
                    respond(&request, peer, "202 Accepted", "");
                    let result = if header(&request, "Refer-To").contains("sip:1001@") {
                        "SIP/2.0 486 Busy Here\r\n"
                    } else {
                        "SIP/2.0 200 OK\r\n"
                    };
                    let to = header(&request, "To");
                    let notify = format!(
                        "NOTIFY {} SIP/2.0\r\nVia: SIP/2.0/UDP 127.0.0.1:{port};branch=z9hG4bKnotify{}\r\nFrom: {to}\r\nTo: {}\r\nCall-ID: {}\r\nCSeq: {} NOTIFY\r\nMax-Forwards: 70\r\nEvent: refer\r\nSubscription-State: terminated;reason=noresource\r\nContent-Type: message/sipfrag\r\nContent-Length: {}\r\n\r\n{result}",
                        header(&request, "Contact").trim_matches(['<', '>']),
                        header(&request, "CSeq").replace(' ', ""),
                        header(&request, "From"),
                        header(&request, "Call-ID"),
                        header(&request, "CSeq")
                            .split_whitespace()
                            .next()
                            .unwrap_or("1"),
                        result.len()
                    );
                    socket.send_to(notify.as_bytes(), peer).unwrap();
                } else if request.starts_with("BYE ") {
                    respond(&request, peer, "200 OK", "");
                }
            }
        });
        (port, stop, calls, worker)
    }
    fn wait_call(
        calls: &mut calls::Calls,
        native: &mut ffi::Native,
        registrations: &Registrations,
        id: &str,
        state: &str,
    ) -> yeyofone_application::calls::CallStatus {
        let until = Instant::now() + Duration::from_secs(5);
        loop {
            native.pump().unwrap();
            calls.poll(native, registrations);
            let row = calls.snapshot().into_iter().find(|c| c.id == id).unwrap();
            if row.state == state {
                return row;
            }
            assert!(
                Instant::now() < until,
                "call {id}: wanted {state}, got {} {:?}",
                row.state,
                row.reason
            );
            thread::sleep(Duration::from_millis(10));
        }
    }
    #[test]
    fn real_outgoing_busy_cancel_early_media_connect_mute_and_idempotence() {
        use std::sync::atomic::Ordering;
        let _serial = SERIAL.lock().unwrap();
        let (port, stop, count, worker) = call_fixture();
        let mut native = ffi::Native::create().unwrap();
        native.start(0).unwrap();
        let mut registrations = Registrations::new();
        registrations
            .command(
                &mut native,
                Operation::Configure {
                    account: test_account("one", port),
                    credentials: SipCredentials::new(b"synthetic-call-password".to_vec()).unwrap(),
                },
            )
            .unwrap();
        let until = Instant::now() + Duration::from_secs(5);
        while registrations.snapshot()[0].state != "registered" {
            native.pump().unwrap();
            registrations.poll(&mut native);
            assert!(Instant::now() < until);
            thread::sleep(Duration::from_millis(10));
        }
        let mut calls = calls::Calls::new();
        calls.audio = false;
        assert!(
            calls
                .command(
                    &mut native,
                    &registrations,
                    Operation::Dial("bad".into(), "one".into(), "sip:1000@evil.example".into())
                )
                .is_err()
        );
        calls
            .command(
                &mut native,
                &registrations,
                Operation::Dial("busy".into(), "one".into(), "1001".into()),
            )
            .unwrap();
        let row = wait_call(&mut calls, &mut native, &registrations, "busy", "ended");
        assert_eq!(row.reason.as_deref(), Some("busy"));
        assert_eq!(row.sip_code, Some(486));
        calls
            .command(
                &mut native,
                &registrations,
                Operation::Dial("busy".into(), "one".into(), "1001".into()),
            )
            .unwrap();
        assert_eq!(count.load(Ordering::Relaxed), 1);
        calls
            .command(
                &mut native,
                &registrations,
                Operation::Dial("cancel".into(), "one".into(), "1003".into()),
            )
            .unwrap();
        wait_call(&mut calls, &mut native, &registrations, "cancel", "ringing");
        assert!(matches!(
            calls.command(
                &mut native,
                &registrations,
                Operation::Dial("second".into(), "one".into(), "1001".into())
            ),
            Err(EngineError::Busy)
        ));
        calls
            .command(
                &mut native,
                &registrations,
                Operation::Hangup("cancel".into()),
            )
            .unwrap();
        calls
            .command(
                &mut native,
                &registrations,
                Operation::Hangup("cancel".into()),
            )
            .unwrap();
        assert_eq!(
            wait_call(&mut calls, &mut native, &registrations, "cancel", "ended")
                .reason
                .as_deref(),
            Some("cancelled")
        );
        calls
            .command(
                &mut native,
                &registrations,
                Operation::Dial("connected".into(), "one".into(), "1002".into()),
            )
            .unwrap();
        let early = wait_call(
            &mut calls,
            &mut native,
            &registrations,
            "connected",
            "early_media",
        );
        assert_eq!(early.duration_seconds, 0);
        wait_call(
            &mut calls,
            &mut native,
            &registrations,
            "connected",
            "connected",
        );
        calls
            .command(
                &mut native,
                &registrations,
                Operation::Mute("connected".into(), true),
            )
            .unwrap();
        calls
            .command(
                &mut native,
                &registrations,
                Operation::Mute("connected".into(), true),
            )
            .unwrap();
        assert!(
            calls
                .snapshot()
                .iter()
                .find(|c| c.id == "connected")
                .unwrap()
                .muted
        );
        thread::sleep(Duration::from_millis(1100));
        calls
            .command(
                &mut native,
                &registrations,
                Operation::Hangup("connected".into()),
            )
            .unwrap();
        let ended = wait_call(
            &mut calls,
            &mut native,
            &registrations,
            "connected",
            "ended",
        );
        assert_eq!(ended.reason.as_deref(), Some("local_hangup"));
        assert!(ended.duration_seconds >= 1);
        assert_eq!(count.load(Ordering::Relaxed), 3);
        native.stop().unwrap();
        stop.store(true, Ordering::Relaxed);
        worker.join().unwrap();
    }
    #[test]
    fn local_hold_resume_consult_cancel_retry_and_transfer_results() {
        use std::sync::atomic::Ordering;
        let _serial = SERIAL.lock().unwrap();
        let (port, stop, _, worker) = call_fixture();
        let mut native = ffi::Native::create().unwrap();
        native.start(0).unwrap();
        let mut registrations = Registrations::new();
        registrations
            .command(
                &mut native,
                Operation::Configure {
                    account: test_account("one", port),
                    credentials: SipCredentials::new(b"synthetic-call-password".to_vec()).unwrap(),
                },
            )
            .unwrap();
        let until = Instant::now() + Duration::from_secs(5);
        while registrations.snapshot()[0].state != "registered" {
            native.pump().unwrap();
            registrations.poll(&mut native);
            assert!(Instant::now() < until);
            thread::sleep(Duration::from_millis(10));
        }
        let mut calls = calls::Calls::new();
        calls.audio = false;
        calls
            .command(
                &mut native,
                &registrations,
                Operation::Dial("original".into(), "one".into(), "1002".into()),
            )
            .unwrap();
        wait_call(
            &mut calls,
            &mut native,
            &registrations,
            "original",
            "connected",
        );
        let control = |calls: &mut calls::Calls,
                       native: &mut ffi::Native,
                       action: &str,
                       dest: &str,
                       child: &str| {
            calls.command(
                native,
                &registrations,
                Operation::Control("original".into(), action.into(), dest.into(), child.into()),
            )
        };
        let settle = |calls: &mut calls::Calls, native: &mut ffi::Native| {
            for _ in 0..30 {
                native.pump().unwrap();
                calls.poll(native, &registrations);
                thread::sleep(Duration::from_millis(10));
            }
        };
        control(&mut calls, &mut native, "hold", "", "").unwrap();
        settle(&mut calls, &mut native);
        assert!(
            calls
                .snapshot()
                .iter()
                .find(|s| s.id == "original")
                .unwrap()
                .held
        );
        assert!(
            calls
                .command(
                    &mut native,
                    &registrations,
                    Operation::Dtmf("original".into(), '1')
                )
                .is_err()
        );
        control(&mut calls, &mut native, "resume", "", "").unwrap();
        settle(&mut calls, &mut native);
        assert!(
            !calls
                .snapshot()
                .iter()
                .find(|s| s.id == "original")
                .unwrap()
                .held
        );
        control(&mut calls, &mut native, "transfer", "1001", "").unwrap();
        settle(&mut calls, &mut native);
        let original = calls
            .snapshot()
            .into_iter()
            .find(|s| s.id == "original")
            .unwrap();
        assert_eq!(original.state, "connected");
        assert_eq!(original.transfer_code, 486);
        assert!(!original.transfer_pending);
        control(&mut calls, &mut native, "consult_start", "1001", "failed").unwrap();
        wait_call(&mut calls, &mut native, &registrations, "failed", "ended");
        settle(&mut calls, &mut native);
        assert!(
            !calls
                .snapshot()
                .iter()
                .find(|s| s.id == "original")
                .unwrap()
                .held
        );
        control(
            &mut calls,
            &mut native,
            "consult_start",
            "1002",
            "cancelled",
        )
        .unwrap();
        wait_call(
            &mut calls,
            &mut native,
            &registrations,
            "cancelled",
            "connected",
        );
        assert!(
            calls
                .snapshot()
                .iter()
                .find(|s| s.id == "original")
                .unwrap()
                .held
        );
        assert!(control(&mut calls, &mut native, "consult_complete", "", "failed").is_err());
        control(&mut calls, &mut native, "consult_cancel", "", "cancelled").unwrap();
        wait_call(
            &mut calls,
            &mut native,
            &registrations,
            "cancelled",
            "ended",
        );
        settle(&mut calls, &mut native);
        assert!(
            !calls
                .snapshot()
                .iter()
                .find(|s| s.id == "original")
                .unwrap()
                .held
        );
        control(
            &mut calls,
            &mut native,
            "consult_start",
            "1002",
            "completed",
        )
        .unwrap();
        wait_call(
            &mut calls,
            &mut native,
            &registrations,
            "completed",
            "connected",
        );
        assert!(
            calls
                .snapshot()
                .iter()
                .find(|s| s.id == "original")
                .unwrap()
                .held
        );
        control(&mut calls, &mut native, "consult_complete", "", "completed").unwrap();
        wait_call(&mut calls, &mut native, &registrations, "original", "ended");
        wait_call(
            &mut calls,
            &mut native,
            &registrations,
            "completed",
            "ended",
        );
        native.stop().unwrap();
        stop.store(true, Ordering::Relaxed);
        worker.join().unwrap();
    }
    #[test]
    fn local_consult_merge_then_one_side_hangs_up() {
        use std::sync::atomic::Ordering;
        let _serial = SERIAL.lock().unwrap();
        let (port, stop, _, worker) = call_fixture();
        let mut native = ffi::Native::create().unwrap();
        native.start(0).unwrap();
        let mut registrations = Registrations::new();
        registrations
            .command(
                &mut native,
                Operation::Configure {
                    account: test_account("one", port),
                    credentials: SipCredentials::new(b"synthetic-call-password".to_vec()).unwrap(),
                },
            )
            .unwrap();
        let until = Instant::now() + Duration::from_secs(5);
        while registrations.snapshot()[0].state != "registered" {
            native.pump().unwrap();
            registrations.poll(&mut native);
            assert!(Instant::now() < until);
            thread::sleep(Duration::from_millis(10));
        }
        let mut calls = calls::Calls::new();
        calls.audio = false;
        calls
            .command(
                &mut native,
                &registrations,
                Operation::Dial("original".into(), "one".into(), "1002".into()),
            )
            .unwrap();
        wait_call(
            &mut calls,
            &mut native,
            &registrations,
            "original",
            "connected",
        );
        let control = |calls: &mut calls::Calls,
                       native: &mut ffi::Native,
                       action: &str,
                       dest: &str,
                       child: &str| {
            calls.command(
                native,
                &registrations,
                Operation::Control("original".into(), action.into(), dest.into(), child.into()),
            )
        };
        let settle = |calls: &mut calls::Calls, native: &mut ffi::Native| {
            for _ in 0..30 {
                native.pump().unwrap();
                calls.poll(native, &registrations);
                thread::sleep(Duration::from_millis(10));
            }
        };
        let find = |calls: &mut calls::Calls, id: &str| {
            calls.snapshot().into_iter().find(|s| s.id == id).unwrap()
        };
        // Merging needs a connected consultation.
        assert!(control(&mut calls, &mut native, "consult_merge", "", "added").is_err());
        control(&mut calls, &mut native, "consult_start", "1002", "added").unwrap();
        wait_call(
            &mut calls,
            &mut native,
            &registrations,
            "added",
            "connected",
        );
        assert!(find(&mut calls, "original").held);

        control(&mut calls, &mut native, "consult_merge", "", "added").unwrap();
        settle(&mut calls, &mut native);
        let original = find(&mut calls, "original");
        let added = find(&mut calls, "added");
        assert!(!original.held);
        assert_eq!(original.state, "connected");
        assert_eq!(added.state, "connected");
        assert_eq!(original.merged_with.as_deref(), Some("added"));
        assert_eq!(added.merged_with.as_deref(), Some("original"));
        assert_eq!(added.consult_parent_id, None);
        // A merged call can't be put on hold or start another consultation.
        assert!(control(&mut calls, &mut native, "hold", "", "").is_err());
        assert!(control(&mut calls, &mut native, "consult_start", "1002", "third").is_err());

        // One side leaves; the other carries on as an ordinary call.
        calls
            .command(
                &mut native,
                &registrations,
                Operation::Hangup("added".into()),
            )
            .unwrap();
        wait_call(&mut calls, &mut native, &registrations, "added", "ended");
        settle(&mut calls, &mut native);
        let original = find(&mut calls, "original");
        assert_eq!(original.state, "connected");
        assert_eq!(original.merged_with, None);
        control(&mut calls, &mut native, "hold", "", "").unwrap();
        native.stop().unwrap();
        stop.store(true, Ordering::Relaxed);
        worker.join().unwrap();
    }
}
