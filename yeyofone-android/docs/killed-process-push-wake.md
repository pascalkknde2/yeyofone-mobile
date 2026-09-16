# Killed-Process Incoming Calls: Firebase and PBX Setup

The Android push-wake skeleton is present in the app, but a fully killed-process incoming call requires Firebase configuration and PBX integration.

## 1. Create the Firebase Android app

In the [Firebase Console](https://console.firebase.google.com/):

1. Create or select a Firebase project.
2. Add an Android app with package name `com.yeyofone.app`.
3. Download `google-services.json`.
4. Place it at `yeyofone-android/app/google-services.json`.

Do not commit Firebase credentials or private service-account keys.

Firebase requires the Google Services Gradle plugin for the JSON configuration. Follow the [official Android setup guide](https://firebase.google.com/docs/android/setup).

## 2. Enable the Google Services Gradle plugin

In `yeyofone-android/build.gradle.kts`, add the current plugin version from the Firebase setup instructions:

```kotlin
plugins {
    id("com.google.gms.google-services") version "<current-version>" apply false
}
```

In `yeyofone-android/app/build.gradle.kts`, apply it:

```kotlin
plugins {
    id("com.google.gms.google-services")
}
```

Verify the build:

```shell
cd yeyofone-android
./gradlew test :app:assembleDebug
```

## 3. Verify FCM token delivery

`YeyoFoneFirebaseMessagingService` already handles token refreshes. Check the emulator log:

```shell
ADB=/Users/pascalkanyamakankonde/Library/Android/sdk/platform-tools/adb
$ADB -s emulator-5554 logcat -d | grep YeyoFonePush
```

The app must send every new token to the PBX because FCM tokens can rotate. See [FCM Android client setup](https://firebase.google.com/docs/cloud-messaging/android/get-started).

## 4. Add PBX token registration

The PBX needs an authenticated endpoint similar to:

```http
POST https://sysinfos.co.uk/api/mobile-push-tokens
Authorization: Bearer <user-session-token>
Content-Type: application/json
```

```json
{
  "platform": "android",
  "packageName": "com.yeyofone.app",
  "extension": "1005",
  "fcmToken": "<device-token>",
  "deviceId": "<stable-installation-id>"
}
```

The Android client should call this endpoint from `onNewToken()` and after account registration succeeds. Firebase service-account credentials must remain on the server.

## 5. Add SIP-to-push behavior in the PBX

When the PBX receives an INVITE for a mobile extension:

1. Check whether the extension has an active SIP registration.
2. If it is unreachable, send an FCM data message to the stored device token.
3. Wait briefly for the app to restart and re-register SIP.
4. Retry or fork the INVITE to the new SIP contact.
5. Continue normal ringing.

The FCM message wakes the process; it does not itself create a SIP call object. SIP registration and the INVITE must establish the call.

Use FCM HTTP v1:

```http
POST https://fcm.googleapis.com/v1/projects/<firebase-project-id>/messages:send
```

Example high-priority data payload:

```json
{
  "message": {
    "token": "<fcm-token>",
    "android": { "priority": "high" },
    "data": {
      "event": "sip_invite_wake",
      "extension": "1005"
    }
  }
}
```

See [FCM HTTP v1 sending](https://firebase.google.com/docs/cloud-messaging/send/v1-api).

## 6. End-to-end verification

1. Open YeyoFone and confirm SIP registration.
2. Confirm the PBX stored the FCM token.
3. Let Android reclaim the app process, or test an explicit force-stop separately.
4. Call extension `1005` from `1004`.
5. Confirm the PBX sends the FCM wake message.
6. Confirm `IncomingCallService` starts and SIP re-registers.
7. Confirm the retried INVITE rings.
8. Answer from the notification or lock screen.
9. Confirm exactly one call screen and two-way audio.

Android generally does not deliver FCM to an app explicitly force-stopped by the user until the app is opened again. Therefore, test OS-reclaimed and force-stopped processes as separate cases.
