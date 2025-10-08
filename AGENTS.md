# AlfredAI contribution guide

Welcome! This document collects the project-specific conventions and guardrails that are easy to miss when skimming the codebase. Please read it fully before modifying files in this repository.

## Repository overview

AlfredAI is a multi-module Android project that implements a push-to-talk assistant powered by the OpenAI Realtime API.

* **`mobile/`** – Primary Android app (phone/tablet). Jetpack Compose UI, Android services, audio routing, and the main `MobileViewModel` live here.
* **`wear/`** – Companion Wear OS app that mirrors most mobile logic. Shares state via message APIs defined in `SharedViewModel`.
* **`shared/`** – Code shared by the phone and watch modules (Compose theme, base `SharedViewModel`, function management helpers).
* **`openai/`** – Hand-written realtime transport/client that wraps the generated OpenAI SDK with Android-friendly glue.
* **`openai-openapi-kotlin/`** – Generated Kotlin client from OpenAI’s OpenAPI schema. Treat the contents as generated artifacts (see [Regenerating the client](#regenerating-the-client)).
* **`utils/`** – Small, platform-agnostic helpers (logging utilities, string redaction, audio helpers).
* **`openai-realtime-js/`** – Static HTML/JS playground that mirrors OpenAI’s sample web implementation. Mostly used for reference and manual testing.

The repo targets **Android API 34+** and Kotlin **2.1**, with Compose Material 2/3, Wear APIs, Twilio’s `audioswitch`, and the WebRTC SDK.

## Build, test, and tooling

* Use Gradle from the repo (`./gradlew`). The most relevant tasks:
  * `./gradlew :mobile:assembleDebug` builds the handheld app.
  * `./gradlew :wear:assembleDebug` builds the Wear OS app.
  * `./gradlew :openai:lint` runs static checks on the realtime transport module.
  * `./gradlew lint` runs Android/Compose lint across all modules.
* No automated unit or UI test suites are wired up yet. When you add new business logic, prefer accompanying unit tests (JUnit4 for JVM, Compose UI testing for Compose screens) and document how to run them in your PR.
* Kotlin formatting follows the **official** code style (`kotlin.code.style=official`). Apply the style before committing; Android Studio’s “Reformat Code” with the official style enabled is sufficient.
* Keep Gradle build logic minimal inside module `build.gradle.kts` files. Reuse catalog dependencies (`libs.`) instead of hardcoding versions.

## Kotlin & Compose conventions

* Prefer `val` to `var`, nullable types to sentinel values, and explicit return types on public APIs.
* Compose functions should be small, pure, and state-hoisted: UI state lives in `ViewModel`s or `rememberSaveable` containers, not deep inside composables.
* When creating previews, keep them in `*-Preview.kt` (see `MobileViewModelPreview.kt`) and guard debug-only code with `@Preview` + `BuildConfig.DEBUG` checks where necessary.
* Side effects and background work run inside `viewModelScope` or injected `CoroutineScope`s. Never block the main thread.
* Use structured logging patterns already present in the code (`Log.d(TAG, ...)`, `RealtimeLog`). Avoid `println` and string concatenation – use Kotlin string templates.
* Follow existing patterns for debug toggles: constants named `debugFoo` inside `companion object`s gated by `BuildConfig.DEBUG && false`.
* Strings visible to users belong in `res/values/strings.xml`. We know localization isn’t complete yet, but new UI strings must use resources.

## View models & shared messaging

* `SharedViewModel` encapsulates Wear-to-phone communication. If you extend or modify messaging, update both mobile and wear implementations and keep message paths (`/ping`, `/pushToTalk`, etc.) consistent.
* Surface state with `StateFlow` and expose read-only versions (`asStateFlow()`). Mutations stay private.
* For audio interactions, prefer the helpers in `utils/Utils.kt` (`playAudioResourceOnce`, `audioDeviceInfoToString`, etc.) to keep logging consistent.

## Realtime client module (`openai/`)

* Transport-specific logic lives in classes implementing `RealtimeTransport`. Add new server events by extending the `RealtimeTransportListener` interface and threading the callbacks through `RealtimeClient`.
* Handle OpenAI API errors gracefully. Reuse helper methods such as `Utils.quote`, `Utils.extractValue`, and avoid swallowing exceptions silently.
* The realtime client depends on the generated OpenAI SDK. Don’t reimplement models that already exist in `com.openai.models`; import and reuse them.

## Regenerating the client

* The OpenAPI-based client in `openai-openapi-kotlin/` is generated via `openai-kotlin-client.sh`. Do not hand-edit code inside `openai-openapi-kotlin/lib/src` unless you are documenting a temporary patch needed post-generation.
* If the schema changes, update the `openapi-YYYYMMDD.yaml` file, run `./openai-kotlin-client.sh`, and document any manual fixes required (the script contains notes for known adjustments).

## JavaScript playground (`openai-realtime-js/`)

* This directory mirrors OpenAI’s sample realtime web UI. Keep changes minimal and backwards compatible so the page continues to load via `index.html` without a build step.
* Avoid committing real API keys. The playground reads from a raw text field intentionally labelled as dangerous.

## Secrets & configuration

* Never commit actual OpenAI API keys. `mobile` loads `DANGEROUS_OPENAI_API_KEY` from your local `gradle.local.properties`; keep that file out of version control.
* Watch for `TODO` comments that flag insecure or placeholder behavior; highlight unresolved risks in your PR description.

## Documentation expectations

* Update `README.md` when you add features that affect setup, capabilities, or demo flow.
* Include diagrams or notes in `art/` if your change materially alters user flows or architecture.

Thank you for keeping AlfredAI healthy! When in doubt, match the existing patterns before introducing new ones.
