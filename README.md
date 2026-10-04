# VDX Call

A focused Android call companion: say or type a person, check their name and
number, confirm, then tap **Call** in Android's dialer. No account, Accessibility
service, SMS access, call-log access, or direct-call permission is required.

This fork implements the existing [first-release call specification](FIRST-RELEASE-CALL-SPEC.md).
The broader upstream assistant remains in source and regression tests, but its
activities, accessibility services, telemetry initialization, and automation
receiver are not declared as release entrypoints.

## Try it

1. Install the debug APK from a successful **Android call release** Actions run,
   or build locally. This is a development build, not a Play release.
2. Launch **VDX Call**. Type a name or number, or tap **Tap to speak**.
3. Allow Contacts only when searching for a name; a number needs no permission.
4. Choose between matching people/numbers. Check the displayed number and tap
   **Yes, open dialer**, or tap the mic and say **yes**.
5. Tap **Call** in the phone app. VDX does not place or claim a connected call.

The optional **Floating microphone** asks for microphone/overlay access only
when selected. Tap it to capture one utterance; hold it or use its notification
action to close it. It never listens continuously or starts after boot.

## Voice and privacy

- Android 12+ on-device recognition is tried first. A compatible installed language
  model is required; availability and quality depend on the device and locale.
- If local recognition is unavailable or fails, typing always remains available.
  **Voice fallback options** explicitly offers the system speech service, which
  may send audio to its provider, or Groq with your own encrypted key.
- Saving a Groq key does not enable automatic uploads. After a local failure,
  explicitly choose Groq, make a new recording, and tap **Stop and transcribe**.
  Recording stops after 20 seconds. The selected provider receives the audio;
  its own account, billing and retention terms apply.
- No VDX server, telemetry startup, cloud backup, saved transcripts, or call
  history is used by this entrypoint. Optional API keys remain encrypted locally.
- Spoken prompts use a local TTS voice when one is installed. Screen text remains
  available when local speech output is unavailable.

## Build and verify

Use JDK 21, Android SDK platform 36 and build tools 36.0.0. Set `JAVA_HOME` and
`ANDROID_HOME` (or untracked `local.properties` with `sdk.dir`). Dependencies and
the Gradle wrapper download on the first run.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

On Linux/macOS use `bash gradlew` with the same tasks. Debug APK:
`app/build/outputs/apk/debug/app-debug.apk`. Release APK is unsigned; production
signing and store submission are deliberately separate. No signing keys belong
in this repository.

Tests cover the existing adversarial and capability corpora plus call confirmation,
cancellation, lifecycle, contact ambiguity, permission-free numeric input,
recognizer callbacks, merged manifest privacy, and cloud audio contracts.

## Evidence and limits

Read [research and plan](docs/research/call-release.md),
[loop state](docs/research/loop-state.md), and
[device verification](docs/research/device-verification.md).

Android unit tests and emulator dialer checks do not prove microphone recognition
quality on a person's physical phone. Real speech, OEM/locales, a live Groq account,
and Play policy review remain explicit release gates. The specification's old
“No cloud” / “called them” marketing copy is not used because it would overstate
the fallback privacy and `ACTION_DIAL` behavior.

Upstream: [luxurylifestyleco/vdx-android](https://github.com/luxurylifestyleco/vdx-android).
