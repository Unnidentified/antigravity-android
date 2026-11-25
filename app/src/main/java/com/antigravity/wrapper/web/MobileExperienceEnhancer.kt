package com.antigravity.wrapper.web

/**
 * Generates safe, non-destructive CSS/JS snippets that improve mobile touch feel,
 * prevent horizontal overflow, sync color-scheme, and insert Quick Prompts into
 * focused chat inputs.
 */
object MobileExperienceEnhancer {

    private const val STYLE_ELEMENT_ID = "antigravity-native-mobile-polish"

    fun buildMobilePolishScript(
        enabled: Boolean,
        darkTheme: Boolean,
        amoledBlack: Boolean
    ): String {
        if (!enabled) {
            return """
                (function() {
                    var el = document.getElementById('$STYLE_ELEMENT_ID');
                    if (el && el.parentNode) { el.parentNode.removeChild(el); }
                })();
            """.trimIndent()
        }

        val scheme = if (darkTheme) "dark" else "light"
        val bgOverride = if (amoledBlack) {
            "html, body { background-color: #000000 !important; }"
        } else {
            ""
        }

        val css = """
            :root { color-scheme: $scheme; }
            * {
                -webkit-tap-highlight-color: transparent !important;
                -webkit-touch-callout: default;
            }
            html, body {
                max-width: 100vw !important;
                overflow-x: clip !important;
                overscroll-behavior-y: contain;
                -webkit-font-smoothing: antialiased;
                text-rendering: optimizeLegibility;
            }
            $bgOverride
            pre, code, table {
                max-width: 100% !important;
                overflow-x: auto !important;
                -webkit-overflow-scrolling: touch;
            }
            ::-webkit-scrollbar {
                width: 4px;
                height: 4px;
            }
            ::-webkit-scrollbar-thumb {
                background: rgba(168, 199, 250, 0.32);
                border-radius: 999px;
            }
            ::-webkit-scrollbar-track {
                background: transparent;
            }
        """.trimIndent()

        val escapedCss = escapeForJsString(css)
        return """
            (function() {
                try {
                    var style = document.getElementById('$STYLE_ELEMENT_ID');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = '$STYLE_ELEMENT_ID';
                        (document.head || document.documentElement).appendChild(style);
                    }
                    style.textContent = "$escapedCss";
                } catch (e) {}
            })();
        """.trimIndent()
    }

    fun buildInsertPromptScript(promptText: String): String {
        val escaped = escapeForJsString(promptText)
        return """
            (function() {
                try {
                    var text = "$escaped";
                    var target = document.activeElement;
                    var isEditable = function(el) {
                        if (!el) return false;
                        var tag = (el.tagName || '').toLowerCase();
                        if (tag === 'textarea') return !el.disabled && !el.readOnly;
                        if (tag === 'input') {
                            var type = (el.type || 'text').toLowerCase();
                            return (type === 'text' || type === 'search') && !el.disabled && !el.readOnly;
                        }
                        return el.isContentEditable === true;
                    };
                    if (!isEditable(target)) {
                        var candidates = document.querySelectorAll('textarea, [contenteditable="true"], input[type="text"]');
                        for (var i = candidates.length - 1; i >= 0; i--) {
                            var rect = candidates[i].getBoundingClientRect();
                            if (rect.width > 0 && rect.height > 0 && isEditable(candidates[i])) {
                                target = candidates[i];
                                break;
                            }
                        }
                    }
                    if (!isEditable(target)) {
                        return false;
                    }
                    target.focus();
                    var tag = (target.tagName || '').toLowerCase();
                    if (tag === 'textarea' || tag === 'input') {
                        var proto = tag === 'textarea' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                        var descriptor = Object.getOwnPropertyDescriptor(proto, 'value');
                        var current = target.value || '';
                        var next = current.length > 0 ? (current + '\n' + text) : text;
                        if (descriptor && descriptor.set) {
                            descriptor.set.call(target, next);
                        } else {
                            target.value = next;
                        }
                        target.dispatchEvent(new Event('input', { bubbles: true }));
                        target.dispatchEvent(new Event('change', { bubbles: true }));
                        return true;
                    }
                    if (target.isContentEditable) {
                        document.execCommand('insertText', false, text);
                        target.dispatchEvent(new Event('input', { bubbles: true }));
                        return true;
                    }
                    return false;
                } catch (e) {
                    return false;
                }
            })();
        """.trimIndent()
    }

    fun escapeForJsString(raw: String): String {
        val sb = StringBuilder(raw.length + 16)
        for (ch in raw) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\u2028' -> sb.append("\\u2028")
                '\u2029' -> sb.append("\\u2029")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
