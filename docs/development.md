# Development & Release Workflow

## Environment Requirements

- **JDK**: 17 (configured in [`app/build.gradle.kts`](../app/build.gradle.kts))
- **Android SDK**: `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`
- **Gradle / AGP / Kotlin**: Gradle 8.9 wrapper, AGP `8.7.3`, Kotlin `2.0.21`, Compose BOM `2024.12.01` (versions centralized in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml))

## Build & Unit Tests

Run all unit tests and assemble the debug APK:

```bash
./gradlew testDebugUnitTest assembleDebug
```

If running in an environment where `ANDROID_HOME` is non-standard (for example `/opt/android-sdk`):

```bash
ANDROID_HOME=/opt/android-sdk ./gradlew testDebugUnitTest assembleDebug
```

Unit tests live under `app/src/test/java/com/antigravity/wrapper/`:
- `data/BridgeStateParserTest.kt`
- `data/RecordCodecTest.kt`
- `ui/markdown/MarkdownAndLatexParserTest.kt`
- `web/UrlRouterTest.kt`
- `web/UserAgentSanitizerTest.kt`

The assembled APK outputs to `app/build/outputs/apk/debug/app-debug.apk`.

## Git & PR Rules

Per [`AGENTS.md`](../AGENTS.md), every commit message and squash-merge subject **must** be short and follow:

```text
xxx: xxx (#prnumber)
```

Examples:
- `fix: trusted send and live stream (#9)`
- `docs: add lowercase docs and changelog (#10)`

Also remember to bump both `versionCode` and `versionName` in [`app/build.gradle.kts`](../app/build.gradle.kts) before tagging a release.

## Publishing Releases (CI Only)

`.github/workflows/release.yml` runs `./gradlew testDebugUnitTest assembleDebug` on pushes, PRs, and `v*` tags:
1. Merge changes to `main` via PR.
2. Tag `main` with `git tag -a v1.0.0-alpha.X -m "Antigravity v1.0.0-alpha.X" && git push origin v1.0.0-alpha.X`.
3. Watch the workflow run (`gh run list` -> `gh run watch <id> --exit-status`). GitHub Actions attaches `antigravity-debug.apk` to the tag release automatically using `secrets.GITHUB_TOKEN`.

Note: Avoid uploading release assets directly from sandboxed/proxied shells where `uploads.github.com` may reject scoped proxy credentials (`HTTP 401`); pushing a `v*` tag lets GitHub Actions publish cleanly.

## Known Targets for Next Model

- **Usage & Quota DOM verification**: `v1.0.0-alpha.9` added deep ancestor-sibling traversal and a 3s async wait in `findUsageContainerFromSheet`, plus `usage=...` telemetry in `diagnosticsNote`. If Antigravity opens *View Usage* in a separate route or external tab on some accounts, inspect a DOM snapshot taken immediately after tapping `View Usage` and adapt `refreshQuota()`.
- **File attachment handling**: `triggerFileUpload()` locates `input[type="file"]` or `button[aria-label="Add context"]` and delegates to `MainActivity.handleShowFileChooser`. Verify end-to-end attachment chip sync if the web composer requires a follow-up snapshot after file selection.
- **Permission preset selector**: `permissionsPreset` (`[data-testid="permissions-selector-trigger"]`) is parsed into `BridgeSnapshot.permissionsPreset`, but does not yet have a native switcher control in the composer bar.
- **Release signing**: CI currently ships `antigravity-debug.apk` (`isMinifyEnabled = true` is configured only for `release` build type).
