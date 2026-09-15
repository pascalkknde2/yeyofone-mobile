# Android architecture

## Phase 1 module graph

```text
app
 ├── core:voip-api
 │    └── core:model
 └── core:voip-pjsip (composition root only)
      ├── core:voip-api
      └── core:model

core:voip-pjsip
 ├── core:voip-api
 └── core:model
```

The application composition root also depends on `core:account-data`. That module depends only on `core:model` and Android persistence APIs; it has no dependency on PJSIP or registration contracts.

`core:registration` coordinates account data, short-lived secret access, connectivity, and the platform-neutral `SipRegistrationGateway`. The gateway implementation remains in `core:voip-pjsip`, preserving the rule that generated/native types never escape their adapter.

`app` is intentionally empty of product UI. `core:model` is a pure Kotlin domain module. `core:voip-api` is a pure Kotlin boundary containing capabilities and observable state. `core:voip-pjsip` owns the Android/native adapter and is not exposed to features.

## Dependency rules

- Domain models import neither Android nor PJSIP.
- VoIP contracts may depend on domain models and coroutines only.
- Features will depend on contracts, never adapter implementations.
- The composition root in `app` will select implementations.
- Credentials are transient write inputs, not observable account state.
- Each manager exposes its authoritative state rather than duplicating UI state machines.

## Package ownership

- `com.yeyofone.core.model`: stable platform-neutral values and states.
- `com.yeyofone.core.voip`: engine capability boundary.
- `com.yeyofone.core.voip.pjsip`: internal PJSUA2 ownership, lifecycle, and type translation.
- `com.yeyofone.core.account`: public account repository plus internal Room and Keystore implementations.
- `com.yeyofone.app`: future Android composition root and navigation.

## Phase 2 native ownership

`PjsipEngine` serializes every endpoint operation onto one owned executor. `Pjsua2EndpointBackend` is the only code that imports generated PJSUA2 types: it loads the library once, registers the executor thread, configures the endpoint with SIP-message logging disabled, creates transports, and explicitly destroys native objects. No generated type crosses the public engine boundary.

## Phase 3 account state

Room owns observable public account state. The Keystore-backed store owns secrets and never exposes them through `AccountRepository`. Compose screens consume repository flows and submit write-only drafts; enablement has no registration side effect until Phase 4.

## Phase 1 risks

- Call and registration state transition legality is not yet centralized; it should be added with the first use case that drives transitions.
- `CharArray` permits explicit clearing but the JVM may retain copies created before the boundary. Phase 3 must use a dedicated secure credential store and minimize lifetime.
- The app currently targets the locally installed API 34. Raising target/compile SDK requires installing and validating the newer SDK first.
