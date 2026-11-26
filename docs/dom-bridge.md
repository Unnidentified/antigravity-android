# DOM Bridge & Selectors

All web automation lives inside [`AntigravityBridgeScript.kt`](../app/src/main/java/com/antigravity/wrapper/bridge/AntigravityBridgeScript.kt) (`BRIDGE_VERSION = 7`) paired with [`AntigravityBridgeEngine.kt`](../app/src/main/java/com/antigravity/wrapper/bridge/AntigravityBridgeEngine.kt).

When updating selectors, increment `BRIDGE_VERSION` so re-injection cleanly replaces `window.__agBridge` without requiring a cold page reload.

## Mobile DOM Root & Chrome Exclusion

- **Primary root**: `[data-testid="mobile-layout"]` inside `ga-remote-agents`. Falls back to `ga-remote-agents`, `main#main-content`, then `document.body`.
- **Excluded chrome**: `isGoogleBar(el)` skips `#gb`, `.boqOnegooglelite*`, and `.header-feedback-btn`.
- **404 auto-recovery**: If `#af-error-container` renders (e.g., an unintended navigation to `/customizations`), `buildBootstrapScript()` and `syncNow()` immediately `window.location.replace('https://antigravity.google.com/remote')`.

## Calibrated `data-testid` Reference

Observed on `https://antigravity.google.com/remote` (home & open-thread views):

### Navigation & Sidebar
| Selector | Purpose | Notes |
| --- | --- | --- |
| `[data-testid="conversation-row-sidebar"]` | Conversation row | Read title from `a[aria-label]` rather than `textContent` so trailing relative timestamps (`5m`, `2h`) aren't concatenated. |
| `[data-testid="mobile-back-to-home"]` | Back button in open thread | Tapped automatically by `selectConversation()` when switching threads because the sidebar list is unmounted inside an open thread. |
| `[data-testid="breadcrumb-segment"]` | Active thread title | Authoritative top-bar title when inside `/remote/<id>`. |
| `[data-testid="project-selector-trigger"]` | Active project trigger | Parse `aria-label` after `current:`; ignore `"No Project"`. |
| `button[aria-label="New Conversation"]` | New chat trigger | Falls back to `[data-testid="mobile-back-to-home"]`, then `/remote`. |

### Open Thread & Turns
| Selector | Purpose | Notes |
| --- | --- | --- |
| `[data-testid="conversation-view"]` | Messages container | Empty array returned unless visible. |
| `[data-testid="user-message"]` | User turn wrapper | Contains `.sr-only` `"You said"`; read Markdown strictly from `[data-testid="user-message-content"]`. |
| `[data-testid="agent-response"]` | Assistant turn wrapper | Contains `.sr-only` `"Agent said"`. |
| `[data-testid="worked-for-collapsible"]` | Reasoning/thought accordion | Extracted into `ReasoningBlock[]` and excluded from main Markdown body. |
| `[data-testid="entity-pill"]` | Inline file/symbol/PR badge | Converted to `` `label` `` or `[label](href)` before stripping `<button>` elements, preventing empty `(, , )` artifacts. |
| `[data-testid="artifact-card"]` | Artifact preview card | Title read from `[data-testid="artifact-card-header"]`. |
| `[data-testid="changed-files-card"]` | Diff summary card | Parses `<N> files changed` + `+added -removed` counts. |
| Ignored containers | Non-body chrome | `.sr-only`, `worked-for-collapsible`, `turn-cards-container`, `artifact-card`, `changed-files-card`, `cascade-system-message-toolbar`, `user-message-actions`, `revert-button`, `fieldset[aria-label="Response actions"]`. |

### Composer, Model Selector & View Usage
| Selector | Purpose | Notes |
| --- | --- | --- |
| `[data-testid="agent-input-box"] [aria-label="Message input"]` | Lexical/ProseMirror composer | Contenteditable textbox (plus `textarea`/`[role="textbox"]` fallback). |
| `[data-testid="send-button"]` / `button[aria-label="Send message"]` | Submit button | Enabled asynchronously after editor state updates. |
| `button[aria-label="Add context"]` | Attachment trigger | Prefer `input[type="file"]` if mounted. |
| `[data-testid="model-selector-trigger"]` | Current model button | Parse `aria-label` after `current:`. |
| `[data-testid="bottom-sheet"]`, `[role="dialog"]` | Modal portal | Dismissed via `[data-testid="bottom-sheet-backdrop"]` or `Escape`. |
| `[data-testid="model-selector-panel"]` | Model list pane (Pane 1) | **Stays mounted in the DOM** inside the bottom sheet even after sliding to Thinking Effort or View Usage. Never scrape `model-selector-panel` directly for quota. |
| `[data-testid="model-selector-effort-group"]` | Effort-configurable model row | Clicking slides to Pane 2 containing visible `[data-testid="model-selector-effort-option"]` (`Low`, `Medium`, `High`, `Extra`). |
| `button[data-testid="model-selector-item"][role="menuitem"]` | Direct model row | Single-step models (e.g., `GPT-OSS 120B (Medium)`). |
| `button.agy-overlay-item` matching `/view usage/i` | View Usage trigger | Inside `model-selector-panel`; opens sibling/nested slide pane or dialog with usage stats. |

## Prompt Send Pipeline

Untrusted JS `paste` / `input` events alone often fail to activate Lexical/ProseMirror or `[data-testid="send-button"]` when the `WebView` is unfocused. `sendPrompt()` therefore executes a multi-stage trusted Android handshake:

1. `AntigravityBridgeEngine.sendPrompt()` temporarily sets `webView.isFocusable = true`, calls `requestFocus()`, and invokes `window.__agBridge.focusComposer()`.
2. `focusComposer()` scrolls the composer into view, dispatches both synthetic `simulateTap()` and a real `MotionEvent` via `AntigravityNative.dispatchTrustedTap(normX, normY)`, calls `input.focus()`, and sets a DOM selection range.
3. After `130ms`, Android commits text through `view.onCreateInputConnection(EditorInfo())?.commitText(text, 1)`.
4. `submitPromptAfterCommit(text)` runs on JS:
   - If the composer is still empty, inserts via `document.execCommand('insertText')`, then `ClipboardEvent('paste')`, then `InputEvent('beforeinput')` + `'input'`.
   - Polls up to `900ms` for `[data-testid="send-button"]` to enable.
   - Clicks via trusted Android `MotionEvent` (`dispatchTrustedTap`), falling back to `simulateTap`, then trusted `KeyEvent.KEYCODE_ENTER` (`AntigravityNative.dispatchTrustedEnter()`).
   - Starts `startStreamingHeartbeat()` (600ms interval up to ~35s, resetting while `detectStreaming()` is true) so replies stream live even if DOM mutations are batched.
   - Reports completion to `AntigravityNative.onPromptSendResult(ok, reason)`, which triggers `restoreFocusLock()`.

## JS-to-Native Interfaces

### Exposed on `window.AntigravityNative`
- `onStateSnapshot(json: String)`
- `dispatchTrustedTap(normX: Double, normY: Double)`
- `dispatchTrustedEnter()`
- `onPromptSendResult(ok: Boolean, reason: String)`
- `onBridgeLog(message: String)`

### Exposed on `window.__agBridge`
- `version` (`7`), `syncNow()`, `focusComposer()`, `submitPromptAfterCommit(text)`, `sendPrompt(text)`, `stopGeneration()`, `refreshModels()`, `selectModel(modelName, effortTier)`, `refreshQuota()`, `startNewChat()`, `selectConversation(url, title)`, `triggerFileUpload()`, `dumpDomSnapshot()`

## Calibrating Selectors from a Device

Go to **Settings -> Developer Diagnostics**:
1. Tap **Copy Sanitized DOM Snapshot** to run `dumpDomSnapshot()`. Emails are redacted to `[email]`, wrapper divs are flattened, and open `[data-testid="bottom-sheet"]` / `[role="dialog"]` trees, `testIds`, and `ariaLabels` are exported cleanly.
2. Check the compact diagnostic line (`turns=..., chats=..., models=..., composer=..., usage=...`) for quota probe telemetry (`usage=ok(...)`, `usage=no-view-btn`, `usage=parse-empty`, or `usage=miss(...)`).
3. Toggle **Show underlying session** to visually inspect the live Chromium view and return to native mode at any time.
