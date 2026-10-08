//! All unsafe code is confined here. Handles never leave their creating thread.
use std::{marker::PhantomData, ptr::NonNull, rc::Rc};
#[repr(C)]
struct Opaque {
    _private: [u8; 0],
}
#[repr(C)]
#[derive(Clone, Copy, Default, Debug)]
pub(crate) struct NativeEvent {
    pub state: i32,
    pub error_code: i32,
    pub dropped: u64,
}
#[repr(C)]
struct NativeAccountConfig {
    token: u64,
    username: *const u8,
    username_len: u32,
    host: *const u8,
    host_len: u32,
    password: *const u8,
    password_len: u32,
    port: u32,
    transport: i32,
}
#[repr(C)]
#[derive(Clone, Copy, Default)]
pub(crate) struct NativeRegistration {
    pub token: u64,
    pub sequence: u64,
    pub phase: i32,
    pub renew: i32,
    pub status: i32,
    pub sip_code: i32,
    pub expires: u32,
    pub failure_kind: i32,
}
#[repr(C)]
#[derive(Clone, Copy)]
pub(crate) struct NativeCall {
    pub token: u64,
    pub account_token: u64,
    pub sequence: u64,
    pub state: i32,
    pub sip_code: i32,
    pub audio_active: i32,
    pub audio_error: i32,
    pub muted: i32,
    pub media_active: i32,
    pub recording: i32,
    pub native_code: i32,
    pub failure_kind: i32,
    pub incoming: i32,
    pub caller_len: i32,
    pub caller: [u8; 65],
    pub connected_ms: u64,
}
#[repr(C)]
#[derive(Default)]
pub(crate) struct NativeCallControls {
    pub held: i32,
    pub transfer_pending: i32,
    pub transfer_code: i32,
}
impl Default for NativeCall {
    fn default() -> Self {
        Self {
            token: 0,
            account_token: 0,
            sequence: 0,
            state: 0,
            sip_code: 0,
            audio_active: 0,
            audio_error: 0,
            muted: 0,
            media_active: 0,
            recording: 0,
            native_code: 0,
            failure_kind: 0,
            incoming: 0,
            caller_len: 0,
            caller: [0; 65],
            connected_ms: 0,
        }
    }
}
unsafe extern "C" {
    fn yv_call_list(
        handle: *mut Opaque,
        output: *mut NativeCall,
        capacity: u32,
        count: *mut u32,
    ) -> i32;
    fn yv_call_answer(handle: *mut Opaque, token: u64) -> i32;
    fn yv_call_reject(handle: *mut Opaque, token: u64, status: i32) -> i32;
    fn yv_call_start(
        handle: *mut Opaque,
        account: u64,
        token: u64,
        uri: *const u8,
        len: u32,
        audio: i32,
    ) -> i32;
    fn yv_call_snapshot(handle: *mut Opaque, token: u64, out: *mut NativeCall) -> i32;
    fn yv_call_hangup(handle: *mut Opaque, token: u64) -> i32;
    fn yv_call_controls(handle: *mut Opaque, token: u64, out: *mut NativeCallControls) -> i32;
    fn yv_call_hold(handle: *mut Opaque, token: u64, held: i32) -> i32;
    fn yv_call_transfer(
        handle: *mut Opaque,
        token: u64,
        uri: *const u8,
        len: u32,
        consultation: u64,
    ) -> i32;
    fn yv_call_mute(handle: *mut Opaque, token: u64, muted: i32) -> i32;
    fn yv_call_record_start(handle: *mut Opaque, token: u64, path: *const u8, len: u32) -> i32;
    fn yv_call_record_stop(handle: *mut Opaque, token: u64) -> i32;
    fn yv_call_dtmf(handle: *mut Opaque, token: u64, digits: *const u8, len: u32) -> i32;
    fn yv_call_release(handle: *mut Opaque, token: u64) -> i32;

    fn yv_account_add(handle: *mut Opaque, config: *const NativeAccountConfig) -> i32;
    fn yv_account_remove(handle: *mut Opaque, token: u64) -> i32;
    fn yv_account_register(handle: *mut Opaque, token: u64, renew: i32) -> i32;
    fn yv_registration(handle: *mut Opaque, token: u64, output: *mut NativeRegistration) -> i32;
    fn yv_failure_kind(status: i32, code: i32) -> i32;
    fn yv_network_online() -> i32;
    fn yv_create(output: *mut *mut Opaque) -> i32;
    fn yv_start(handle: *mut Opaque, port: u32) -> i32;
    fn yv_stop(handle: *mut Opaque) -> i32;
    fn yv_pump(handle: *mut Opaque) -> i32;
    fn yv_next_event(handle: *mut Opaque, output: *mut NativeEvent) -> i32;
    fn yv_destroy(handle: *mut Opaque) -> i32;
}
pub(crate) struct Native {
    handle: NonNull<Opaque>,
    _owner_only: PhantomData<Rc<()>>,
}
fn checked(code: i32) -> Result<(), i32> {
    if code == 0 { Ok(()) } else { Err(code) }
}
impl Native {
    pub fn calls(&mut self) -> Result<Vec<NativeCall>, i32> {
        let mut items = [NativeCall::default(); 16];
        let mut count = 0;
        // SAFETY: bounded repr(C) buffer matches native ABI; same owner thread.
        checked(unsafe {
            yv_call_list(
                self.handle.as_ptr(),
                items.as_mut_ptr(),
                items.len() as u32,
                &mut count,
            )
        })?;
        if count as usize > items.len() {
            return Err(-9);
        }
        Ok(items[..count as usize].to_vec())
    }
    pub fn answer(&mut self, token: u64) -> Result<(), i32> {
        // SAFETY: same-thread handle, opaque incoming token checked by native bridge.
        checked(unsafe { yv_call_answer(self.handle.as_ptr(), token) })
    }
    pub fn reject(&mut self, token: u64, status: i32) -> Result<(), i32> {
        if status != 480 && status != 603 {
            return Err(-6);
        }
        // SAFETY: same-thread handle, checked SIP status and opaque incoming token.
        checked(unsafe { yv_call_reject(self.handle.as_ptr(), token, status) })
    }
    pub fn dial(&mut self, account: u64, token: u64, uri: &str, audio: bool) -> Result<(), i32> {
        if uri.len() > 512 {
            return Err(-6);
        }
        // SAFETY: bounded UTF-8 buffer remains alive through synchronous copying; owner-only handle.
        checked(unsafe {
            yv_call_start(
                self.handle.as_ptr(),
                account,
                token,
                uri.as_ptr(),
                uri.len() as u32,
                i32::from(audio),
            )
        })
    }
    pub fn call(&mut self, token: u64) -> Result<NativeCall, i32> {
        let mut out = NativeCall::default();
        // SAFETY: repr(C) output matches bridge.h and is writable on the owner thread.
        checked(unsafe { yv_call_snapshot(self.handle.as_ptr(), token, &mut out) })?;
        Ok(out)
    }
    pub fn hangup(&mut self, token: u64) -> Result<(), i32> {
        // SAFETY: same-thread live handle and scalar application token.
        checked(unsafe { yv_call_hangup(self.handle.as_ptr(), token) })
    }
    pub fn mute(&mut self, token: u64, muted: bool) -> Result<(), i32> {
        // SAFETY: same-thread live handle, scalar token and validated boolean.
        checked(unsafe { yv_call_mute(self.handle.as_ptr(), token, i32::from(muted)) })
    }
    pub fn start_recording(&mut self, token: u64, path: &str) -> Result<(), i32> {
        if path.is_empty() || path.len() > 4096 || path.as_bytes().contains(&0) {
            return Err(-6);
        }
        // SAFETY: path is bounded and remains alive through the synchronous native copy.
        checked(unsafe {
            yv_call_record_start(
                self.handle.as_ptr(),
                token,
                path.as_ptr(),
                path.len() as u32,
            )
        })
    }
    pub fn stop_recording(&mut self, token: u64) -> Result<(), i32> {
        // SAFETY: live same-thread handle and validated scalar token.
        checked(unsafe { yv_call_record_stop(self.handle.as_ptr(), token) })
    }
    pub fn dtmf(&mut self, token: u64, digit: char) -> Result<(), i32> {
        if !digit.is_ascii_digit() && digit != '*' && digit != '#' {
            return Err(-6);
        }
        let byte = digit as u8;
        // SAFETY: one validated DTMF digit remains live for the synchronous native call.
        checked(unsafe { yv_call_dtmf(self.handle.as_ptr(), token, &byte, 1) })
    }
    pub fn controls(&mut self, token: u64) -> Result<NativeCallControls, i32> {
        // SAFETY: live owner-thread handle; bridge checks thread affinity.

        let mut out = NativeCallControls::default();
        checked(unsafe { yv_call_controls(self.handle.as_ptr(), token, &mut out) })?;
        Ok(out)
    }
    pub fn hold(&mut self, token: u64, held: bool) -> Result<(), i32> {
        // SAFETY: live owner-thread handle; bridge checks thread affinity.
        checked(unsafe { yv_call_hold(self.handle.as_ptr(), token, i32::from(held)) })
    }
    pub fn transfer(&mut self, token: u64, uri: &str, consultation: u64) -> Result<(), i32> {
        // SAFETY: live owner-thread handle; bridge checks thread affinity.

        if uri.len() > 512 || uri.bytes().any(|x| x == 0) {
            return Err(-6);
        }
        checked(unsafe {
            yv_call_transfer(
                self.handle.as_ptr(),
                token,
                uri.as_ptr(),
                uri.len() as u32,
                consultation,
            )
        })
    }
    pub fn release_call(&mut self, token: u64) -> Result<(), i32> {
        // SAFETY: same-thread live handle, removes wrapper only outside callbacks.
        checked(unsafe { yv_call_release(self.handle.as_ptr(), token) })
    }

    pub fn add_account(
        &mut self,
        token: u64,
        account: &yeyofone_application::accounts::Account,
        password: &yeyofone_domain::SipCredentials,
    ) -> Result<(), i32> {
        account.validate().map_err(|_| -6)?;
        if account.host.contains(':') {
            return Err(-10);
        } // IPv6 transport profile requires separate verification.
        password.expose(|secret| {
            let config = NativeAccountConfig {
                token,
                username: account.username.as_ptr(),
                username_len: account.username.len() as u32,
                host: account.host.as_ptr(),
                host_len: account.host.len() as u32,
                password: secret.as_ptr(),
                password_len: secret.len() as u32,
                port: u32::from(account.port),
                transport: match account.transport.as_str() {
                    "udp" => 0,
                    "tcp" => 1,
                    "tls" => 2,
                    _ => return Err(-6),
                },
            };
            // SAFETY: all pointers refer to validated, bounded buffers alive through this synchronous call. Native copies them and never retains Rust pointers.
            checked(unsafe { yv_account_add(self.handle.as_ptr(), &config) })
        })
    }
    pub fn remove_account(&mut self, token: u64) -> Result<(), i32> {
        // SAFETY: live same-thread handle, numeric generation token only.
        checked(unsafe { yv_account_remove(self.handle.as_ptr(), token) })
    }
    pub fn register(&mut self, token: u64, renew: bool) -> Result<(), i32> {
        // SAFETY: live same-thread handle; native validates token and boolean.
        checked(unsafe { yv_account_register(self.handle.as_ptr(), token, i32::from(renew)) })
    }
    pub fn registration(&mut self, token: u64) -> Result<NativeRegistration, i32> {
        let mut result = NativeRegistration::default();
        // SAFETY: repr(C) layout matches bridge.h, synchronous same-thread valid output.
        checked(unsafe { yv_registration(self.handle.as_ptr(), token, &mut result) })?;
        Ok(result)
    }
    pub fn failure_kind(status: i32, code: i32) -> i32 {
        // SAFETY: pure numeric classification, no pointers or mutable native state.
        unsafe { yv_failure_kind(status, code) }
    }
    pub fn network_online() -> Option<bool> {
        // SAFETY: bounded OS interface enumeration; no native engine state/pointers.
        match unsafe { yv_network_online() } {
            0 => Some(false),
            1 => Some(true),
            _ => None,
        }
    }
    pub fn create() -> Result<Self, i32> {
        let mut output = std::ptr::null_mut();
        // SAFETY: out-pointer is valid; C++ initializes it and contains all exceptions.
        checked(unsafe { yv_create(&mut output) })?;
        let handle = NonNull::new(output).ok_or(-3)?;
        Ok(Self {
            handle,
            _owner_only: PhantomData,
        })
    }
    pub fn start(&mut self, port: u16) -> Result<(), i32> {
        // SAFETY: unique live handle, called only on its creating thread (!Send/!Sync).
        checked(unsafe { yv_start(self.handle.as_ptr(), u32::from(port)) })
    }
    pub fn stop(&mut self) -> Result<(), i32> {
        // SAFETY: unique live handle remains valid after idempotent stop.
        checked(unsafe { yv_stop(self.handle.as_ptr()) })
    }
    pub fn pump(&mut self) -> Result<(), i32> {
        // SAFETY: same-thread live handle; no Rust callback or reference crosses FFI.
        checked(unsafe { yv_pump(self.handle.as_ptr()) })
    }
    pub fn next_event(&mut self) -> Result<Option<NativeEvent>, i32> {
        let mut event = NativeEvent::default();
        // SAFETY: ABI layout matches bridge.h; output is valid for this synchronous call.
        match unsafe { yv_next_event(self.handle.as_ptr(), &mut event) } {
            0 => Ok(None),
            1 => Ok(Some(event)),
            code => Err(code),
        }
    }
}
impl Drop for Native {
    fn drop(&mut self) {
        // SAFETY: last unique handle, on its creating thread. C++ disposes before deleting.
        // On native cleanup failure C++ deliberately retains the live allocation; no UAF.
        let _ = unsafe { yv_destroy(self.handle.as_ptr()) };
    }
}
