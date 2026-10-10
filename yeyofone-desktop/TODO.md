# YeyoFone Desktop — TODO

Improvements for the desktop app (`yeyofone-desktop/`). When a task is finished, tick its box and add the date and PR, e.g. `- [x] … — done 2026-10-10 (#36)`.

## In progress

- [ ] **Share screen in the Team room** (in PR #36). The share menu offers Share screen and Share agenda; the captured screen shows live on the stage, only on this device. Still to do: try it with a real click in the app.

## Quick wins

- [x] **1. Remove leftovers from the old Team room.** About 20 translations in `frontend/src/translations.ts` and 4 patterns in `frontend/src/i18n.tsx` ("…joined the preview room", "…has the floor in this preview", "Room focus: …", "Focus …") are no longer used. — done 2026-10-09 (#36): removed 22 translations and 5 patterns (also "Agenda: …").
- [x] **2. Deal with the preview screens that can no longer be opened.** Since PR #33 these have no way in: incoming-call and video-call previews (`CallWindows.tsx`), Call forwarding, Ring groups and Audio conference (`CallingFeatures.tsx`), plus their state in `main.tsx`. Delete the code or give the screens a real place in the app. — done 2026-10-09 (#36): Call forwarding, Ring groups and Audio conference now open from a "Calling features" section in Settings; the incoming/video call demo windows are deleted (`CallIcon` moved to its own file, 30 demo-only translations removed).
- [x] **3. Set the Team room's opening focus on purpose.** The first button ("Notes") gets focus when the room opens, so keyboard users may see an outline there. Focus the Meeting tab or the Close button instead. — done 2026-10-09 (#36): focus now starts on the "Team room" title, with no outline.
- [x] **4. Arrow keys in the share menu.** It is marked as a menu, so ↑/↓ should move between items; today only Tab works. — done 2026-10-09 (#36): ↑/↓ wrap, Home/End jump, Tab and Escape close (Escape returns focus to the share button).

## Make the Team room more real

These run on your own device only; nothing is sent to other people.

- [x] **5. Real camera preview on your own tile.** Camera capture is already allowed by the app, so the Camera button can show your webcam instead of initials. — done 2026-10-09 (#37): Camera starts off; turning it on shows your live, mirrored video in your tile and on the stage. Added `NSCameraUsageDescription` to Info.plist.
- [x] **6. Real mic activity for you.** Light your "speaking" badge from the microphone level, and make Mute actually stop the mic. — done 2026-10-09 (#37): the mic starts muted; Unmute opens it and your speaking badge follows the real mic level; Mute releases it.
- [x] **7. Show your screen in your own tile while sharing**, so someone else can be on the stage while you still see what you share. — done 2026-10-09 (#37): putting someone else on stage keeps your share running and moves your screen into your tile; clicking your tile brings it back.

## Quality

- [x] **8. Tests for Voicemail and the Team room.** `frontend/tests/` covers call logic, the dialer, registration and recording waveforms (11 tests), but no screens. Start with:
  - Voicemail: the call button stays disabled until an account is registered.
  - Team room: Share screen stops capturing when you leave the room.
  — done 2026-10-09 (#38): the screens' rules moved into `src/mailbox.ts` and `src/roomMedia.ts` and are covered by 9 new tests (20 in total). The tests check those modules, not the rendered screens; screen-level tests would need a DOM test setup (e.g. happy-dom).
- [ ] **9. Test these in the real app:**
  - [ ] Voicemail call: dial *97, enter the PIN with the keypad, hang up.
  - [ ] Incoming call after the event-permission fix (#34): the call screen should appear without the half-second delay.
  - [ ] Share screen with a real click, including the macOS Screen Recording prompt and a release build.
  - [ ] Ring groups → Call group with a real ring group. A native test call to 2000 on 2026-10-10 got SIP 480 (nobody available), so the PBX needs a real group first.
  - [ ] Merge calls: call your phone (1001), Add call to a second phone, Merge, check all three hear each other, then hang up one side and check the other stays.
  - [ ] Team room camera and mic: macOS asks once for each, your video appears, the speaking badge follows your voice, and Mute/Camera off release the devices.

## Housekeeping

- [x] **10. Remove or archive the nested `yeyofone-desktop/.git`.** It is an older, separate repo (remote `pascalkknde2/yeyofone-desktop`) with its own uncommitted changes, while the `yeyofone-mobile` repo already tracks these files. Easy to commit to the wrong repo by mistake. — done 2026-10-10: it had no unpushed commits or stashes (HEAD = origin/main `301dacc`), so its `.git` was moved out to `~/yeyofone-desktop-nested-git-backup-2026-10-10`. To restore: move that folder back to `yeyofone-desktop/.git`.
- [x] **11. Keep `account.md` and `ssh.md` out of git.** They are in the `yeyofone-mobile` root and have stayed uncommitted. If they hold account or SSH details, add them to `.gitignore`. — done 2026-10-09 (#39): both ignored and `account.md` untracked. Its earlier version is still in git history (`5bb8a94`, on GitHub) and one line looks like a credential, so rotate it if it is real.

## Calls

- [x] **12. Merge calls.** Add call, then Merge joins you and two other people in one call (native `yv_call_merge`, engine action `consult_merge`). — done 2026-10-10 (#41). Tested against the local SIP fixture; not yet tried with real phones (see item 9).
- [x] **13. Improve the in-call screen.** Dark look matching the call bar, bigger caller info, labelled main buttons (Mute, Keypad, Hold, Add call, More, Hang up), a two-calls view with Merge, and a conference view with per-person End. — done 2026-10-10 (#41).
- [ ] **14. Rebuild the iOS framework** with `yeyofone-ios/tools/build-pjsip-ios.sh --bridge-only` so iOS also gets the shared bridge's new merge function (the change is additive; nothing in iOS uses it yet).

## Bigger projects (plan first)

- [ ] **Real video calls and screen sharing over SIP.** The native PJSIP build is audio-only today; needs video enabled and a screen-capture video source.
- [ ] **A real conference backend** (for example a WebRTC media server such as LiveKit, plus signalling and accounts), so others in the Team room actually see and hear you.

## Suggested order

Do 1–4 together as one small cleanup PR, then 5–7 to make the Team room work for real on your own device.
