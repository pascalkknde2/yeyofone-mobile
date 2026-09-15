# Phase definition of done

A phase is complete only when its exit gate passes or each unavailable check is explicitly recorded with the exact reason and follow-up owner.

## Required evidence

- Requested scope is implemented; unrelated refactors are excluded.
- Architecture boundaries and authoritative state ownership remain intact.
- Unit tests cover new deterministic state, validation, mapping, and policy logic.
- Relevant build, test, lint, and static-analysis commands pass.
- Platform/native behavior is tested on an appropriate device or clearly marked unverified.
- Errors are surfaced meaningfully; exceptions are not silently swallowed.
- No secrets or sensitive SIP headers appear in code, fixtures, logs, screenshots, or exports.
- New persisted data has a migration and rollback/compatibility story.
- User-facing work includes accessibility labels, disabled/loading/error states, and localization-ready strings.
- Documentation and `PROJECT-CONTEXT.md` reflect decisions made.

## Mandatory phase report

Return:

1. Outcome and phase status: `PASS`, `PARTIAL`, or `BLOCKED`.
2. Files/modules changed and why.
3. Architecture/state-machine decisions.
4. Commands run with pass/fail results.
5. Manual/device/SIP-server scenarios run.
6. Security, privacy, lifecycle, and native-memory review.
7. Known limitations and unverified behavior.
8. Exact prerequisites for the next phase.
9. Diff summary, then stop.
