# Android refactor review

This review compares the current refactored UI with the behavior documented in [ANDROID-SCREENS-REPORT.md](ANDROID-SCREENS-REPORT.md). It was performed without changing implementation code.

## Confirmed regressions or missing functionality

| Area | Missing or changed behavior | Current implementation |
| --- | --- | --- |
| Account details | The **Call** button is gone. Users cannot start a call directly from account details. | `AccountDetail` exposes edit, registration, preferences, and sign-in/sign-out actions, but no call action. |
| Account details | **Delete account** and its confirmation dialog are gone. **Sign out** only disables the account; it does not remove the Room record or stored credentials. | `ActionButtons` contains re-register/sign-in and sign-out only. |
| Outgoing calls | The refactored call screen exposes Speaker, Keypad, Mute, Hold, Transfer, and Hang up, but does not expose the Earpiece, wired-headset, or Bluetooth route chips documented above. | `OutgoingCallScreen` receives only a speaker boolean; route selection is not rendered there. |
| Outgoing calls | **Consult/attended transfer** is missing from the active outgoing-call UI, including consultation destination, Start consultation, Complete transfer, Return to caller, and consultation status. | The older `InCallControls` still contains these controls, but outgoing calls now use `OutgoingCallScreen`. |
| Outgoing calls | Blind-transfer **Pending**, **Succeeded**, and **Failed** status is not shown on the new outgoing screen. | Transfer is submitted, then the transfer panel closes without rendering `TransferState`. |
| Call history | Per-row **Delete** is missing. The current screen supports callback and Clear all only. | `CallHistoryItem` has voicemail and callback actions, but no delete callback. |
| Dial pad | Add-contact and More-options buttons are visible but currently do nothing. | `DialPadScreen` uses their default empty callbacks from `MainActivity`. |
| Incoming calls | Connected incoming calls use the older in-call control implementation, while outgoing calls use the refactored implementation. This creates inconsistent controls between incoming and outgoing sessions. | The incoming wrapper renders `OutgoingCallScreen` for the active path, but another terminal/legacy path still renders `InCallControls`. |

## Intentional behavior change

The transfer destination text field was replaced by an inline keypad in the current local change. This matches the requested transfer flow. The keypad currently has no visible backspace or clear action.

## New refactor areas that are still placeholders

- Settings entries other than Accounts display “Feature coming soon” rather than opening settings screens.
- The voicemail action in call history is rendered but wired to an empty callback.
- The Contacts navigation destination currently opens the SIP accounts screen rather than a separate contacts feature.
