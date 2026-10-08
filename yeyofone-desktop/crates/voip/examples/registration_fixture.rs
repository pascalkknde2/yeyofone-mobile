use std::{
    thread,
    time::{Duration, Instant},
};
use yeyofone_application::accounts::Account;
use yeyofone_domain::SipCredentials;
use yeyofone_voip::PjsipEngine;
fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() != 4 {
        eprintln!("host port transport required");
        std::process::exit(2);
    }
    let account = Account {
        id: "fixture".into(),
        label: "Fixture".into(),
        username: "test".into(),
        host: args[1].clone(),
        port: args[2].parse().unwrap(),
        transport: args[3].clone(),
        enabled: true,
        stun_server: None,
        ice: false,
        turn_server: None,
        turn_username: None,
    };
    let engine = PjsipEngine::new().unwrap();
    engine.start_engine().unwrap();
    engine
        .configure_account(
            account,
            SipCredentials::new(b"synthetic-fixture-password".to_vec()).unwrap(),
        )
        .unwrap();
    let until = Instant::now() + Duration::from_secs(38);
    loop {
        let statuses = engine.registrations().unwrap();
        let s = &statuses[0];
        if s.failure.is_some() {
            println!("{}", serde_json::to_string(s).unwrap());
            break;
        }
        if Instant::now() >= until {
            println!("fixture timed out");
            break;
        }
        thread::sleep(Duration::from_millis(50));
    }
    engine.shutdown().unwrap();
}
