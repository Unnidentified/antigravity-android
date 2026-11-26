# Architecture

Antigravity for Android renders all interactive UI natively in Jetpack Compose (`androidx.compose.material3`) while delegating session state, authentication, prompt dispatch, and live streaming to an embedded Chromium `WebView` acting strictly as a headless transport and DOM state source.

## Visual Layer Stack

[`AntigravityNativeApp`](../app/src/main/java/com/antigravity/wrapper/ui/AntigravityNativeApp.kt) composes three z-ordered layers inside a root `Box`:

1. **Layer 0 (`zIndex` `0f` normally, `3f` when `showWebOverlay = true`)**: Hosts the singleton `WebView` configured by [`AntigravityBridgeEngine`](../app/src/main/java/com/antigravity/wrapper/bridge/AntigravityBridgeEngine.kt). Surfaced to the user only when:
   - `bridgeStatus == BridgeStatus.AUTH_REQUIRED` (Google Sign-In / Consent pages), or
   - `settings.webInspectorMode == true` (Settings -> Developer Diagnostics -> *Show underlying session*).
2. **Layer 1 (`zIndex = 1f`)**: Opaque `Surface(color = MaterialTheme.colorScheme.background)` containing `ModalNavigationDrawer` ([`ChatDrawerSheet`](../app/src/main/java/com/antigravity/wrapper/ui/components/ChatDrawer.kt)), `Scaffold` ([`ChatTopBar`](../app/src/main/java/com/antigravity/wrapper/ui/components/ChatTopBar.kt), message `LazyColumn` or [`EmptyChatWelcome`](../app/src/main/java/com/antigravity/wrapper/ui/components/NativeSheets.kt), [`PromptComposerBar`](../app/src/main/java/com/antigravity/wrapper/ui/components/PromptComposerBar.kt)), and modal sheets (`ModelSelectorSheet`, `UsageQuotaSheet`, `QuickPromptsSheet`).
3. **Layer 2 (`zIndex = 2f`)**: Full-screen opaque `SettingsFullScreen` with horizontal slide transitions (`slideInHorizontally` / `slideOutHorizontally`) backed by a solid background `Surface` so underlying chat items never bleed through during navigation transitions.

## Key Runtime Invariants

Never break these invariants without device verification:

- **Keep hidden `WebView.alpha = 1f`**: Layer 1 is opaque and covers Layer 0 cleanly. Setting `view.alpha = 0f` or `View.INVISIBLE` causes Android HWUI/Chromium to treat the `WebView` as occluded and throttle `BeginMainFrame`, React 18 commits, and live token streaming.
- **Hidden `WebView` focus & touch lock**: `AntigravityBridgeEngine.setInteractive(false)` sets `isFocusable = false`, `isFocusableInTouchMode = false`, `descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS`, and consumes touch events. Never mutate focusability inside `AndroidView(update = ...)` on recomposition—only update it from `LaunchedEffect(showWebOverlay)` or during the short `sendPrompt` unlock window (`restoreFocusLock()` + 2.6s guard). Otherwise, clicking the native `BasicTextField` inside an open thread loses focus and collapses the soft keyboard immediately.
- **Off-main-thread snapshot parsing**: `NativeJsBridge.onStateSnapshot` runs [`BridgeStateParser.parseSnapshot`](../app/src/main/java/com/antigravity/wrapper/data/BridgeStateParser.kt) on a single-thread `Executors.newSingleThreadExecutor()` and drops identical consecutive snapshots before hopping to `mainHandler`.

## State Flow

```text
Antigravity SPA DOM
   ├── MutationObserver (throttled: 380ms streaming / 950ms idle) + 600ms post-send heartbeat
   ▼
AntigravityBridgeScript.kt (window.__agBridge v7)
   ├── JSON.stringify snapshot (only pushed if hash changed or force=true)
   ▼
AntigravityBridgeEngine.NativeJsBridge.onStateSnapshot
   ├── parserExecutor -> BridgeStateParser.parseSnapshot -> dedupe
   ▼
ChatViewModel.onBridgeSnapshot (StateFlow<ChatUiState>)
   ├── merges optimisticMessages until server user turn appears
   ├── resolves isSwitchingConversation once new URL/messages arrive
   ▼
Compose UI (AntigravityNativeApp)
```

## Module Map

| Package / File | Responsibility |
| --- | --- |
| [`MainActivity.kt`](../app/src/main/java/com/antigravity/wrapper/MainActivity.kt) | Splash gate (max 750ms), edge-to-edge system bars, back stack, `WebViewClient` / `WebChromeClient` callbacks, file chooser (`OpenMultipleDocuments`), runtime permissions, Custom Tabs, clipboard/share. |
| [`bridge/AntigravityBridgeEngine.kt`](../app/src/main/java/com/antigravity/wrapper/bridge/AntigravityBridgeEngine.kt) | `WebView` setup, cookie persistence, UA sanitization, focus lock, trusted Android `MotionEvent`/`InputConnection`/`KeyEvent` dispatch, background JSON parsing. |
| [`bridge/AntigravityBridgeScript.kt`](../app/src/main/java/com/antigravity/wrapper/bridge/AntigravityBridgeScript.kt) | Injected JS bridge (`BRIDGE_VERSION = 7`), DOM extraction (`conversations`, `messages`, `reasoning`, `artifacts`, `models`, `quota`), UI automation, sanitized DOM dumper. |
| [`data/Models.kt`](../app/src/main/java/com/antigravity/wrapper/data/Models.kt) | Immutable data models (`AppSettings`, `ChatConversation`, `ChatMessage`, `ReasoningBlock`, `ArtifactCard`, `ModelOption`, `QuotaInfo`, `BridgeSnapshot`, `QuickPrompt`). |
| [`data/BridgeStateParser.kt`](../app/src/main/java/com/antigravity/wrapper/data/BridgeStateParser.kt) | JSON-to-Kotlin deserialization plus model `(Tier)` normalization and placeholder title filtering. |
| [`data/AppRepository.kt`](../app/src/main/java/com/antigravity/wrapper/data/AppRepository.kt) | Jetpack DataStore preferences (`antigravity_native_prefs`) and Quick Prompts persistence via [`RecordCodec`](../app/src/main/java/com/antigravity/wrapper/data/RecordCodec.kt). |
| [`ui/state/ChatViewModel.kt`](../app/src/main/java/com/antigravity/wrapper/ui/state/ChatViewModel.kt) | Holds `ChatUiState`, optimistic send/model/switch states, and sheet visibility flags. |
| [`ui/markdown/*`](../app/src/main/java/com/antigravity/wrapper/ui/markdown/NativeMarkdownView.kt) | Pure-Kotlin Markdown & LaTeX parsers, syntax highlighter, and Compose renderers. |
| [`web/UrlRouter.kt`](../app/src/main/java/com/antigravity/wrapper/web/UrlRouter.kt) | Host allowlist for in-app navigation vs Chrome Custom Tabs vs system `intent://` handling. |
| [`web/UserAgentSanitizer.kt`](../app/src/main/java/com/antigravity/wrapper/web/UserAgentSanitizer.kt) | Strips Android `wv` WebView tokens from the UA string to avoid Google OAuth `disallowed_useragent` blocks. |
