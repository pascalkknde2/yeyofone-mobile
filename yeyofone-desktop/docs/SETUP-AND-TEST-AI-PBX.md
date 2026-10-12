# Setup and test checklist: AI assistant, voicemail and live calls

Everything below is built but **not yet tested end to end**. Work through it in order, ticking each box as you go. Design details are in [ai-notes.md](ai-notes.md).

## 0. Open pull requests and merge order

| PR | What | Branch |
| --- | --- | --- |
| #53 | Android: 32-bit ARM support (moto g pure) | `feat/android-armeabi-v7a` |
| #54 | Team room on iOS and Android, plus desktop "close the room when a call starts" | `feat/mobile-team-room` |
| #55 | Android: lock to portrait | `fix/android-portrait-lock` |
| #56 | Desktop AI call notes (phase 1) | `feat/desktop-ai-notes` |
| #57 | Desktop voicemail transcripts and Listen / Whisper / Barge (phases 2 and 3) | `feat/desktop-voicemail-live-calls` |

- [ ] Merge **#56 before #57**. #57 is built on #56, and its base switches to `main` once #56 is merged.
- [ ] #53, #54 and #55 are independent and can be merged in any order. The Motorola needs #53, or calling won't work on it.

## 1. Build and run the desktop app

From `yeyofone-desktop/frontend`:

```sh
PATH="$PWD/../.native-tools/bin:$PATH" bun run tauri build --features native-voip
open ../target/release/bundle/macos/YeyoFone.app
```

whisper.cpp needs the pinned CMake on `PATH`. The first build takes several minutes.

## 2. Phase 1: AI call notes

### Setup (Settings › AI assistant)
- [ ] Paste your **Claude API key** (`sk-ant-…`, from console.anthropic.com). It's saved in the macOS Keychain.
- [ ] Choose the writing model. Claude Sonnet 5.5 is the default.
- [ ] Download a speech model. **Base · multilingual (148 MB)** is the quickest to start with. The download is checked against a pinned SHA-256.
- [ ] Optional: turn on **Record every call for AI notes**. Tell the other party: many places require their consent to record.

### Test
- [ ] Make a real call (for example 1005 → 1002), talk for about 30 seconds, and start recording (or let automatic recording do it).
- [ ] After hanging up, check `~/Library/Application Support/com.yeyofone.desktop/call-recordings/` for **three files**: `<id>.wav`, `<id>.you.wav` and `<id>.them.wav`. This is the new three-track recording, and the automated tests couldn't cover it.
- [ ] In **Call history**, click **✦ AI notes** on the call. The status should go from "Transcribing…" to "Claude is writing…" to the notes.
- [ ] Check the notes: the summary, action items, decisions, the follow-up draft (try **Copy**), the talk-share bar, coaching, and the **Transcript** tab with "You" and "Caller" in the right places.
- [ ] Type a word you said into the Call history search box. The call should appear under "✦ Said on calls", with the word highlighted.
- [ ] Open **AI insights** in the sidebar. The totals, mood, talk share, topics and coaching should appear.
- [ ] Delete the recording. All three WAV files should be gone.

## 3. Install the PBX add-on (needed for phases 2 and 3)

This changes the production server, so run it yourself. From `yeyofone-desktop/`:

```sh
SSH="ssh root@<your-pbx>" pbx/install-yeyofone-api.sh
```

- [ ] The script prints `No syntax errors detected` and then `Installed /var/www/fusionpbx/app/yeyofone/api.php`.
- To undo it, remove `/var/www/fusionpbx/app/yeyofone` on the PBX.

### In FusionPBX (web admin)
- [ ] **Accounts › Users** › your user › generate an **API key**, then save. Keep it secret.
- [ ] **Accounts › Extensions › 1005** › link that user to the extension, then save. At the moment no extension has a user linked; without this, the voicemail list stays empty.
- [ ] For Live calls, the user needs the `call_active_view` permission. Admins have it.

### In the app (Settings › AI assistant › Phone system)
- [ ] Enter `https://sysinfos.co.uk` and the API key, then **Connect**. The row should show the domain, extension 1005, and "Voicemail allowed · Live calls allowed".
- [ ] Enter the **eavesdrop PIN**, the one the PBX asks for after `*33`, stored in FusionPBX's `eavesdrop` dialplan. It's saved in the Keychain, and the app keys it in for you.

If connecting fails:
- "The YeyoFone add-on isn't installed on the PBX": run the install script.
- "The PBX didn't accept the API key": the key is wrong or wasn't saved in FusionPBX.
- "Your FusionPBX user doesn't have permission": give the user the missing permission.

## 4. Phase 2: voicemail transcripts

- [ ] Open **Voicemail**. The **Messages** list should show the PBX's voicemails for 1005. Leave yourself a test message if there are none.
- [ ] Click **✦ Transcribe** on one. The notes should show a summary, what to do, a reply draft and urgency (marked **Urgent** if high), plus the transcript.
- [ ] The one-line summary should then appear under the message in the list.
- [ ] With **Notes for every recorded call** on, leave a new voicemail. It should be transcribed by itself within about 2 minutes.
- Known limit: voicemails stored as **MP3** show "can't be transcribed yet". FusionPBX here records WAV.

## 5. Phase 3: Listen / Whisper / Barge

You need two other extensions on a call, for example 1001 calling 1002, while you're on the Mac as 1005.

- [ ] Open **Live calls**. Both extensions should appear as "Talking with …", with a running timer. Your own extension is not listed.
- [ ] Click **Listen** on one. The Mac dials `*33<ext>`, keys in the PIN, and shows "Connected to …". You should hear both sides, and they shouldn't hear you.
- [ ] Switch to **Whisper**. Only the agent should hear you.
- [ ] Switch to **Barge**. Both sides should hear you.
- [ ] Switch back to **Listen**, then click **Leave**. The eavesdrop call should end and the agents' call should continue.
- If you hear "invalid PIN", re-enter the PIN in Settings. If it says nobody is there, the extension's call must be ACTIVE (answered).

## 6. Other things to test

- [ ] **Team room (#54):** on the iPhone and the Motorola, open Chat › video icon. Turn the camera on (your tile shows live video) and the mic on (your tile's badge lights up when you talk). Open People / Chat, then Leave. On the Mac, open Video conference, then call 1005 from 1002: the room should close and the incoming call should show.
- [ ] **Portrait lock (#55):** turn the Motorola sideways with YeyoFone open. The app should stay upright.
- [ ] **Motorola (#53):** after merging, a build from `main` should still register 1005 with 200 OK on the moto g pure.

## Notes

- **Cost:** Claude requests use your API key and are billed to your Anthropic account. Transcription runs on the Mac and costs nothing.
- **Privacy:** call and voicemail audio never leave the Mac. Only text transcripts go to Claude.
- **Rotate the eavesdrop PIN:** it is stored in plain text in FusionPBX's `eavesdrop` dialplan and was shown during setup. If you change it, update it in the app too.
