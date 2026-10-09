# Phase 7 — destination validation and dialer

Status: PARTIAL — implementation and automated checks pass; interactive macOS keyboard/visual verification remains unrun.

The main phone button now opens a real account-aware validation dialog rather than the simulated outgoing call preview. Select a saved account, see live registration status, type or paste a destination, and press Enter to validate through Rust. Keypad insertion preserves selection/caret. Native dialog supplies focus containment, Escape dismissal and focus restoration. All text is at least 16px, with English/French/Spanish/German labels. Incoming and video previews remain previews. No INVITE or media operation is exposed.

Changed files: domain destination module and exports; IPC destination contracts; Tauri command, manifest and local-window permission; frontend Dialer, CSS, destinationWire helper/tests, main routing and translations; documentation. Existing Phase 3–6 changes are preserved.

Parser policy: at most 512 bytes; ASCII numeric destinations with spaces, parentheses, periods and hyphens normalized, leading zeroes preserved; 1–6 digits classified as extensions, 7–15 digits or a leading + as phone numbers. No country-code inference. Explicit lowercase sip:/sips: supports bounded user, DNS/IPv4-style host and optional port 1–65535. Host lowercased, port normalized. Passwords, display names, headers, URI parameters, IPv6 literals, percent escapes, control characters and unsupported characters are rejected. This is a documented subset, not a complete SIP URI parser. Validation is independent of registration; selected-account readiness is live presentation, not call authorization. Phase 8 must revalidate account/destination and transport/authentication policy at call time.

Checks:
- cargo test --workspace --all-features --locked: 43 passed; 1 OS Keychain test ignored.
- cargo clippy --workspace --all-targets --all-features --locked -- -D warnings: passed.
- cargo check -p yeyofone-desktop --no-default-features --locked: passed.
- bun test frontend/tests: 6 passed.
- bun run --cwd frontend build: typecheck and production build passed.
- cargo fmt --all --check and git diff --check: passed.

Security/native review: bounded versioned IPC, strict unknown-field rejection and correlation checks; errors do not echo rejected destinations. Parser accesses neither credentials nor native handles. New permission restricted to the local main window. No clipboard plugin, filesystem grant, logging of destinations, credential reads or native calling API added. Native owner-thread code unchanged. Unit checks cover injection, bounds, normalization, stale wire replies and caret insertion. Browser paste uses normal input semantics without stripping invalid characters.

Manual limitations: no real calls made, no PBX changes, no interactive UI automation, no Windows/Linux execution. Full SIP URI variants and feature codes are unsupported. Next phase: real outgoing sessions and audio, with transport/authentication checks, cancellation, early media and authoritative failure/duration handling.
