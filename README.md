# Antigravity for Android

Native Jetpack Compose and Material 3 client for [antigravity.google.com](https://antigravity.google.com).

The interface runs natively in Kotlin over a headless `WebView` DOM bridge. Browser chrome and web UI remain hidden during normal usage and appear only for Google sign-in or when explicitly enabled in Developer Diagnostics.

## Features

- **Conversations & navigation**: Material 3 navigation drawer with search, live synced conversations, active workspace indicator, and New Chat action.
- **Prompt composer**: Auto-expanding native input bar with file attachment picker, inline model selector pill, Quick Prompts sheet, and Send/Stop controls.
- **Model & thinking effort picker**: Native bottom sheet supporting both direct models and two-step thinking effort tiers (`Low`, `Medium`, `High`, `Extra`).
- **Usage & quota sheet**: Live quota extraction from Antigravity's `View Usage` panel with percentage indicator, reset timing, and session status.
- **Collapsible reasoning**: Expandable thought/reasoning cards (`worked-for-collapsible`) with duration badges and streaming status.
- **Pure-Kotlin Markdown & LaTeX**: Native rendering for headings, lists, blockquotes, pipe tables, syntax-highlighted code blocks, artifact/diff cards, and composed LaTeX math (fractions, roots, scripts, accents, matrices).
- **Theming & diagnostics**: System, Dark, AMOLED, and Light modes with optional Material You dynamic color, message text-size presets, haptics, keep-screen-awake while streaming, and a 1-tap sanitized DOM snapshot exporter.

## Documentation

Lowercase engineering docs for contributors and coding agents live in [`docs/`](docs):

- [`docs/architecture.md`](docs/architecture.md) — Layer model, state flow, focus lock, and compositor invariants.
- [`docs/dom-bridge.md`](docs/dom-bridge.md) — Calibrated DOM selectors, `window.__agBridge` API, trusted input dispatch, and snapshot calibration.
- [`docs/rendering.md`](docs/rendering.md) — Markdown/LaTeX AST pipeline, syntax highlighting caps, and scroll performance rules.
- [`docs/development.md`](docs/development.md) — Local build/test commands, CI release workflow, commit conventions, and known edge cases.

## Build & Test

Requires JDK 17 and Android SDK 35 (`minSdk 26`).

```bash
./gradlew testDebugUnitTest assembleDebug
```

Debug APKs are built and attached automatically by GitHub Actions on `v*` tags on the [Releases](https://github.com/Unnidentified/antigravity-android/releases) page.

## License

Licensed under the [MIT License](LICENSE).
