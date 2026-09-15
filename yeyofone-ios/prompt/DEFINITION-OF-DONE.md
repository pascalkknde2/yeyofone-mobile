# iOS phase definition of done

- Requested scope and error states are complete without unrelated redesign.
- Domain state is authoritative; bridge/PJSUA2 types do not escape.
- Swift concurrency isolation is explicit and warning-free at the project's configured strictness.
- Tests cover deterministic validation, mapping, policy and state transitions.
- Relevant build/tests/static checks pass.
- Persistence changes include tested migrations and compatibility.
- Keychain, logs, UI state, diagnostics and fixtures contain no exposed secrets.
- CallKit/audio/lifecycle work has real-device evidence or is explicitly unverified.
- SwiftUI work covers VoiceOver, Dynamic Type, localization-ready strings and error/loading/permission states.
- Entitlements, Info.plist usage descriptions and privacy manifest are kept consistent.

Return `PASS`, `PARTIAL`, or `BLOCKED`; changed files; decisions; exact commands/results; simulator/device/SIP scenarios; concurrency/native/security review; limitations; and next-phase prerequisites. Then stop.
