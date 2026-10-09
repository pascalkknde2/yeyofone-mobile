//! Authorized live registration probe: native vault only, public numeric diagnostics only.
use std::{
    thread,
    time::{Duration, Instant},
};
use yeyofone_application::accounts::AccountService;
use yeyofone_platform::accounts::{OsVault, SqliteAccounts};
use yeyofone_voip::PjsipEngine;
fn wait(engine: &PjsipEngine, id: &str, state: &str) -> Result<(), String> {
    let until = Instant::now() + Duration::from_secs(38);
    let mut previous = String::new();
    while Instant::now() < until {
        let statuses = engine.registrations().map_err(|_| "engine unavailable")?;
        if let Some(status) = statuses.into_iter().find(|s| s.account_id == id) {
            let key = format!(
                "{}-{:?}-{:?}",
                status.state, status.failure, status.sip_code
            );
            if key != previous {
                println!(
                    "{}",
                    serde_json::to_string(&status).map_err(|_| "serialization unavailable")?
                );
                previous = key;
            }
            if status.state == state {
                return Ok(());
            }
            if status.state == "failed" {
                return Err("registration failed; numeric diagnostics above".into());
            }
        }
        thread::sleep(Duration::from_millis(100));
    }
    Err("registration verification timed out".into())
}
fn run() -> Result<(), String> {
    let mut args = std::env::args_os().skip(1);
    let path = args.next().ok_or("database path required")?;
    let id = args
        .next()
        .ok_or("account id required")?
        .into_string()
        .map_err(|_| "invalid id")?;
    let service = AccountService::new(
        SqliteAccounts::open(std::path::Path::new(&path)).map_err(|_| "storage unavailable")?,
        OsVault,
    );
    let (account, credentials) = service
        .registration_account(&id)
        .map_err(|_| "account or vault unavailable")?;
    let engine = PjsipEngine::new().map_err(|_| "engine unavailable")?;
    engine.start_engine().map_err(|_| "engine start failed")?;
    let result = (|| {
        engine
            .configure_account(account, credentials)
            .map_err(|_| "account configuration failed")?;
        wait(&engine, &id, "registered")?;
        println!("REGISTER verification passed");
        engine
            .register_account(&id)
            .map_err(|_| "refresh command failed")?;
        wait(&engine, &id, "registered")?;
        println!("REFRESH verification passed");
        engine
            .unregister_account(&id)
            .map_err(|_| "unregister command failed")?;
        wait(&engine, &id, "unregistered")?;
        println!("UNREGISTER verification passed");
        engine
            .register_account(&id)
            .map_err(|_| "reregister command failed")?;
        wait(&engine, &id, "registered")?;
        println!("REREGISTER verification passed");
        Ok(())
    })();
    engine.shutdown().map_err(|_| "engine shutdown failed")?;
    result
}
fn main() {
    if let Err(error) = run() {
        eprintln!("{error}");
        std::process::exit(1);
    }
}
