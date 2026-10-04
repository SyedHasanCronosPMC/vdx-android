# Loop engineering state

Objective / scope: Complete and harden the existing first-release call specification.
Source: public luxurylifestyleco/vdx-android; user has READ. Destination: own GitHub
account SyedHasanCronosPMC. Preserve unrelated Elastic Web working tree.
Starting commit: 1a1147c0a7c8fa0a2ba576f2c3357ae2aae5e1e0 (upstream master).

Baseline: `gradlew.bat testDebugUnitTest` passed, 218 tests / 0 failures / 0 skips,
with Android Studio JBR 21 and Android SDK 36. Declared dependencies downloaded.
No source edits were made until baseline compilation/testing completed.

Milestones / acceptance: see call-release.md.
Changes and checks: dedicated call entrypoint, confirmation state, bounded contact
lookup, on-device-first speech, explicit system/Groq fallback, optional microphone
bubble, narrow manifest, cloud audio repairs, build workflow and documentation.
Full `testDebugUnitTest lintDebug assembleDebug assembleRelease` passed: 238 tests,
0 failures/skips, lint 0 errors (171 warnings including retained legacy sources).
Fresh emulator contact -> confirmation -> DIAL observed without Accessibility.
Checker: separate read-only release_checker; final verdict pending.
Repair history: checker identified malformed reused WAV serialization, nonexistent
Groq confidence field, and disappearing confirmation target during speech. All
three repaired with fixtures and persistent target display. Initial lint exposed
an existing API-29 call on minSdk28, fixed with a version guard. Manifest inspection
exposed unused Sentry/WorkManager startup components; Sentry dependency removed,
WorkManager entrypoints removed from release manifest and contract-tested.
Deferred decisions: Play publication and broader assistant tasks out of scope.
Open release evidence: physical on-device speech and live BYOK transcription,
as documented in device-verification.md; do not claim full voice release acceptance.
Next action: independent verification, finish device evidence, push reviewed scope.
Final commit / remote verification: pending.
