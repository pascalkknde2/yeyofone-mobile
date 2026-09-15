# Desktop phase definition of done

A phase is done only when:

- requested behavior and failure states are implemented without unrelated rewrites;
- typed IPC inputs are validated and outputs/events are version-compatible;
- domain state remains authoritative and PJSUA2 never escapes the Rust adapter;
- deterministic logic has tests and affected builds/lints pass;
- native ownership, callback lifetime, shutdown and thread safety are reviewed;
- persistence changes include migrations and compatibility handling;
- secrets are absent from frontend state, logs, fixtures and exports;
- accessibility, keyboard behavior and error/loading/empty states are covered;
- Windows, macOS and Linux results are recorded separately—unrun is not passed;
- documentation and project context reflect decisions.

Return status (`PASS`, `PARTIAL`, or `BLOCKED`), changed files, decisions, exact commands/results, manual OS/SIP scenarios, security/native review, limitations, and next-phase prerequisites. Then stop.
