use std::io::{Read, Write};
use yeyofone_application::accounts::{Account, AccountRepository, AccountService};
use yeyofone_domain::{CredentialRef, CredentialStore};
use yeyofone_platform::accounts::{OsVault, SqliteAccounts};
fn run() -> Result<(), &'static str> {
    let path = std::env::args_os().nth(1).ok_or("Missing database path")?;
    let mut input = zeroize::Zeroizing::new(String::new());
    std::io::stdin()
        .take(16001)
        .read_to_string(&mut input)
        .map_err(|_| "Input unavailable")?;
    if input.len() > 16000 {
        return Err("Oversized input");
    }
    let mut value: serde_json::Value = serde_json::from_str(&input).map_err(|_| "Invalid input")?;
    let password = zeroize::Zeroizing::new(
        value
            .get("password")
            .and_then(serde_json::Value::as_str)
            .ok_or("Missing password")?
            .to_owned(),
    );
    value
        .as_object_mut()
        .ok_or("Invalid input")?
        .remove("password");
    let account: Account = serde_json::from_value(value).map_err(|_| "Invalid account")?;
    let expected_id = account.id.clone();
    let repo =
        SqliteAccounts::open(std::path::Path::new(&path)).map_err(|_| "Storage unavailable")?;
    let mut service = AccountService::new(repo, OsVault);
    let nonce = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map_err(|_| "Clock unavailable")?
        .as_nanos()
        .to_string();
    service
        .save(
            account,
            Some(zeroize::Zeroizing::new(password.to_string())),
            None,
            &nonce,
        )
        .map_err(|_| "Secure import failed")?;
    let verify_repo = SqliteAccounts::open(std::path::Path::new(&path))
        .map_err(|_| "Verification storage unavailable")?;
    let stored = verify_repo
        .list()
        .map_err(|_| "Verification unavailable")?
        .into_iter()
        .find(|s| s.account.id == expected_id)
        .ok_or("Imported account absent")?;
    let secret = OsVault
        .read(&CredentialRef::new(&stored.sip_ref).map_err(|_| "Invalid reference")?)
        .map_err(|_| "Vault verification unavailable")?;
    if !secret.expose(|s| s == password.as_bytes()) {
        return Err("Vault verification mismatch");
    }
    std::io::stdout()
        .write_all(b"Account securely imported; registration pending.\n")
        .map_err(|_| "Output unavailable")?;
    Ok(())
}
fn main() {
    if let Err(error) = run() {
        eprintln!("{error}");
        std::process::exit(1);
    }
}
