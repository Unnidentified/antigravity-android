# Antigravity for Android

End-to-end native Jetpack Compose & Material 3 Android client for [`https://antigravity.google.com`](https://antigravity.google.com).

## Native Architecture
No visible browser chrome or embedded web pages — the entire user experience is built natively in Kotlin and Jetpack Compose over an invisible headless session bridge:

- **Native Chat Menu Selector**: Material 3 navigation drawer with New Chat CTA, conversation search, live synced conversations, and Usage & Quota summary.
- **Native Prompt Input Bar**: Auto-expanding Material 3 composer with `+` file attachments, clickable Model Selector chip, Quick Prompts picker, and Send/Stop button with haptics.
- **Native Model Selector & Usage Quota Sheets**: Material 3 bottom sheets for switching models and monitoring usage quota / session telemetry.
- **Collapsible Reasoning Blocks**: Smooth expandable/collapsible thought/reasoning cards with step/duration badges and formatted reasoning steps.
- **Native Markdown, Syntax-Highlighted Code & LaTeX Engine**: Pure-Kotlin Markdown + recursive-descent LaTeX renderer displaying headings, lists, blockquotes, scrollable tables, syntax-highlighted code blocks with 1-tap Copy, and native Compose stacked fractions, roots, superscripts/subscripts, and matrices.
- **Google OAuth & Bridge Diagnostics**: Surfaces Google Sign-In only when authentication is required, and includes a 1-tap **Copy Sanitized DOM Snapshot** diagnostic tool in Settings for fast selector calibration.

## Build & Test
```bash
./gradlew testDebugUnitTest assembleDebug
```
