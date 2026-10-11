//! Call handoff: a call that another device of the same account has offered to move here.
use serde::Serialize;

#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct HandoffOffer {
    /// Stable id for this offer, used to take the call over.
    pub id: String,
    pub account_id: String,
    /// The other party's number or extension.
    pub remote: String,
    /// The other party's display name, when the offering device knows it.
    pub remote_name: Option<String>,
    /// Name of the device the call is on, such as "iPhone".
    pub device_name: String,
    /// When the call connected, in Unix milliseconds; 0 when unknown.
    pub connected_at_ms: i64,
}
