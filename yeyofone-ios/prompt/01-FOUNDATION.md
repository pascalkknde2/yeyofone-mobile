# Phases 1-2: foundation

Use the master rules. Execute only the requested phase.

## Phase 1 — Architecture

Inspect first. Establish the smallest useful App/Core/Features/Platform layout, platform-neutral models/protocols, typed errors, state machines, dependency rules, test fixtures and ADR. Choose observation and actor ownership deliberately. Do not integrate PJSIP or fake calls.

## Phase 2 — PJSIP/PJSUA2 bridge

Verify source/version/license/build/bindings and architecture support. Prefer Swift -> narrow Objective-C++ bridge -> PJSUA2. Implement endpoint load/configuration/start/stop, safe logging, transport foundation, serialized native execution, callback translation, C++ exception containment and explicit ownership/destruction. Do not register or call.

Exit gate: clean build/tests, repeatable native packaging, lifecycle/failure tests, concurrency review and proof that no C++ object crosses the bridge.
