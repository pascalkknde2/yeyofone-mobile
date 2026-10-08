//! Explicit wire contracts. Domain credentials and native types cannot be serialized here.
use serde::{Deserialize, Serialize};
use yeyofone_application::ApplicationState;
use yeyofone_domain::EngineState;

pub const SCHEMA_VERSION: u8 = 1;
const MAX_REQUEST_BYTES: usize = 512;

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Request {
    pub schema_version: u8,
    pub request_id: String,
}

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Response<T> {
    pub schema_version: u8,
    pub request_id: String,
    pub sequence: u64,
    pub data: T,
}

/// Reserved event contract: sequence strictly increases per runtime instance.
/// Consumers discard duplicates and resynchronize on gaps; no events are emitted yet.
#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct EventEnvelope<T> {
    pub schema_version: u8,
    pub runtime_id: String,
    pub sequence: u64,
    pub data: T,
}

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ErrorCode {
    InvalidRequest,
    UnsupportedVersion,
    EngineUnavailable,
    AccountUnavailable,
    VaultUnavailable,
    CallUnavailable,
    Busy,
}

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct IpcError {
    pub schema_version: u8,
    pub request_id: Option<String>,
    pub code: ErrorCode,
}

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct RuntimeStatus {
    pub engine: String,
    pub calling_available: bool,
}

fn error(code: ErrorCode, request_id: Option<String>) -> IpcError {
    IpcError {
        schema_version: SCHEMA_VERSION,
        request_id,
        code,
    }
}

pub fn validate_request(value: serde_json::Value) -> Result<Request, IpcError> {
    if value.to_string().len() > MAX_REQUEST_BYTES {
        return Err(error(ErrorCode::InvalidRequest, None));
    }
    let request: Request =
        serde_json::from_value(value).map_err(|_| error(ErrorCode::InvalidRequest, None))?;
    if request.request_id.is_empty()
        || request.request_id.len() > 64
        || !request
            .request_id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-')
    {
        return Err(error(ErrorCode::InvalidRequest, None));
    }
    if request.schema_version != SCHEMA_VERSION {
        return Err(error(
            ErrorCode::UnsupportedVersion,
            Some(request.request_id),
        ));
    }
    Ok(request)
}

pub fn runtime_status(value: serde_json::Value) -> Result<Response<RuntimeStatus>, IpcError> {
    let request = validate_request(value)?;
    Ok(status_response(
        request,
        ApplicationState::default().engine(),
        0,
    ))
}

pub fn status_response(
    request: Request,
    state: EngineState,
    sequence: u64,
) -> Response<RuntimeStatus> {
    let engine = match state {
        EngineState::Stopped => "stopped",
        EngineState::Starting => "starting",
        EngineState::Running => "running",
        EngineState::Stopping => "stopping",
        EngineState::Failed => "failed",
    };
    Response {
        schema_version: SCHEMA_VERSION,
        request_id: request.request_id,
        sequence,
        data: RuntimeStatus {
            engine: engine.into(),
            calling_available: false,
        },
    }
}

pub fn engine_unavailable(request_id: String) -> IpcError {
    error(ErrorCode::EngineUnavailable, Some(request_id))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn status_is_correlated_and_contains_only_public_fields() {
        let response = runtime_status(json!({"schemaVersion":1,"requestId":"test-123"})).unwrap();
        let wire = serde_json::to_value(&response).unwrap();
        assert_eq!(
            wire,
            json!({"schemaVersion":1,"requestId":"test-123","sequence":0,"data":{"engine":"stopped","callingAvailable":false}})
        );
        assert_eq!(
            serde_json::from_value::<Response<RuntimeStatus>>(wire).unwrap(),
            response
        );
    }
    #[test]
    fn malformed_and_oversized_requests_are_rejected_without_echoing_input() {
        for value in [
            json!(null),
            json!({}),
            json!({"schemaVersion":1,"requestId":123}),
            json!({"schemaVersion":1,"requestId":"bad id"}),
            json!({"schemaVersion":1,"requestId":""}),
            json!({"schemaVersion":1,"requestId":"a".repeat(65)}),
            json!({"schemaVersion":1,"requestId":"ok","password":"private"}),
            json!({"schemaVersion":1,"requestId":"a".repeat(513)}),
        ] {
            let error = runtime_status(value).unwrap_err();
            assert_eq!(error.code, ErrorCode::InvalidRequest);
            assert_eq!(error.request_id, None);
        }
    }
    #[test]
    fn unsupported_version_preserves_valid_correlation() {
        let error = runtime_status(json!({"schemaVersion":2,"requestId":"test"})).unwrap_err();
        assert_eq!(error.code, ErrorCode::UnsupportedVersion);
        assert_eq!(error.request_id.as_deref(), Some("test"));
    }
    #[test]
    fn live_engine_status_uses_authoritative_state_and_sequence() {
        let request = validate_request(json!({"schemaVersion":1,"requestId":"live"})).unwrap();
        let response = status_response(request, EngineState::Running, 2);
        assert_eq!(response.sequence, 2);
        assert_eq!(response.data.engine, "running");
        assert!(!response.data.calling_available);
    }
    #[test]
    fn event_contract_round_trips() {
        let event = EventEnvelope {
            schema_version: 1,
            runtime_id: "runtime-1".into(),
            sequence: 1,
            data: "stopped",
        };
        let parsed: EventEnvelope<String> =
            serde_json::from_str(&serde_json::to_string(&event).unwrap()).unwrap();
        assert_eq!(parsed.sequence, 1);
        assert_eq!(parsed.runtime_id, "runtime-1");
    }
}

#[derive(serde::Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct AccountsRequest {
    pub schema_version: u8,
    pub request_id: String,
    pub action: String,
    pub account: Option<yeyofone_application::accounts::Account>,
    pub id: Option<String>,
    pub password: Option<String>,
    pub turn_password: Option<String>,
}

pub fn validate_accounts_request(value: serde_json::Value) -> Result<AccountsRequest, IpcError> {
    if value.to_string().len() > 16000 {
        return Err(error(ErrorCode::InvalidRequest, None));
    }
    let request: AccountsRequest =
        serde_json::from_value(value).map_err(|_| error(ErrorCode::InvalidRequest, None))?;
    validate_request(
        serde_json::json!({"schemaVersion":request.schema_version,"requestId":request.request_id}),
    )?;
    let valid = match request.action.as_str() {
        "list" => {
            request.account.is_none()
                && request.id.is_none()
                && request.password.is_none()
                && request.turn_password.is_none()
        }
        "save" => {
            request
                .account
                .as_ref()
                .is_some_and(|a| a.validate().is_ok())
                && request.id.is_none()
        }
        "delete" => {
            request.account.is_none()
                && request.password.is_none()
                && request.turn_password.is_none()
                && request.id.as_ref().is_some_and(|s| {
                    !s.is_empty()
                        && s.len() <= 64
                        && s.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'-')
                })
        }
        _ => false,
    };
    if !valid {
        return Err(error(ErrorCode::InvalidRequest, Some(request.request_id)));
    }
    Ok(request)
}
#[cfg(test)]
mod account_wire_tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn account_commands_reject_unknown_secret_fields_versions_and_actions() {
        for value in [
            json!({"schemaVersion":1,"requestId":"one","action":"list","password":"SECRET"}),
            json!({"schemaVersion":1,"requestId":"one","action":"list","unknown":"SECRET"}),
            json!({"schemaVersion":1,"requestId":"one","action":"register"}),
            json!({"schemaVersion":1,"requestId":"one","action":"delete","id":"../bad"}),
            json!({"schemaVersion":2,"requestId":"one","action":"list"}),
        ] {
            let result = validate_accounts_request(value);
            assert!(result.is_err());
            let wire = serde_json::to_string(&result.err().unwrap()).unwrap();
            assert!(!wire.contains("SECRET"));
        }
        assert!(
            validate_accounts_request(json!({"schemaVersion":1,"requestId":"one","action":"list"}))
                .is_ok()
        );
    }
}

#[derive(Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum RegistrationAction {
    Status,
    Register,
    Refresh,
    Unregister,
}
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct RegistrationRequest {
    pub schema_version: u8,
    pub request_id: String,
    pub action: RegistrationAction,
    pub id: Option<String>,
}
pub fn validate_registration_request(
    value: serde_json::Value,
) -> Result<RegistrationRequest, IpcError> {
    if value.to_string().len() > 512 {
        return Err(error(ErrorCode::InvalidRequest, None));
    }
    let request: RegistrationRequest =
        serde_json::from_value(value).map_err(|_| error(ErrorCode::InvalidRequest, None))?;
    validate_request(
        serde_json::json!({"schemaVersion":request.schema_version,"requestId":request.request_id}),
    )?;
    let valid = if request.action == RegistrationAction::Status {
        request.id.is_none()
    } else {
        request.id.as_ref().is_some_and(|s| {
            !s.is_empty()
                && s.len() <= 64
                && s.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'-')
        })
    };
    if !valid {
        return Err(error(ErrorCode::InvalidRequest, Some(request.request_id)));
    }
    Ok(request)
}
pub fn registration_error(request_id: String, code: ErrorCode) -> IpcError {
    error(code, Some(request_id))
}
#[cfg(test)]
mod registration_wire_tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn strict_registration_commands_reject_secrets_paths_and_unknown_actions() {
        for request in [
            json!({"schemaVersion":1,"requestId":"test","action":"register","id":"../bad"}),
            json!({"schemaVersion":1,"requestId":"test","action":"register","id":"one","password":"PRIVATE"}),
            json!({"schemaVersion":1,"requestId":"test","action":"delete","id":"one"}),
            json!({"schemaVersion":1,"requestId":"test","action":"status","id":"one"}),
            json!({"schemaVersion":1,"requestId":"test","action":"register"}),
        ] {
            let e = validate_registration_request(request).unwrap_err();
            assert!(!serde_json::to_string(&e).unwrap().contains("PRIVATE"));
        }
        assert!(
            validate_registration_request(
                json!({"schemaVersion":1,"requestId":"test","action":"status"})
            )
            .is_ok()
        );
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct DestinationRequest {
    pub schema_version: u8,
    pub request_id: String,
    pub destination: String,
}
pub fn validate_destination_request(
    value: serde_json::Value,
) -> Result<DestinationRequest, IpcError> {
    if value.to_string().len() > 2048 {
        return Err(error(ErrorCode::InvalidRequest, None));
    }
    let req: DestinationRequest =
        serde_json::from_value(value).map_err(|_| error(ErrorCode::InvalidRequest, None))?;
    validate_request(
        serde_json::json!({"schemaVersion":req.schema_version,"requestId":req.request_id}),
    )?;
    if req.destination.len() > 512 {
        return Err(error(ErrorCode::InvalidRequest, Some(req.request_id)));
    }
    Ok(req)
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DestinationResult {
    pub kind: String,
    pub normalized: String,
    pub calling_available: bool,
}
pub fn destination_response(
    value: serde_json::Value,
) -> Result<Response<DestinationResult>, IpcError> {
    let req = validate_destination_request(value)?;
    let parsed = yeyofone_domain::parse_destination(&req.destination)
        .map_err(|_| error(ErrorCode::InvalidRequest, Some(req.request_id.clone())))?;
    Ok(Response {
        schema_version: 1,
        request_id: req.request_id,
        sequence: 0,
        data: DestinationResult {
            kind: parsed.kind.into(),
            normalized: parsed.normalized,
            calling_available: false,
        },
    })
}
#[cfg(test)]
mod destination_wire_tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn validation_is_correlated_and_never_enables_calls() {
        let r = destination_response(
            json!({"schemaVersion":1,"requestId":"dial","destination":"1005"}),
        )
        .unwrap();
        assert_eq!(r.request_id, "dial");
        assert_eq!(r.data.normalized, "1005");
        assert!(!r.data.calling_available);
    }
    #[test]
    fn rejects_untrusted_wire_without_echoing_input() {
        for v in [
            json!({"schemaVersion":1,"requestId":"dial","destination":"sip:a:SECRET@x"}),
            json!({"schemaVersion":1,"requestId":"dial","destination":"1005","password":"SECRET"}),
            json!({"schemaVersion":2,"requestId":"dial","destination":"1005"}),
        ] {
            let e = destination_response(v).err().unwrap();
            assert!(!serde_json::to_string(&e).unwrap().contains("SECRET"));
        }
    }
}

#[derive(Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum CallAction {
    Status,
    Dial,
    Hangup,
    Mute,
    Answer,
    Reject,
    RecordStart,
    RecordStop,
    Dtmf,
    Hold,
    Resume,
    Transfer,
    ConsultStart,
    ConsultComplete,
    ConsultCancel,
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct CallRequest {
    pub schema_version: u8,
    pub request_id: String,
    pub action: CallAction,
    pub id: Option<String>,
    pub account_id: Option<String>,
    pub destination: Option<String>,
    pub muted: Option<bool>,
    pub digits: Option<String>,
    pub consult_id: Option<String>,
}
pub fn validate_call_request(value: serde_json::Value) -> Result<CallRequest, IpcError> {
    if value.to_string().len() > 2048 {
        return Err(error(ErrorCode::InvalidRequest, None));
    }
    let req: CallRequest =
        serde_json::from_value(value).map_err(|_| error(ErrorCode::InvalidRequest, None))?;
    validate_request(
        serde_json::json!({"schemaVersion":req.schema_version,"requestId":req.request_id}),
    )?;
    let identifier = |s: &Option<String>| {
        s.as_ref().is_some_and(|s| {
            !s.is_empty()
                && s.len() <= 64
                && s.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-')
        })
    };
    let consultation_action = matches!(
        req.action,
        CallAction::ConsultStart | CallAction::ConsultComplete | CallAction::ConsultCancel
    );
    if req.consult_id.is_some() && !consultation_action {
        return Err(error(ErrorCode::InvalidRequest, Some(req.request_id)));
    }
    let valid = match req.action {
        CallAction::Hold | CallAction::Resume => {
            identifier(&req.id)
                && req.account_id.is_none()
                && req.destination.is_none()
                && req.muted.is_none()
                && req.digits.is_none()
        }
        CallAction::Transfer | CallAction::ConsultStart => {
            identifier(&req.id)
                && req.account_id.is_none()
                && req
                    .destination
                    .as_ref()
                    .is_some_and(|s| yeyofone_domain::parse_destination(s).is_ok())
                && req.muted.is_none()
                && req.digits.is_none()
                && (!consultation_action
                    || (identifier(&req.consult_id) && req.consult_id != req.id))
        }
        CallAction::ConsultComplete | CallAction::ConsultCancel => {
            identifier(&req.id)
                && identifier(&req.consult_id)
                && req.consult_id != req.id
                && req.account_id.is_none()
                && req.destination.is_none()
                && req.muted.is_none()
                && req.digits.is_none()
        }
        CallAction::Status => {
            req.id.is_none()
                && req.account_id.is_none()
                && req.destination.is_none()
                && req.muted.is_none()
                && req.digits.is_none()
        }
        CallAction::Dial => {
            identifier(&req.id)
                && identifier(&req.account_id)
                && req
                    .destination
                    .as_ref()
                    .is_some_and(|s| yeyofone_domain::parse_destination(s).is_ok())
                && req.muted.is_none()
                && req.digits.is_none()
        }
        CallAction::Hangup => {
            identifier(&req.id)
                && req.account_id.is_none()
                && req.destination.is_none()
                && req.muted.is_none()
                && req.digits.is_none()
        }
        CallAction::Answer
        | CallAction::Reject
        | CallAction::RecordStart
        | CallAction::RecordStop => {
            identifier(&req.id)
                && req.account_id.is_none()
                && req.destination.is_none()
                && req.muted.is_none()
                && req.digits.is_none()
        }
        CallAction::Mute => {
            identifier(&req.id)
                && req.account_id.is_none()
                && req.destination.is_none()
                && req.muted.is_some()
                && req.digits.is_none()
        }
        CallAction::Dtmf => {
            identifier(&req.id)
                && req.account_id.is_none()
                && req.destination.is_none()
                && req.muted.is_none()
                && req.digits.as_ref().is_some_and(|s| {
                    s.len() == 1
                        && s.bytes()
                            .all(|b| b.is_ascii_digit() || b == b'*' || b == b'#')
                })
        }
    };
    if !valid {
        return Err(error(ErrorCode::InvalidRequest, Some(req.request_id)));
    }
    Ok(req)
}
#[cfg(test)]
mod call_wire_tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn rejects_overposting_injections_and_incomplete_commands() {
        for v in [
            json!({"schemaVersion":1,"requestId":"test","action":"dial","id":"one","accountId":"one","destination":"sip:a:SECRET@x"}),
            json!({"schemaVersion":1,"requestId":"test","action":"status","password":"SECRET"}),
            json!({"schemaVersion":1,"requestId":"test","action":"hangup","id":"../bad"}),
            json!({"schemaVersion":1,"requestId":"test","action":"mute","id":"one"}),
            json!({"schemaVersion":1,"requestId":"test","action":"dial","id":"one","accountId":"one","destination":"1000","muted":true}),
        ] {
            let e = validate_call_request(v).err().unwrap();
            assert!(!serde_json::to_string(&e).unwrap().contains("SECRET"));
        }
    }
    #[test]
    fn transfer_controls_validate_relationships_and_fields() {
        for action in [
            "hold",
            "resume",
            "transfer",
            "consult_start",
            "consult_complete",
            "consult_cancel",
        ] {
            let mut v =
                json!({"schemaVersion":1,"requestId":"test","action":action,"id":"original"});
            if ["transfer", "consult_start"].contains(&action) {
                v["destination"] = json!("1001");
            }
            if action.starts_with("consult_") {
                v["consultId"] = json!("consultation");
            }
            assert!(validate_call_request(v.clone()).is_ok(), "{action}");
            v["muted"] = json!(true);
            assert!(validate_call_request(v).is_err());
        }
        for v in [
            json!({"schemaVersion":1,"requestId":"test","action":"consult_start","id":"one","consultId":"one","destination":"1001"}),
            json!({"schemaVersion":1,"requestId":"test","action":"transfer","id":"one","destination":"sip:a:secret@host"}),
            json!({"schemaVersion":1,"requestId":"test","action":"resume","id":"one","consultId":"two"}),
            json!({"schemaVersion":1,"requestId":"test","action":"consult_complete","id":"one"}),
        ] {
            assert!(validate_call_request(v).is_err());
        }
    }
    #[test]
    fn accepts_only_explicit_bounded_call_actions() {
        for v in [
            json!({"schemaVersion":1,"requestId":"test","action":"status"}),
            json!({"schemaVersion":1,"requestId":"test","action":"dial","id":"one","accountId":"one","destination":"1000"}),
            json!({"schemaVersion":1,"requestId":"test","action":"mute","id":"one","muted":true}),
            json!({"schemaVersion":1,"requestId":"test","action":"hangup","id":"one"}),
        ] {
            assert!(validate_call_request(v).is_ok());
        }
    }
}
