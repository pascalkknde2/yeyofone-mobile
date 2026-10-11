//! Call handoff between devices of the same account.
//!
//! Devices talk through SIP MESSAGEs to their own address, which the PBX delivers to every
//! registered device. A device on a call announces it ("call", repeated while it lasts, then
//! "ended"); a device that registers asks for current calls ("query"). An announced call becomes
//! an offer that can be taken over with an INVITE carrying Replaces (see Calls::take_over).
use super::*;
use serde::Deserialize;
use std::collections::BTreeSet;
use std::hash::{BuildHasher, Hash, Hasher};
use yeyofone_application::handoff::HandoffOffer;

/// Offering devices re-announce every 15 s; an offer not refreshed in this long is dropped.
const OFFER_TTL_MS: u64 = 45_000;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Wire {
    v: u8,
    #[serde(rename = "type")]
    kind: String,
    device: String,
    name: Option<String>,
    call_id: Option<String>,
    local_tag: Option<String>,
    remote_tag: Option<String>,
    remote: Option<String>,
    remote_name: Option<String>,
    connected_at: Option<i64>,
}

pub(super) struct Offer {
    pub public: HandoffOffer,
    pub call_id: String,
    /// The offering device's own tag; the takeover INVITE sends it as Replaces from-tag.
    pub local_tag: String,
    /// The PBX's tag in the offering device's dialog; sent as Replaces to-tag.
    pub remote_tag: String,
    seen: u64,
}

pub(super) struct Handoffs {
    device: String,
    offers: BTreeMap<String, Offer>,
    queried: BTreeSet<String>,
    origin: Instant,
}

fn sip_token(value: &str, max: usize, call_id: bool) -> bool {
    !value.is_empty()
        && value.len() <= max
        && value.bytes().all(|b| {
            b.is_ascii_alphanumeric()
                || b"-.!%*_+`'~".contains(&b)
                || (call_id && b"@:/".contains(&b))
        })
}

fn remote_party(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 64
        && value
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b"+*#._-".contains(&b))
}

/// Printable text of at most `max` characters, or None.
fn label(value: Option<String>, max: usize) -> Option<String> {
    let text: String = value?
        .chars()
        .filter(|c| !c.is_control())
        .take(max)
        .collect();
    let text = text.trim().to_string();
    (!text.is_empty()).then_some(text)
}

impl Handoffs {
    pub fn new() -> Self {
        // A per-process id, so this device ignores its own messages echoed back by the PBX.
        let mut hasher = std::collections::hash_map::RandomState::new().build_hasher();
        std::time::SystemTime::now().hash(&mut hasher);
        std::process::id().hash(&mut hasher);
        Self {
            device: format!("desktop-{:016x}", hasher.finish()),
            offers: BTreeMap::new(),
            queried: BTreeSet::new(),
            origin: Instant::now(),
        }
    }

    fn now(&self) -> u64 {
        self.origin.elapsed().as_millis().min(u128::from(u64::MAX)) as u64
    }

    fn expire(&mut self) {
        let now = self.now();
        self.offers
            .retain(|_, offer| now.saturating_sub(offer.seen) < OFFER_TTL_MS);
    }

    pub fn list(&mut self) -> Vec<HandoffOffer> {
        self.expire();
        self.offers.values().map(|o| o.public.clone()).collect()
    }

    /// Removes and returns an offer to take it over.
    pub fn take(&mut self, id: &str) -> Option<Offer> {
        self.expire();
        self.offers.remove(id)
    }

    pub fn poll(&mut self, native: &mut ffi::Native, registrations: &Registrations) {
        let now = registrations.now();
        for (id, registration) in &registrations.accounts {
            let registered = registration.token != 0
                && registration.machine.snapshot(id, now).state == "registered";
            if !registered {
                self.queried.remove(id);
                self.offers.retain(|_, o| &o.public.account_id != id);
            } else if !self.queried.contains(id) {
                let query = serde_json::json!({"v":1,"type":"query","device":self.device});
                if native
                    .handoff_send(registration.token, &query.to_string())
                    .is_ok()
                {
                    self.queried.insert(id.clone());
                }
            }
        }
        for _ in 0..16 {
            match native.handoff_next() {
                Ok(Some((token, body))) => self.receive(token, &body, registrations),
                _ => break,
            }
        }
        self.expire();
    }

    fn receive(&mut self, token: u64, body: &[u8], registrations: &Registrations) {
        let Some(account_id) = registrations
            .accounts
            .iter()
            .find(|(_, r)| r.token == token)
            .map(|(id, _)| id.clone())
        else {
            return;
        };
        let Ok(wire) = serde_json::from_slice::<Wire>(body) else {
            return;
        };
        if wire.v != 1 || wire.device == self.device || wire.device.len() > 64 {
            return;
        }
        match wire.kind.as_str() {
            "call" => {
                let (Some(call_id), Some(local_tag), Some(remote_tag), Some(remote)) =
                    (wire.call_id, wire.local_tag, wire.remote_tag, wire.remote)
                else {
                    return;
                };
                if !sip_token(&call_id, 128, true)
                    || !sip_token(&local_tag, 64, false)
                    || !sip_token(&remote_tag, 64, false)
                    || !remote_party(&remote)
                {
                    return;
                }
                let mut hasher = std::collections::hash_map::DefaultHasher::new();
                (&account_id, &call_id).hash(&mut hasher);
                let id = format!("handoff-{:016x}", hasher.finish());
                let seen = self.now();
                self.offers.insert(
                    id.clone(),
                    Offer {
                        public: HandoffOffer {
                            id,
                            account_id,
                            remote,
                            remote_name: label(wire.remote_name, 64),
                            device_name: label(wire.name, 32)
                                .unwrap_or_else(|| "another device".into()),
                            connected_at_ms: wire.connected_at.unwrap_or(0).max(0),
                        },
                        call_id,
                        local_tag,
                        remote_tag,
                        seen,
                    },
                );
            }
            "ended" => {
                if let Some(call_id) = wire.call_id {
                    self.offers.retain(|_, o| {
                        !(o.public.account_id == account_id && o.call_id == call_id)
                    });
                }
            }
            _ => {}
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_dialog_identifiers_and_parties() {
        assert!(sip_token("5C212CB1-9769-4170-969B-39FD380CE764", 128, true));
        assert!(sip_token("abc@host.example", 128, true));
        assert!(!sip_token("abc@host", 64, false));
        assert!(!sip_token("tag;evil=1", 64, false));
        assert!(!sip_token("", 64, false));
        assert!(remote_party("1002"));
        assert!(remote_party("+447700900123"));
        assert!(!remote_party("1002@evil"));
        assert!(!remote_party("10 02"));
    }

    #[test]
    fn labels_are_trimmed_and_bounded() {
        assert_eq!(
            label(Some("  iPhone  ".into()), 32).as_deref(),
            Some("iPhone")
        );
        assert_eq!(label(Some("\u{7}".into()), 32), None);
        assert_eq!(label(Some("x".repeat(80)), 32).map(|s| s.len()), Some(32));
    }
}
