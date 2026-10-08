use rusqlite::{Connection, params};
use yeyofone_application::accounts::{AccountError, AccountRepository, StoredAccount};
use yeyofone_domain::{CredentialRef, CredentialStore, DomainError, SipCredentials};
pub struct SqliteAccounts(Connection);
impl SqliteAccounts {
    pub fn open(path: &std::path::Path) -> Result<Self, AccountError> {
        let db = Connection::open(path).map_err(|_| AccountError::StorageUnavailable)?;
        db.busy_timeout(std::time::Duration::from_secs(3))
            .map_err(|_| AccountError::StorageUnavailable)?;
        let version: u32 = db
            .pragma_query_value(None, "user_version", |r| r.get(0))
            .map_err(|_| AccountError::StorageUnavailable)?;
        if version > 1 {
            return Err(AccountError::StorageUnavailable);
        }
        db.execute_batch("BEGIN; CREATE TABLE IF NOT EXISTS accounts(id TEXT PRIMARY KEY, public_json TEXT NOT NULL, sip_ref TEXT NOT NULL, turn_ref TEXT); CREATE TABLE IF NOT EXISTS vault_gc(reference TEXT PRIMARY KEY); PRAGMA user_version=1; COMMIT;").map_err(|_|AccountError::StorageUnavailable)?;
        Ok(Self(db))
    }
}
impl AccountRepository for SqliteAccounts {
    fn list(&self) -> Result<Vec<StoredAccount>, AccountError> {
        let mut q = self
            .0
            .prepare("SELECT public_json,sip_ref,turn_ref FROM accounts ORDER BY id LIMIT 33")
            .map_err(|_| AccountError::StorageUnavailable)?;
        let rows = q
            .query_map([], |r| {
                Ok((
                    r.get::<_, String>(0)?,
                    r.get::<_, String>(1)?,
                    r.get::<_, Option<String>>(2)?,
                ))
            })
            .map_err(|_| AccountError::StorageUnavailable)?;
        rows.map(|r| {
            let (json, sip_ref, turn_ref) = r.map_err(|_| AccountError::StorageUnavailable)?;
            let account: yeyofone_application::accounts::Account =
                serde_json::from_str(&json).map_err(|_| AccountError::StorageUnavailable)?;
            account
                .validate()
                .map_err(|_| AccountError::StorageUnavailable)?;
            Ok(StoredAccount {
                account,
                sip_ref,
                turn_ref,
            })
        })
        .collect()
    }
    fn save(&mut self, s: &StoredAccount) -> Result<(), AccountError> {
        let tx = self
            .0
            .transaction()
            .map_err(|_| AccountError::StorageUnavailable)?;
        tx.execute("INSERT OR IGNORE INTO vault_gc SELECT sip_ref FROM accounts WHERE id=?1 AND sip_ref<>?2",params![s.account.id,s.sip_ref]).map_err(|_|AccountError::StorageUnavailable)?;
        tx.execute("INSERT OR IGNORE INTO vault_gc SELECT turn_ref FROM accounts WHERE id=?1 AND turn_ref IS NOT NULL AND (turn_ref<>?2 OR ?2 IS NULL)",params![s.account.id,s.turn_ref]).map_err(|_|AccountError::StorageUnavailable)?;
        let json =
            serde_json::to_string(&s.account).map_err(|_| AccountError::StorageUnavailable)?;
        tx.execute("INSERT INTO accounts VALUES(?1,?2,?3,?4) ON CONFLICT(id) DO UPDATE SET public_json=excluded.public_json,sip_ref=excluded.sip_ref,turn_ref=excluded.turn_ref",params![s.account.id,json,s.sip_ref,s.turn_ref]).map_err(|_|AccountError::StorageUnavailable)?;
        tx.execute(
            "DELETE FROM vault_gc WHERE reference=?1 OR reference=?2",
            params![s.sip_ref, s.turn_ref],
        )
        .map_err(|_| AccountError::StorageUnavailable)?;
        tx.commit().map_err(|_| AccountError::StorageUnavailable)
    }
    fn delete(&mut self, id: &str) -> Result<(), AccountError> {
        let tx = self
            .0
            .transaction()
            .map_err(|_| AccountError::StorageUnavailable)?;
        tx.execute(
            "INSERT OR IGNORE INTO vault_gc SELECT sip_ref FROM accounts WHERE id=?1",
            [id],
        )
        .map_err(|_| AccountError::StorageUnavailable)?;
        tx.execute("INSERT OR IGNORE INTO vault_gc SELECT turn_ref FROM accounts WHERE id=?1 AND turn_ref IS NOT NULL",[id]).map_err(|_|AccountError::StorageUnavailable)?;
        if tx
            .execute("DELETE FROM accounts WHERE id=?1", [id])
            .map_err(|_| AccountError::StorageUnavailable)?
            == 0
        {
            return Err(AccountError::NotFound);
        }
        tx.commit().map_err(|_| AccountError::StorageUnavailable)
    }
    fn stage(&mut self, r: &str) -> Result<(), AccountError> {
        self.0
            .execute("INSERT OR IGNORE INTO vault_gc VALUES(?1)", [r])
            .map_err(|_| AccountError::StorageUnavailable)?;
        Ok(())
    }
    fn garbage(&self) -> Result<Vec<String>, AccountError> {
        let mut q = self
            .0
            .prepare("SELECT reference FROM vault_gc WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE sip_ref=reference OR turn_ref=reference)")
            .map_err(|_| AccountError::StorageUnavailable)?;
        q.query_map([], |r| r.get(0))
            .map_err(|_| AccountError::StorageUnavailable)?
            .collect::<Result<Vec<_>, _>>()
            .map_err(|_| AccountError::StorageUnavailable)
    }
    fn cleaned(&mut self, r: &str) -> Result<(), AccountError> {
        self.0
            .execute("DELETE FROM vault_gc WHERE reference=?1", [r])
            .map_err(|_| AccountError::StorageUnavailable)?;
        Ok(())
    }
}
pub struct OsVault;
#[cfg(target_os = "macos")]
fn entry(r: &CredentialRef) -> Result<keyring::Entry, DomainError> {
    keyring::Entry::new("com.yeyofone.desktop.sip", r.as_str())
        .map_err(|_| DomainError::Unavailable)
}
impl CredentialStore for OsVault {
    fn read(&self, r: &CredentialRef) -> Result<SipCredentials, DomainError> {
        #[cfg(target_os = "macos")]
        {
            SipCredentials::new(
                entry(r)?
                    .get_secret()
                    .map_err(|_| DomainError::Unavailable)?,
            )
        }
        #[cfg(not(target_os = "macos"))]
        {
            let _ = r;
            Err(DomainError::Unavailable)
        }
    }
    fn write(&mut self, r: &CredentialRef, s: &SipCredentials) -> Result<(), DomainError> {
        #[cfg(target_os = "macos")]
        {
            s.expose(|v| {
                entry(r)?
                    .set_secret(v)
                    .map_err(|_| DomainError::Unavailable)
            })
        }
        #[cfg(not(target_os = "macos"))]
        {
            let _ = (r, s);
            Err(DomainError::Unavailable)
        }
    }
    fn remove(&mut self, r: &CredentialRef) -> Result<(), DomainError> {
        #[cfg(target_os = "macos")]
        {
            match entry(r)?.delete_credential() {
                Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
                Err(_) => Err(DomainError::Unavailable),
            }
        }
        #[cfg(not(target_os = "macos"))]
        {
            let _ = r;
            Err(DomainError::Unavailable)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{
        collections::BTreeMap,
        sync::{Arc, Mutex},
    };
    use yeyofone_application::accounts::{Account, AccountService};
    #[derive(Clone, Default)]
    struct Vault(Arc<Mutex<BTreeMap<String, Vec<u8>>>>);
    impl CredentialStore for Vault {
        fn read(&self, r: &CredentialRef) -> Result<SipCredentials, DomainError> {
            SipCredentials::new(
                self.0
                    .lock()
                    .unwrap()
                    .get(r.as_str())
                    .ok_or(DomainError::Unavailable)?
                    .clone(),
            )
        }
        fn write(&mut self, r: &CredentialRef, s: &SipCredentials) -> Result<(), DomainError> {
            s.expose(|s| {
                self.0
                    .lock()
                    .unwrap()
                    .insert(r.as_str().to_owned(), s.to_vec());
            });
            Ok(())
        }
        fn remove(&mut self, r: &CredentialRef) -> Result<(), DomainError> {
            self.0.lock().unwrap().remove(r.as_str());
            Ok(())
        }
    }
    fn account(id: &str) -> Account {
        Account {
            id: id.into(),
            label: "Office".into(),
            username: "1005".into(),
            host: "sysinfos.co.uk".into(),
            port: 5060,
            transport: "udp".into(),
            enabled: true,
            stun_server: None,
            ice: false,
            turn_server: None,
            turn_username: None,
        }
    }
    fn secret(value: &str) -> Option<zeroize::Zeroizing<String>> {
        Some(zeroize::Zeroizing::new(value.to_owned()))
    }
    #[test]
    fn validators_reject_injection_ports_and_inconsistent_turn() {
        for host in [
            "https://host",
            "host/path",
            "a\nb",
            "-bad.example",
            "bad..example",
        ] {
            let mut a = account("one");
            a.host = host.into();
            assert_eq!(a.validate(), Err(AccountError::InvalidInput));
        }
        let mut a = account("../one");
        assert!(a.validate().is_err());
        a.id = "one".into();
        a.port = 0;
        assert!(a.validate().is_err());
        a.port = 5060;
        a.turn_server = Some("turn.example".into());
        assert!(a.validate().is_err());
        a.turn_username = Some("user".into());
        assert!(a.validate().is_ok());
    }
    #[test]
    fn multiple_accounts_edit_without_password_replace_turn_disable_delete() {
        let vault = Vault::default();
        let mut s = AccountService::new(
            SqliteAccounts::open(std::path::Path::new(":memory:")).unwrap(),
            vault.clone(),
        );
        s.save(account("one"), secret("first-secret"), None, "1")
            .unwrap();
        s.save(account("two"), secret("second-secret"), None, "2")
            .unwrap();
        let mut a = account("one");
        a.enabled = false;
        s.save(a.clone(), None, None, "3").unwrap();
        assert_eq!(s.list().unwrap().len(), 2);
        assert!(!s.list().unwrap()[0].enabled);
        assert_eq!(vault.0.lock().unwrap().len(), 2);
        a.turn_server = Some("turn.example".into());
        a.turn_username = Some("turnuser".into());
        s.save(a.clone(), secret("replacement"), secret("turn-secret"), "4")
            .unwrap();
        assert_eq!(vault.0.lock().unwrap().len(), 3);
        assert!(
            !vault
                .0
                .lock()
                .unwrap()
                .values()
                .any(|v| v == b"first-secret")
        );
        a.turn_server = None;
        a.turn_username = None;
        s.save(a, None, None, "5").unwrap();
        assert_eq!(vault.0.lock().unwrap().len(), 2);
        s.delete("one").unwrap();
        assert_eq!(s.list().unwrap().len(), 1);
        assert_eq!(vault.0.lock().unwrap().len(), 1);
        assert_eq!(s.delete("missing"), Err(AccountError::NotFound));
    }
    #[test]
    fn saved_credentials_cannot_be_redirected_without_a_new_password() {
        let vault = Vault::default();
        let mut service = AccountService::new(
            SqliteAccounts::open(std::path::Path::new(":memory:")).unwrap(),
            vault.clone(),
        );
        service
            .save(account("one"), secret("original"), None, "1")
            .unwrap();
        for field in ["host", "username", "port", "transport"] {
            let mut changed = account("one");
            match field {
                "host" => changed.host = "other.invalid".into(),
                "username" => changed.username = "different".into(),
                "port" => changed.port = 5061,
                _ => changed.transport = "tls".into(),
            };
            assert_eq!(
                service.save(changed, None, None, "2"),
                Err(AccountError::InvalidInput)
            );
            assert_eq!(service.list().unwrap(), vec![account("one")]);
        }
        let mut changed = account("one");
        changed.host = "new-provider.example".into();
        service
            .save(changed.clone(), secret("new-password"), None, "3")
            .unwrap();
        assert_eq!(service.list().unwrap(), vec![changed]);
        assert!(!vault.0.lock().unwrap().values().any(|v| v == b"original"));
    }
    #[test]
    fn database_restart_has_no_password_and_preserves_public_account() {
        let path = std::env::temp_dir().join(format!(
            "yeyofone-account-test-{}-{}.sqlite3",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        let vault = Vault::default();
        {
            let mut s = AccountService::new(SqliteAccounts::open(&path).unwrap(), vault.clone());
            s.save(account("one"), secret("UNIQUE-PRIVATE-SECRET"), None, "1")
                .unwrap();
        }
        let bytes = std::fs::read(&path).unwrap();
        assert!(
            !bytes
                .windows(b"UNIQUE-PRIVATE-SECRET".len())
                .any(|b| b == b"UNIQUE-PRIVATE-SECRET")
        );
        {
            let s = AccountService::new(SqliteAccounts::open(&path).unwrap(), vault);
            assert_eq!(s.list().unwrap(), vec![account("one")]);
        }
        std::fs::remove_file(path).unwrap();
    }
    #[test]
    fn failed_database_commit_keeps_existing_account_and_queues_new_secret_cleanup() {
        let repo = SqliteAccounts::open(std::path::Path::new(":memory:")).unwrap();
        let vault = Vault::default();
        let mut s = AccountService::new(repo, vault.clone());
        s.save(account("one"), secret("original"), None, "1")
            .unwrap();
        // Use an actual SQLite trigger to fail replacement rather than mirror implementation.
        // Re-open access through a repository wrapper is unnecessary: test a repository directly.
        let mut repo = SqliteAccounts::open(std::path::Path::new(":memory:")).unwrap();
        repo.save(&StoredAccount {
            account: account("one"),
            sip_ref: "sip-one-old".into(),
            turn_ref: None,
        })
        .unwrap();
        repo.0.execute_batch("CREATE TRIGGER reject_update BEFORE UPDATE ON accounts BEGIN SELECT RAISE(FAIL,'injected'); END;").unwrap();
        let mut s = AccountService::new(repo, vault.clone());
        assert_eq!(
            s.save(account("one"), secret("new"), None, "2"),
            Err(AccountError::StorageUnavailable)
        );
        assert!(!vault.0.lock().unwrap().contains_key("sip-one-2"));
        assert_eq!(s.list().unwrap().len(), 1);
        s.cleanup().unwrap();
    }
    #[test]
    fn crash_staged_secret_cleanup_and_future_schema_fail_closed() {
        let mut repo = SqliteAccounts::open(std::path::Path::new(":memory:")).unwrap();
        repo.stage("sip-orphan-1").unwrap();
        let mut vault = Vault::default();
        vault
            .write(
                &CredentialRef::new("sip-orphan-1").unwrap(),
                &SipCredentials::new(b"private".to_vec()).unwrap(),
            )
            .unwrap();
        let mut s = AccountService::new(repo, vault.clone());
        s.cleanup().unwrap();
        assert!(vault.0.lock().unwrap().is_empty());
        let path = std::env::temp_dir().join(format!(
            "yeyofone-future-schema-{}.sqlite3",
            std::process::id()
        ));
        {
            let repo = SqliteAccounts::open(&path).unwrap();
            repo.0.pragma_update(None, "user_version", 99).unwrap();
        }
        assert!(matches!(
            SqliteAccounts::open(&path),
            Err(AccountError::StorageUnavailable)
        ));
        std::fs::remove_file(path).unwrap();
    }
    #[cfg(target_os = "macos")]
    #[test]
    #[ignore = "Requires access to the actual macOS Keychain"]
    fn actual_keychain_round_trip() {
        let reference = CredentialRef::new(&format!(
            "test-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ))
        .unwrap();
        let mut vault = OsVault;
        let secret = SipCredentials::new(b"ephemeral-test-secret".to_vec()).unwrap();
        vault.write(&reference, &secret).unwrap();
        let result = vault.read(&reference);
        vault.remove(&reference).unwrap();
        assert!(result.unwrap().expose(|v| v == b"ephemeral-test-secret"));
        assert!(vault.read(&reference).is_err());
    }
}

#[cfg(test)]
mod vault_failure_tests {
    use super::*;
    use yeyofone_application::accounts::{Account, AccountService};
    struct Locked;
    impl CredentialStore for Locked {
        fn read(&self, _: &CredentialRef) -> Result<SipCredentials, DomainError> {
            Err(DomainError::Unavailable)
        }
        fn write(&mut self, _: &CredentialRef, _: &SipCredentials) -> Result<(), DomainError> {
            Err(DomainError::Unavailable)
        }
        fn remove(&mut self, _: &CredentialRef) -> Result<(), DomainError> {
            Err(DomainError::Unavailable)
        }
    }
    fn account() -> Account {
        Account {
            id: "one".into(),
            label: "Office".into(),
            username: "1005".into(),
            host: "sysinfos.co.uk".into(),
            port: 5060,
            transport: "udp".into(),
            enabled: true,
            stun_server: None,
            ice: false,
            turn_server: None,
            turn_username: None,
        }
    }
    #[test]
    fn locked_vault_never_saves_an_account_without_a_password() {
        let mut service = AccountService::new(
            SqliteAccounts::open(std::path::Path::new(":memory:")).unwrap(),
            Locked,
        );
        assert_eq!(
            service.save(
                account(),
                Some(zeroize::Zeroizing::new("secret".into())),
                None,
                "1"
            ),
            Err(AccountError::VaultUnavailable)
        );
        assert!(service.list().unwrap().is_empty());
    }
    #[test]
    fn locked_vault_deletion_is_durable_and_secret_cleanup_stays_pending() {
        let mut repo = SqliteAccounts::open(std::path::Path::new(":memory:")).unwrap();
        repo.save(&StoredAccount {
            account: account(),
            sip_ref: "sip-one-old".into(),
            turn_ref: None,
        })
        .unwrap();
        let mut service = AccountService::new(repo, Locked);
        service.delete("one").unwrap();
        assert!(service.list().unwrap().is_empty());
        assert_eq!(service.cleanup(), Err(AccountError::VaultUnavailable));
    }
}
