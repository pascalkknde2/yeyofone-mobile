# ADR 0001: Start with three modules

- Status: Accepted
- Date: 2026-09-15

## Decision

Start with `app`, `core:model`, and `core:voip-api`. Add feature, persistence, security, platform and PJSIP adapter modules only when their implementation phases begin.

## Rationale

The repository had no application code. Three modules make dependency direction executable while avoiding empty modules and speculative abstractions. Pure Kotlin core modules provide fast tests and keep Android/PJSIP dependencies outside the domain.

## Rejected alternatives

- One application module: too easy for native and Android types to leak into domain code.
- The full conceptual module list immediately: produces empty scaffolding with no validated ownership.
- A shared cross-platform binary core now: Android, iOS and Desktop lifecycle/FFI strategies have not yet been validated.
