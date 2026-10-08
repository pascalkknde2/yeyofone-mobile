//! Explicitly authorized bounded live call. No secrets or raw SIP logged.
use std::{
    thread,
    time::{Duration, Instant},
};
use yeyofone_application::accounts::AccountService;
use yeyofone_platform::accounts::{OsVault, SqliteAccounts};
use yeyofone_voip::PjsipEngine;
fn run() -> Result<(), String> {
    let args: Vec<_> = std::env::args().collect();
    if args.len() != 4 {
        return Err("database, account id and authorized destination required".into());
    }
    let service = AccountService::new(
        SqliteAccounts::open(std::path::Path::new(&args[1])).map_err(|_| "storage unavailable")?,
        OsVault,
    );
    let (account, credentials) = service
        .registration_account(&args[2])
        .map_err(|_| "account or vault unavailable")?;
    let engine = PjsipEngine::new().map_err(|_| "engine unavailable")?;
    engine.start_engine().map_err(|_| "engine start failed")?;
    let result = (|| {
        engine
            .configure_account(account, credentials)
            .map_err(|_| "configuration failed")?;
        let until = Instant::now() + Duration::from_secs(40);
        loop {
            let rows = engine
                .registrations()
                .map_err(|_| "registration unavailable")?;
            if rows.iter().any(|r| r.state == "registered") {
                println!("REGISTER 200 verified");
                break;
            }
            if rows.iter().any(|r| r.state == "failed") || Instant::now() >= until {
                return Err("registration failed".into());
            }
            thread::sleep(Duration::from_millis(100));
        }
        let id = "authorized-live-call";
        engine
            .dial(id, &args[2], &args[3])
            .map_err(|_| "dial command failed")?;
        let deadline = Instant::now() + Duration::from_secs(25);
        let mut previous = String::new();
        let mut muted = false;
        let mut ended = false;
        while Instant::now() < deadline {
            let rows = engine.calls().map_err(|_| "call status unavailable")?;
            let row = rows.iter().find(|r| r.id == id).ok_or("call missing")?;
            let key = format!(
                "{}-{:?}-{}-{}",
                row.state, row.reason, row.audio_active, row.muted
            );
            if key != previous {
                println!(
                    "{}",
                    serde_json::to_string(row).map_err(|_| "serialization failed")?
                );
                previous = key;
            }
            if row.state == "ended" {
                ended = true;
                break;
            }
            if row.state == "connected" && row.duration_seconds >= 2 && !muted {
                engine.mute(id, true).map_err(|_| "mute failed")?;
                engine.mute(id, false).map_err(|_| "unmute failed")?;
                muted = true;
                println!("MUTE / UNMUTE commands verified");
            }
            if row.state == "connected" && row.duration_seconds >= 5 {
                break;
            }
            thread::sleep(Duration::from_millis(100));
        }
        if !ended {
            engine.hangup(id).map_err(|_| "hangup failed")?;
            let until = Instant::now() + Duration::from_secs(6);
            loop {
                let rows = engine.calls().map_err(|_| "call status unavailable")?;
                let row = rows.iter().find(|r| r.id == id).ok_or("call missing")?;
                if row.state == "ended" {
                    println!(
                        "{}",
                        serde_json::to_string(row).map_err(|_| "serialization failed")?
                    );
                    break;
                }
                if Instant::now() >= until {
                    return Err("termination verification timed out".into());
                }
                thread::sleep(Duration::from_millis(100));
            }
        }
        Ok(())
    })();
    engine.shutdown().map_err(|_| "shutdown failed")?;
    result
}
fn main() {
    if let Err(error) = run() {
        eprintln!("{error}");
        std::process::exit(1);
    }
}
