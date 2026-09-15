# Phases 1-2: foundation and native engine

Use `00-MASTER-RULES.md`. Execute only the phase explicitly requested.

## Phase 1 — Repository and architecture

Inspect before designing. Produce the smallest useful module graph; do not mechanically create modules. Establish domain models for accounts, credentials, server/NAT/security configuration, registration, calls, media, routes, codecs, and quality metrics. Establish narrow VoIP interfaces, error taxonomy, package ownership, dependency rules, test fixtures, and architecture decision records. Do not integrate PJSIP or fake SIP behavior.

Exit gate: the scaffolding compiles; dependency direction is enforceable; state/error models and interfaces have tests; a module diagram and ADR explain rejected alternatives.

## Phase 2 — PJSIP/PJSUA2 adapter

First verify the exact PJSIP version, packaging, license, generated bindings, ABIs, and available APIs. Implement native-library loading, endpoint configuration/start/stop, safe logging, transports required for later use, engine state, dedicated serialized execution, callback translation, exception containment, and explicit native disposal. Keep all PJSUA2/SWIG types internal. Do not register accounts or place calls.

Exit gate: repeated initialize/start/stop and failure transitions are tested; packaging is documented; APK/AAB contains intended ABIs; mappings are unit-tested; no native type crosses the boundary; native/thread ownership is documented.
