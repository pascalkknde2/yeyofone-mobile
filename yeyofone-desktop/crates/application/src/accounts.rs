//! Validated public accounts and persistence/vault orchestration. Secrets are never serializable.
use serde::{Deserialize, Serialize};
use yeyofone_domain::{CredentialRef, CredentialStore, SipCredentials};
use zeroize::Zeroizing;
#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Account {
    pub id: String,
    pub label: String,
    pub username: String,
    pub host: String,
    pub port: u16,
    pub transport: String,
    pub enabled: bool,
    pub stun_server: Option<String>,
    pub ice: bool,
    pub turn_server: Option<String>,
    pub turn_username: Option<String>,
}
impl Account {
    pub fn validate(&self) -> Result<(), AccountError> {
        let valid_id = !self.id.is_empty()
            && self.id.len() <= 64
            && self
                .id
                .bytes()
                .all(|c| c.is_ascii_alphanumeric() || c == b'-');
        let token = |s: &str| {
            !s.is_empty()
                && s.len() <= 128
                && s.bytes()
                    .all(|c| c.is_ascii_alphanumeric() || b"-_.+".contains(&c))
        };
        let host = |s: &str| {
            !s.is_empty()
                && s.len() <= 253
                && (s.parse::<std::net::IpAddr>().is_ok()
                    || s.split('.').all(|part| {
                        !part.is_empty()
                            && part.len() <= 63
                            && !part.starts_with('-')
                            && !part.ends_with('-')
                            && part.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'-')
                    }))
        };
        if !valid_id
            || self.label.trim().is_empty()
            || self.label.len() > 128
            || self.label.chars().any(char::is_control)
            || !token(&self.username)
            || !host(&self.host)
            || self.port == 0
            || !["udp", "tcp", "tls"].contains(&self.transport.as_str())
            || self.stun_server.as_deref().is_some_and(|s| !host(s))
            || self.turn_server.as_deref().is_some_and(|s| !host(s))
            || self.turn_username.as_deref().is_some_and(|s| !token(s))
            || self.turn_server.is_some() != self.turn_username.is_some()
        {
            return Err(AccountError::InvalidInput);
        }
        Ok(())
    }
}
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum AccountError {
    InvalidInput,
    StorageUnavailable,
    VaultUnavailable,
    NotFound,
    Capacity,
}
pub struct StoredAccount {
    pub account: Account,
    pub sip_ref: String,
    pub turn_ref: Option<String>,
}
pub trait AccountRepository: Send {
    fn list(&self) -> Result<Vec<StoredAccount>, AccountError>;
    /// Atomically replace a row and queue old references for deletion.
    fn save(&mut self, account: &StoredAccount) -> Result<(), AccountError>;
    fn delete(&mut self, id: &str) -> Result<(), AccountError>;
    fn stage(&mut self, reference: &str) -> Result<(), AccountError>;
    fn garbage(&self) -> Result<Vec<String>, AccountError>;
    fn cleaned(&mut self, reference: &str) -> Result<(), AccountError>;
}
pub struct AccountService<R, V> {
    repository: R,
    vault: V,
}
impl<R: AccountRepository, V: CredentialStore> AccountService<R, V> {
    pub fn new(repository: R, vault: V) -> Self {
        Self { repository, vault }
    }
    pub fn list(&self) -> Result<Vec<Account>, AccountError> {
        Ok(self
            .repository
            .list()?
            .into_iter()
            .map(|s| s.account)
            .collect())
    }
    /// Native-only secret access for registration; never used in an IPC response.
    pub fn registration_account(
        &self,
        id: &str,
    ) -> Result<(Account, SipCredentials), AccountError> {
        let stored = self
            .repository
            .list()?
            .into_iter()
            .find(|s| s.account.id == id)
            .ok_or(AccountError::NotFound)?;
        if !stored.account.enabled {
            return Err(AccountError::InvalidInput);
        }
        let key =
            CredentialRef::new(&stored.sip_ref).map_err(|_| AccountError::StorageUnavailable)?;
        let secret = self
            .vault
            .read(&key)
            .map_err(|_| AccountError::VaultUnavailable)?;
        Ok((stored.account, secret))
    }
    pub fn cleanup(&mut self) -> Result<(), AccountError> {
        for reference in self.repository.garbage()? {
            self.vault
                .remove(
                    &CredentialRef::new(&reference)
                        .map_err(|_| AccountError::StorageUnavailable)?,
                )
                .map_err(|_| AccountError::VaultUnavailable)?;
            self.repository.cleaned(&reference)?;
        }
        Ok(())
    }
    pub fn save(
        &mut self,
        account: Account,
        password: Option<Zeroizing<String>>,
        turn_password: Option<Zeroizing<String>>,
        nonce: &str,
    ) -> Result<(), AccountError> {
        account.validate()?;
        if account.turn_server.is_none() && turn_password.is_some() {
            return Err(AccountError::InvalidInput);
        }
        if [password.as_ref(), turn_password.as_ref()]
            .into_iter()
            .flatten()
            .any(|s| s.is_empty() || s.len() > 4096 || s.contains('\0'))
        {
            return Err(AccountError::InvalidInput);
        }
        let rows = self.repository.list()?;
        let previous = rows.iter().find(|r| r.account.id == account.id);
        // A WebView may edit public fields, but must never redirect an existing vault secret to a new provider identity.
        if let Some(old) = previous {
            let identity_changed = old.account.username != account.username
                || !old.account.host.eq_ignore_ascii_case(&account.host)
                || old.account.port != account.port
                || old.account.transport != account.transport;
            if identity_changed && password.is_none() {
                return Err(AccountError::InvalidInput);
            }
            let turn_changed = old.account.turn_server != account.turn_server
                || old.account.turn_username != account.turn_username;
            if account.turn_server.is_some() && turn_changed && turn_password.is_none() {
                return Err(AccountError::InvalidInput);
            }
        }
        if previous.is_none() && rows.len() >= 32 {
            return Err(AccountError::Capacity);
        }
        if previous.is_none() && password.is_none() {
            return Err(AccountError::InvalidInput);
        }
        let sip_ref = if password.is_some() {
            format!("sip-{}-{nonce}", account.id)
        } else {
            previous.ok_or(AccountError::NotFound)?.sip_ref.clone()
        };
        let turn_ref = if account.turn_server.is_none() {
            None
        } else if turn_password.is_some() {
            Some(format!("turn-{}-{nonce}", account.id))
        } else {
            previous.and_then(|s| s.turn_ref.clone())
        };
        if account.turn_server.is_some() && turn_ref.is_none() {
            return Err(AccountError::InvalidInput);
        }
        let mut written = Vec::new();
        for (reference, secret) in [
            (Some(&sip_ref), password.as_ref()),
            (turn_ref.as_ref(), turn_password.as_ref()),
        ] {
            if let (Some(reference), Some(secret)) = (reference, secret) {
                let key = CredentialRef::new(reference).map_err(|_| AccountError::InvalidInput)?;
                let secret = SipCredentials::new(secret.as_bytes().to_vec())
                    .map_err(|_| AccountError::InvalidInput)?;
                self.repository.stage(reference)?;
                if self.vault.write(&key, &secret).is_err() {
                    for k in &written {
                        let _ = self.vault.remove(k);
                    }
                    return Err(AccountError::VaultUnavailable);
                }
                written.push(key);
            }
        }
        if let Err(e) = self.repository.save(&StoredAccount {
            account,
            sip_ref,
            turn_ref,
        }) {
            for k in &written {
                let _ = self.vault.remove(k);
            }
            return Err(e);
        }
        // Old secrets are durably queued; a locked vault can be retried at next startup.
        let _ = self.cleanup();
        Ok(())
    }
    pub fn delete(&mut self, id: &str) -> Result<(), AccountError> {
        self.repository.delete(id)?;
        let _ = self.cleanup();
        Ok(())
    }
}
