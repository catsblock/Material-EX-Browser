# Material EX Browser

Work in progress. Not a usable browser yet.

## Status

Done and verified (38 unit tests, run with kotlinc 2.1.20 on JDK 21):
- URL / search-input resolution with unsafe-scheme rejection
- Search engine templates and custom template validation
- Per-site permission policy: Ask / Allow / Deny, exact three-denial auto-block, reset, profile and origin isolation
- Tab state manager: open, close, duplicate, restore, reorder, loaded-tab limit (1-30)

Written but not built (Gradle and Maven were unreachable from the authoring sandbox): `android/` Gradle configuration and the CI workflow.

Not started: GeckoView integration, Compose UI, onboarding, Room data layer, settings, downloads, extensions, localization resources, Windows (CEF) app, documentation.

Build: `cd android && gradle testDebugUnitTest assembleDebug` (Gradle 8.13, JDK 17). The Gradle wrapper is not included.

License: GPL-3.0 (see LICENSE). GeckoView is MPL-2.0.
