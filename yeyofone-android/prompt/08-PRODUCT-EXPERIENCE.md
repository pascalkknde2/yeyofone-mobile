# Phases 24-26: provisioning and product experience

Use `00-MASTER-RULES.md`. Execute only the requested phase.

## Phase 24 — QR provisioning

Define a versioned, size-bounded schema and strict parser for account configuration. Validate version, encoding, fields, URIs, hosts, ports, transports, and security policy; show a confirmation preview. Prefer short-lived one-time provisioning tokens over reusable passwords. Define replay, expiry, issuer trust, offline, and unsupported-version behavior before adding server exchange.

## Phase 25 — Settings

Separate application-global from account-specific settings. Organize accounts, calling, audio, network, security, codecs, notifications, appearance, advanced SIP, diagnostics, and about into basic/advanced modes. Persist with typed DataStore keys and migrations; explain dangerous options and validate cross-setting conflicts.

## Phase 26 — UI/UX refinement

Polish without changing SIP behavior. Provide clear navigation and call controls, large touch targets, TalkBack, scalable text, localization/RTL readiness, light/dark modes, edge-to-edge, landscape, small/large screens, reduced motion, loading/empty/error/offline states, and screenshot privacy where justified.

Exit gate: malformed/adversarial QR tests, DataStore migration tests, accessibility checks, font-scale and form-factor screenshots, and critical user journeys pass without dead controls or embedded secrets.
