# Phases 26-33: desktop integration

Use the master rules. Execute only the requested phase.

## Phase 26 — QR provisioning

Parse a strict versioned/size-bounded schema, preview before saving, and prefer expiring one-time HTTPS tokens to reusable passwords.

## Phase 27 — Deep links

Define an allowlisted URI scheme/universal-link format, single decoding rule, length limits, user confirmation and safe routing. Treat all links as hostile input.

## Phase 28 — Keyboard shortcuts

Add discoverable, conflict-aware shortcuts that never trigger destructive call actions unexpectedly and work with assistive technology.

## Phase 29 — System tray/menu bar

Implement OS-native lifecycle semantics, truthful status and safe quick actions without accidentally terminating active calls.

## Phase 30 — Notifications

Use per-OS notification APIs/permissions, sanitize caller content, support actionable calls only where reliable, and define privacy settings.

## Phase 31 — Auto-start

Make opt-in, explain behavior, use supported OS mechanisms, and provide clean disable/uninstall handling.

## Phase 32 — Single instance

Coordinate startup, deep links and activation without losing payloads or allowing untrusted local IPC. Test races.

## Phase 33 — Updates

Use signed manifests/artifacts, HTTPS, explicit channels, rollback/failure handling and external key custody. Never ship private signing keys.

Exit gate: malformed input, cold/warm start, OS permission/lifecycle, privacy, signature failure and rollback cases are verified separately per supported OS.
