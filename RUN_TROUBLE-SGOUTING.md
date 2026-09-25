The emulator is stuck offline during boot; the app is not causing the black screen. Restarting ADB did not recover it.

Fix it with a cold boot:

1. Close the emulator window.
2. In Android Studio, open Tools → Device Manager.
3. Find Pixel_7.
4. Click its ⋮ menu.
5. Select Cold Boot Now.
6. Wait for the Android lock/home screen before running the app.

If it remains black, start it without the saved snapshot from PowerShell:

& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" `
    -avd Pixel_7 `
-no-snapshot-load `
-gpu auto

Then confirm it is online:

& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices

You want:

emulator-5554    device

Once online, install and launch YeyoFone:

cd C:\Users\pasca\IdeaSnapshots\yeyofone-mobile\yeyofone-android
.\gradlew.bat installDebug

& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" `
shell am start -n com.yeyofone.app/.MainActivity

If cold boot still fails, use Device Manager → Pixel_7 → ⋮ → Wipe Data, then start it again. Wiping data removes apps and settings stored inside that
emulator, but does not affect your source code.




to build and run the app


• From PowerShell:

cd C:\Users\pasca\IdeaSnapshots\yeyofone-mobile\yeyofone-android

Confirm the emulator is connected:

& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices

It should show:

emulator-5554    device

Build and install the debug app:

.\gradlew.bat installDebug

Launch it:

& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" `
shell am start -n com.yeyofone.app/.MainActivity

Or in Android Studio:

1. Open yeyofone-android.
2. Select app in the run-configuration dropdown.
3. Select the Pixel_7 emulator.
4. Click the green ▶ Run button.

For subsequent changes, clicking Run again automatically rebuilds, reinstalls, and launches the app.