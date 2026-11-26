# Rendering & Performance

All assistant responses, user messages, reasoning blocks, code snippets, pipe tables, and LaTeX equations render natively in Compose without any embedded `WebView`.

## Pipeline Overview

```text
DOM HTML (nodeToMarkdown)
   │  strips .sr-only, excluded toolbars, & reasoning/artifact subtrees
   │  preserves entity-pills, KaTeX annotations, <pre>/<code>, tables
   ▼
Normalized Markdown String (normalizeMarkdown)
   ▼
MarkdownParser.parse(markdown)      ──► 64-entry synchronized LRU cache (entries <= 24 KB)
   │  returns List<MarkdownBlock>
   ▼
NativeMarkdownView
   ├── InlineRichText + AnnotatedString (bold, italic, strikethrough, code, links, inline math)
   ├── LatexParser.parse(latex) -> RenderLatexNode (fractions, roots, scripts, accents, matrices)
   ├── CodeSyntaxHighlighter.highlight(code, isDark) (skipped cleanly above 4,000 chars)
   └── NativeMarkdownTable (horizontalScroll)
```

## DOM-to-Markdown Conversion Rules

Implemented in [`AntigravityBridgeScript.kt`](../app/src/main/java/com/antigravity/wrapper/bridge/AntigravityBridgeScript.kt) (`nodeToMarkdown` & `normalizeMarkdown`):

- **Math preservation**: Detects `.katex-display` / `mjx-container[display="true"]` and `.katex` / `mjx-container`, extracting the underlying TeX from `annotation[encoding="application/x-tex"]`, `script[type*="math/tex"]`, or short `aria-label` attributes before emitting `$$...$$` or `$...$`.
- **Entity pills**: `[data-testid="entity-pill"]` must be handled *before* the generic `tag === 'button'` filter (`length <= 24`) strips UI action buttons, otherwise inline code references turn into empty commas like `(, , )`.
- **Empty punctuation cleanup**: `normalizeMarkdown` strips leftover `You said` / `Agent said` prefixes, collapses extra horizontal spaces, tightens punctuation spacing, and strips empty `(,)` artifacts.
- **No synthetic fallback reasoning/cards**: `extractReasoningBlocks` and `extractArtifacts` emit only items actually present in the DOM.

## Markdown & LaTeX Engines

### [`MarkdownParser`](../app/src/main/java/com/antigravity/wrapper/ui/markdown/MarkdownParser.kt)
- **Block types**: `Heading` (H1–H6), `Paragraph`, `CodeBlock`, `MathBlock` (`$$...$$` and `\[...\]`), `BlockQuote`, `BulletList`, `OrderedList`, `TableBlock`, `HorizontalRule`.
- **Inline types**: `Text`, `Bold`, `Italic`, `Strikethrough`, `InlineCode`, `InlineMath` (`$...$` with currency-safety check and `\(...\)`), `Link`.
- **Caching**: A `64`-entry LRU `LinkedHashMap` prevents re-parsing identical message bodies on every streaming tick or scroll recomposition.

### [`LatexParser`](../app/src/main/java/com/antigravity/wrapper/ui/markdown/LatexParser.kt) & [`NativeMarkdownView`](../app/src/main/java/com/antigravity/wrapper/ui/markdown/NativeMarkdownView.kt)
- Pure-Kotlin recursive-descent parser outputting `LatexNode` (`Row`, `Text`, `Fraction`, `Sqrt`, `Script`, `Matrix`, `OverAccent`).
- Supports `\frac`/`\dfrac`/`\tfrac`/`\cfrac`, `\sqrt[n]{...}`, `_`/`^`, `\left`/`\right` delimiters, `\begin{pmatrix|bmatrix|Bmatrix|vmatrix|Vmatrix|cases}`, `\mathbb`, `\mathbf`, `\text`/`\mathrm`/`\operatorname`, accents (`\hat`, `\bar`, `\vec`, `\tilde`, `\dot`), and Greek/calculus/logic/arrow symbols.
- Large operators (`∑`, `∏`, `∫`, `∬`, `∮`, `lim`) render stacked limits above/below the operator inside `NativeLatexDisplayCard`.

### [`CodeSyntaxHighlighter`](../app/src/main/java/com/antigravity/wrapper/ui/markdown/CodeSyntaxHighlighter.kt)
- Lightweight single-pass tokenizer highlighting comments, strings, `@Annotations`, numbers, Kotlin/Python/JS/Rust/SQL keywords, and `PascalCase` type identifiers with light/dark palettes.
- Skips highlighting when `code.length > MAX_HIGHLIGHT_LENGTH` (`4000`) to avoid large `AnnotatedString` allocation jank during fast `LazyColumn` flings.

## Scroll & Animation Guidelines

- **Thread opening**: `AntigravityNativeApp` includes a trailing `item(key = "bottom_anchor")` and scrolls straight to `displayedMessages.size` whenever `currentUrl` or `displayedMessages.size` changes, so threads land cleanly at the latest turn.
- **Scroll-to-bottom FAB**: Driven directly by `remember { derivedStateOf { displayedMessages.isNotEmpty() && listState.canScrollForward } }` (independent of DOM scroll state).
- **Avoid `attributes: true` on `MutationObserver`**: Only observe `{ childList: true, subtree: true, characterData: true }` in the DOM bridge; observing CSS/attribute ticks floods `scheduleSync` during idle animations and stalls drawer/list frames.
- **Reasoning cards**: Default collapsed unless `settings.autoExpandReasoning` or `block.isStreaming` is true; user overrides persist per-session in `ChatUiState.expandedReasoningIds`.
