# Account storage and security

## Boundary

`core:account-data` is the authoritative source for account configuration. Room database `yeyofone-accounts.db` stores only public SIP, server, NAT, media-security, and enablement settings. Passwords are never columns, model properties, observable flows, or saved UI state.

Passwords are accepted only by `AccountDraft`, copied into AES-GCM input bytes, encrypted with a non-exportable 256-bit Android Keystore key, and stored as IV plus ciphertext in a separate private `SharedPreferences` file. The draft `CharArray` and temporary byte array are cleared after use. The password text field uses non-saveable Compose memory and is cleared before persistence begins, so rotation or process recreation intentionally discards unsaved secrets.

AndroidX Security Crypto is deliberately not used because its stable API is deprecated in favor of Android Keystore. The repository never logs account commands or secret-store values.

## Validation and policy

- Display name and SIP/auth usernames are required and length bounded.
- Domains and STUN/TURN hosts are IDN-normalized and strictly validated.
- Registrar and proxy values must be `sip:` or `sips:` URIs.
- Ports are limited to 1–65535; registration expiry is 60–86400 seconds.
- A new account requires a password; editing leaves the existing password unchanged unless a replacement is entered.
- Duplicate auth-username/domain/transport identities are rejected, including disabled accounts.
- Deletion removes encrypted credentials before public metadata. A failed secret deletion prevents public deletion so an orphaned credential is not silently retained.
- Enabling an account only changes stored policy. Phase 3 does not register it.

## Schema and migration

Room schema version 2 is exported under `core/account-data/schemas`. Migration 1→2 adds TURN username, SRTP policy, registration expiry, voicemail, and caller-ID columns with backward-compatible defaults. The instrumentation migration test constructs the version-1 table and checks every new column. Downgrade is intentionally unsupported; rollback uses an older application build only after clearing its local database.

## Verification limits

JVM tests cover validation, duplicate handling, command-secret clearing, repository recreation, deletion, and absence of password fields from the Room entity. Instrumentation tests cover the real SQLite migration and Android Keystore ciphertext persistence, but require a connected API 26+ Android device or emulator. No device was attached during Phase 3, so running `./gradlew :core:account-data:connectedDebugAndroidTest` is an explicit follow-up for the Android QA/build owner.
