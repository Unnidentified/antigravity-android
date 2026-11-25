# Antigravity for Android

Native Kotlin Android WebView wrapper for [`https://antigravity.google.com`](https://antigravity.google.com).

## Features
- **Single-Activity Material 3 UI** with edge-to-edge `WindowInsets` handling, dark minimalist adaptive icon, and AndroidX `SplashScreen`.
- **Google OAuth Compatible WebView** with JavaScript, DOM/database storage, hardware acceleration, persistent first- and third-party cookies, and sanitized Chrome Mobile User-Agent.
- **Smart URL Routing** keeping `antigravity.google.com` and Google auth domains in-app while opening external links in Chrome Custom Tabs.
- **Native UX** including `OnBackPressedCallback` history navigation, top `LinearProgressIndicator`, scroll-gated `SwipeRefreshLayout`, native offline/error screen with retry, file uploads (`OpenMultipleDocuments`), authenticated downloads (`DownloadManager`), and origin-checked `onPermissionRequest` handling.

## Build
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
