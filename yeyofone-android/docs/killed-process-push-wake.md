# Push wake-up setup

The Android client now obtains and caches its Firebase Cloud Messaging (FCM) token, registers it with the push relay for each enabled SIP account, retries temporary relay failures, and starts the incoming-call service when a data push arrives. The account detail screen lets the user import or remove the device credential issued by the service operator.

Push wake-up works end to end only after the relay and PBX are configured. This repository contains the Android client; the relay and PBX push sender are separate services.

## Configure the Android build

The Firebase Android app configuration is `app/google-services.json`; keep Firebase service-account keys out of the mobile app and repository. Configure the relay base URL when building:

```shell
./gradlew :app:assembleRelease -PpushRelayUrl=https://<your-push-relay-host>
```

The URL must use HTTPS. Without it, the account screen reports that push wake-up is unavailable and the app does not send credentials or tokens to a relay. For local relay development, the debug manifest permits cleartext traffic, but release builds do not.

## Configure the relay and PBX

Deploy the compatible `yeyofone-push-relay` service and configure its FCM sender credentials on the server. The app authenticates relay requests with an operator-issued, per-device credential, entered in the account's **Push wake-up** section. The relay associates that device credential with the app's FCM token.

When an incoming SIP INVITE has no reachable registration, the PBX must ask the relay to send an FCM **high-priority data message** to the registered device. The data event should be `sip_invite_wake`. FCM wakes the app process; the client restarts SIP registration and the PBX must retry or fork the INVITE to the newly registered contact. Push delivery does not itself create a SIP call.

Keep FCM service-account credentials on the relay/PBX server. Never place those credentials or a shared relay secret in the Android app.

## Verify end to end

1. Build with the deployed HTTPS relay URL and install on a physical Android device with Google Play services.
2. Add and enable a SIP account, then import its device credential in **Push wake-up**.
3. Confirm the status changes to **Active** and that the relay has the current FCM token.
4. Let Android reclaim the app process; then call the extension from another SIP endpoint.
5. Confirm the PBX sends the wake message, the app re-registers, the retried INVITE rings, and answering works from the notification or call screen.
6. Repeat with temporary relay outages and token rotation. Confirm the status and retry behavior recover.

Android generally does not deliver FCM to an app explicitly force-stopped by the user until the user opens it again. Test force-stop separately from normal process reclamation. Emulators may not accurately model background delivery or call audio; use a physical device for release acceptance.
