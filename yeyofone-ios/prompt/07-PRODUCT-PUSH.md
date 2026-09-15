# Phases 24-27: product experience and VoIP push

Use the master rules. Execute only the requested phase.

## Phase 24 — QR provisioning

Use verified scanning APIs and a strict versioned/size-bounded schema. Validate and preview configuration; prefer expiring one-time HTTPS tokens. Define issuer, replay, expiry and unsupported-version behavior; do not invent the backend.

## Phase 25 — Settings

Separate typed global and account settings, Basic/Advanced modes and migrations. Keep secrets solely in Keychain and validate conflicting options.

## Phase 26 — SwiftUI polish

Polish without changing SIP internals. Cover VoiceOver, Dynamic Type, themes, localization/RTL, reduced motion, safe areas, iPhone/iPad/landscape decisions, permission/loading/error/offline states and screenshot privacy where justified.

## Phase 27 — PushKit architecture

Explain that suspended/terminated incoming calls require PBX/push-gateway/APNs support. Implement only the client foundation: legitimate PKPushRegistry token lifecycle, secure backend abstraction, payload validation/deduplication, prompt CallKit reporting and documented backend contract. Never use VoIP pushes as keepalives or claim end-to-end success without infrastructure.

Exit gate: malformed QR/push, token rotation, duplicate pushes, cold launch, CallKit timing, entitlements and privacy behavior are verified on real devices where necessary; backend gaps remain explicit.
