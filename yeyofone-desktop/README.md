# YeyoFone Desktop

React/TypeScript interface running in a Tauri 2 desktop shell with validated Rust IPC. Secure SIP accounts, registration and incoming/outgoing audio calls run through the optional native PJSUA2 engine. Video, forwarding and conference screens use preview data.

## Run the desktop app

Requires Bun 1.4.2, the Rust toolchain pinned in `rust-toolchain.toml`, and the [Tauri platform prerequisites](https://v2.tauri.app/start/prerequisites/).

From the repository root:

```sh
cd frontend
bun install --frozen-lockfile
cd ..
bun run tauri dev
```

The native window opens automatically. Its Vite server uses port 5175. The status strip confirms the Rust bridge connection.

## Browser preview

```sh
bun run --cwd frontend dev
```

Open http://127.0.0.1:5173. Native IPC is unavailable in this mode.

## Verify and package

Build the native artifacts first for workspace Rust tests; see [native build prerequisites](docs/pjsip-build.md).

```sh
bun run --cwd frontend build
cargo fmt --all --check
cargo test --workspace --locked
cargo clippy --workspace --all-targets --locked -- -D warnings
bun run tauri build
```

The current bundle target is a macOS `.app`. Windows and Linux packaging have not been verified. See `docs/PHASE-02-REPORT.md` and `docs/IPC.md`.

## Native VoIP build foundation

PJSIP/OpenSSL source pins, native build commands and platform limits are documented in [docs/pjsip-build.md](docs/pjsip-build.md). Enable the native adapter with `bun run tauri dev --features native-voip` after building native artifacts. See [docs/native-adapter.md](docs/native-adapter.md) for prerequisites, static linking and lifecycle limits.

SIP accounts can now be managed under Settings in the desktop app. Public details persist in SQLite; macOS passwords use Keychain. Account 1005 has been imported locally when using the existing authorized configuration. In the native build, enabled accounts register automatically. See [Phase 5 report](docs/PHASE-05-REPORT.md). Windows/Linux vaults remain unavailable.

Enabled SIP accounts now register automatically in the native desktop build. Settings includes live status and register/refresh/unregister controls. Account 1005/sysinfos.co.uk was verified against the real provider with SIP 200. Use `bun run tauri dev --features native-voip`. Outgoing audio calling is implemented in Phase 8; video and incoming calls remain later phases. This native profile supports seven configured accounts at once. See [Phase 6 report](docs/PHASE-06-REPORT.md).

Outgoing calls: open the phone dialer, select a registered SIP account, validate a destination, then select Call. Mute, cancel/hangup and connected duration use native state. An authorized 1005 → 1001 live call connected with SIP 200 and active audio routing. Audible two-way quality is unverified. See [Phase 8 report](docs/PHASE-08-REPORT.md) for supported destinations, capacity and media limitations.

## Recording cloud backup

Cloudflare R2 backup, restore and recording deletion are configured under Call History. See [R2 setup](docs/R2-SETUP.md) for the private bucket, Worker deployment and Keychain-backed connection token.

Talking calls include icon controls for blind transfer, consult transfer, hold/resume, mute and the DTMF keypad. Consult transfer holds the original caller while you speak to the destination; complete the transfer or cancel and resume. Transfers require provider support for SIP REFER/Replaces. Local SIP tests cover success, failure, cancellation and retry; provider transfer interoperability remains unverified.
