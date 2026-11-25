# Antigravity for Android

Native Jetpack Compose & Material 3 Android app for [`https://antigravity.google.com`](https://antigravity.google.com).

## Features
- **Native Jetpack Compose + Material 3 Shell**: Full bottom navigation (`Workspace`, `Saved`, `Downloads`, `Settings`), branded navigation drawer with pinned bookmarks, adaptive edge-to-edge bars, and IME-aware layout that hides bottom chrome while typing.
- **Material You & Themes**: Supports System, Dark, pure AMOLED Black, and Light themes, plus Android 12+ Material You dynamic colors.
- **Quick Controls & Quick Prompts**: One-tap bottom sheet with Back/Forward/Home/Share/Copy/Find actions, live text scaling (80%–150%), Mobile UI Polish, Desktop Viewport mode, Focus Mode, and reusable Quick Prompts that insert directly into the active input (or copy to clipboard).
- **Retained Workspace Session**: Switching between native tabs never reloads or resets your active Antigravity session.
- **Native Bookmarks & Downloads**: Search, pin, and organize Antigravity links; track authenticated downloads in-app with one-tap access to Android Downloads.
- **Google OAuth & Mobile Web Integration**: Persistent first-/third-party cookies, sanitized Chrome Mobile User-Agent, Chrome Custom Tabs for external links, native file uploads (`OpenMultipleDocuments`), and origin-checked media permissions.

## Build & Test
```bash
./gradlew testDebugUnitTest assembleDebug
```

## Automated Releases
Pushing a tag matching `v*` (for example `v1.0.0-alpha.2`) triggers [`.github/workflows/release.yml`](.github/workflows/release.yml) to run unit tests, assemble `antigravity-debug.apk`, and automatically publish the GitHub release (marked as a prerelease when the tag contains `-`) with the APK attached.
