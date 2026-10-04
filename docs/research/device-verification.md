# Android device verification

Environment: Android emulator `emulator-5554`, API 36, Android Studio SDK on Windows.
Date: 2026-10-04. All contacts/numbers used below are synthetic test fixtures.

## Observed results

- A clean install was performed with `adb uninstall com.vdx.alpha` followed by
  `adb install app/build/outputs/apk/debug/app-debug.apk` (no `-r`).
- Launcher opens VDX Call without a permissions wall or Accessibility prompt.
- `settings get secure enabled_accessibility_services` returned `null`.
- Searching for a synthetic contact asks for Contacts permission at that moment.
  Granting it resolves `VDXTestPerson`, `2025550123` and presents confirmation.
- Cancel displays “Cancelled. No dialer was opened.” Repeating the lookup and
  confirming opens `com.google.android.dialer` with `(202) 555-0123` populated.
- A fresh `uiautomator dump --compressed` identifies the dialer's `id/digits`
  element and `id/dialpad_voice_call_button`. The Call button was **not** pressed.
- Captured screenshot and UI hierarchy show confirmation and the dialer. Local
  outputs are under ignored `artifacts/device/`; only synthetic fixture data is
  used. Tool cold-start/null-root failures were retried after the UI settled;
  stale dumps are not evidence of success.
- Crash log was empty when inspected after the call handoff. No app crash was
  observed. This is not a claim of exhaustive performance or device testing.
- Denying microphone access preserves the screen and typed input with an explicit
  recovery message. After microphone access was granted for testing, the actual
  on-device recognizer reported an unavailable language model. The app displayed
  that condition and offered fallback choices without silently starting a cloud
  recognizer. A system keyboard stylus tutorial briefly obscured touch input;
  closing that system overlay restored normal interaction.

## Repeat the acceptance test

Use a disposable emulator or a test phone. Uninstall clears app state and keys.
Do not run this against a personal installation whose state you need to preserve.

```text
adb uninstall com.vdx.alpha
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.vdx.alpha/com.vdx.call.CallActivity
adb shell settings get secure enabled_accessibility_services
```

Create a synthetic contact in Contacts. Search its name in VDX; deny Contacts
once and verify typed numeric recovery. Grant Contacts on a new lookup. Add a
second number or same-name contact and verify an explicit choice is offered.
Cancel once, then repeat and confirm. Capture the dialer before pressing Call:

```text
adb shell uiautomator dump --compressed /sdcard/vdx-dial.xml
adb pull /sdcard/vdx-dial.xml
adb shell screencap -p /sdcard/vdx-dial.png
adb pull /sdcard/vdx-dial.png
adb logcat -d -b crash
```

## Not yet established by this evidence

- A real spoken utterance recognized by a physical device's on-device engine.
  The installed emulator has a system speech provider; a configured on-device
  provider/model has not been established. Simulated recognizer unit tests are
  explicitly not equivalent to microphone recognition evidence.
- Real Groq transcription with a user-owned key. WAV and provider response
  contracts are tested locally without sending anyone's audio or using credentials.
- OEM/locale speech quality, TalkBack usability sessions, real call connection,
  and Google Play approval. Release signing and store publication are not done.

Consequently, the specification's complete real-device voice acceptance gate
must remain open even when code tests and the emulator dialer gate pass.
