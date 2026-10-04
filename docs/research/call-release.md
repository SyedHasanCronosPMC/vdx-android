# Call release research and implementation contract

Research date: 2026-10-04. Authoritative target: `FIRST-RELEASE-CALL-SPEC.md`.
Upstream baseline: `1a1147c0a7c8fa0a2ba576f2c3357ae2aae5e1e0`.

## Product and architecture

The release does one job: resolve a spoken or typed person, obtain confirmation
for their exact number, and open the phone dialer. It does not place a call or
verify a connected call. Its primary audience is people who find ordinary phone
navigation difficult, including low-vision users; it is not a screen reader.

The existing app is a much broader automation prototype. The release-specific
spec takes precedence over `.cursorrules`' earlier full-assistant task matrix.
The external `~/vdx` product documents referenced there are not in this repository;
their contents are not assumed. Existing assistant sources and test assertions
remain available for future work, but are not release entrypoints.

## Findings that change implementation

| Evidence | Decision |
| --- | --- |
| Existing launcher directs setup to Accessibility; Sonic diagnostics and screen reads precede calls | Dedicated call entrypoint and local state machine, bypassing the assistant engine |
| Manifest declares call/SMS/log permissions and Accessibility services | Narrow the shipping manifest, remove legacy entrypoints and automatic telemetry initialization |
| Existing cloud keys make PCM/cloud the primary route | Explicit on-device recognition first; fallback only after local failure and a separate user action |
| Contact lookup returns the first SQL LIKE match | Exact names first, bounded partial results, explicit candidate/number selection |
| Legacy confirmations accept prefix matches and call strings claim dialing | Whole-response confirmation, expiry and one-use pending target, honest dialer wording |

## Primary research

1. [Android common phone intents](https://developer.android.com/guide/components/intents-common#Phone):
   `ACTION_DIAL` pre-fills a number and requires the user to press Call. Only
   `ACTION_CALL` needs `CALL_PHONE`. Therefore this release needs no call permission.
2. [SpeechRecognizer API](https://developer.android.com/reference/android/speech/SpeechRecognizer):
   on-device availability/creation APIs require API 31. The generic recognizer can
   stream audio remotely. All calls belong on the main thread and the recognizer
   must be destroyed. Therefore generic recognition is an explicitly disclosed
   fallback, never described as guaranteed offline.
3. [RecognizerIntent API](https://developer.android.com/reference/android/speech/RecognizerIntent):
   `EXTRA_PREFER_OFFLINE` is a preference which implementations may ignore. It is
   not proof of offline processing. We do not infer privacy from that flag.
4. [Foreground service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start):
   microphone services require while-in-use permission and should start while an
   activity is visible. Therefore bubble setup starts from an explicit foreground
   action after microphone permission, never boot or background auto-start.
5. [Google Play Accessibility policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en):
   Accessibility API usage has declaration and eligibility requirements. Removing
   those components is appropriate for this release. This is an engineering
   inference, not a guarantee of Play approval. Microphone foreground-service
   declarations and privacy disclosures still need store review.

## Bounded milestones and acceptance

1. Establish untouched baseline with `gradlew.bat testDebugUnitTest`.
2. Call boundary: input -> resolve -> choose -> confirm -> `ACTION_DIAL`.
   Never invent contacts, select an ambiguous first match, dial after cancellation,
   or reuse an expired confirmation. Numeric input works without contacts access.
3. Voice and onboarding: no setup permission wall, no Accessibility prompt,
   explicit tap-to-talk, on-device first, disclosed optional fallback, keyboard
   recovery, usable large controls and spoken status using a local TTS voice.
4. Shipping boundary: minimal permissions, no Accessibility services or legacy
   automation receiver, no app telemetry startup, no backup of keys or transcripts.
5. Verify regression suite, adversarial/capability bars, release build/lint and
   fresh emulator install. Capture DIAL hierarchy/screenshot/logcat and separately
   report what real speech/OEM testing remains unproven.
6. Independent read-only checker reviews exact revision and runs tests. Repair
   concrete findings, then push verified work to the user's GitHub repository.

## Honest copy and unresolved release gates

The source spec contains conflicting copy: “No cloud” versus a system recognizer
that may use a network, and “called them” versus `ACTION_DIAL`. Implementation
uses accurate wording: no account, no VDX cloud, on-device voice when available,
and “Dialer opened. Tap Call to connect.” Optional BYOK sends audio to the selected
provider only after explicit consent following a failed local attempt.

Store publication, physical-device voice quality across OEMs/locales, provider
account testing, and marketing/user research with real participants are separate
release evidence. An emulator or mocked recognizer cannot prove those claims.
