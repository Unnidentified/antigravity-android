package com.antigravity.wrapper.bridge

/**
 * Calibrated headless JS bridge using Antigravity's exact mobile DOM `data-testid`s:
 * - Navigation: `mobile-back-to-home`, `breadcrumb-segment`, `conversation-row-sidebar`
 * - Messages: `conversation-view`, `user-message` / `user-message-content`, `agent-response`,
 *   `worked-for-collapsible`, `entity-pill`, `artifact-card`, `changed-files-card`
 * - Composer: `agent-input-box`, `[aria-label="Message input"]`, `send-button`
 * - Models & Usage: `model-selector-trigger`, `model-selector-panel`, `model-selector-effort-group`,
 *   `model-selector-effort-option`, `model-selector-item`, and sibling View Usage pane
 */
object AntigravityBridgeScript {

    fun buildBootstrapScript(): String = """
        (function() {
          if (document.getElementById('af-error-container')) {
            window.location.replace('https://antigravity.google.com/remote');
            return;
          }
          const BRIDGE_VERSION = 19;
          if (window.__agBridge && window.__agBridge.version === BRIDGE_VERSION) {
            window.__agBridge.syncNow();
            return;
          }

          const store = window.__agBridgeStore || (window.__agBridgeStore = {
            models: [],
            effortMap: {},
            projects: [],
            conversations: [],
            quota: {
              usedPercent: null,
              leftPercent: null,
              fiveHourLeftPercent: null,
              fiveHourResetLabel: '',
              weeklyLeftPercent: null,
              weeklyResetLabel: '',
              limits: [],
              remainingLabel: '',
              resetLabel: '',
              planLabel: 'Antigravity',
              rawSummary: '',
              isLive: false
            },
            lastQuotaDiag: '',
            modelMutationHoldUntil: 0
          });
          let cachedModels = store.models;
          let cachedEffortMap = store.effortMap;
          let cachedProjects = store.projects;
          let cachedConversations = store.conversations;
          let cachedQuota = store.quota;
          let syncTimer = null;
          let streamingHeartbeatTimer = null;
          let streamingHeartbeatTicks = 0;
          let lastQuotaDiag = store.lastQuotaDiag || '';
          let lastPayloadHash = '';
          let isInternalModalProbe = false;
          let modelsPrewarmed = store.models.length > 0;
          let quotaPrewarmed = !!store.quota.isLive;

          function saveStore() {
            store.models = cachedModels;
            store.effortMap = cachedEffortMap;
            store.projects = cachedProjects;
            store.conversations = cachedConversations;
            store.quota = cachedQuota;
            store.lastQuotaDiag = lastQuotaDiag;
          }

          function fastHash(str) {
            let h = 2166136261;
            for (let i = 0; i < str.length; i++) {
              h ^= str.charCodeAt(i);
              h = Math.imul(h, 16777619);
            }
            return (h >>> 0).toString(36);
          }

          function getAppRoot() {
            return document.querySelector('[data-testid="mobile-layout"]') ||
              document.querySelector('ga-remote-agents') ||
              document.querySelector('main#main-content') ||
              document.body;
          }

          function isVisible(el) {
            if (!el || el.nodeType !== 1) return false;
            const style = window.getComputedStyle(el);
            if (!style || style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
              return false;
            }
            const rect = el.getBoundingClientRect();
            return rect.width > 0 || rect.height > 0;
          }

          function isInViewport(el) {
            if (!isVisible(el)) return false;
            const rect = el.getBoundingClientRect();
            return rect.width > 0 && rect.height > 0 && rect.right > 4 && rect.left < (window.innerWidth - 4);
          }

          function isScreenReaderOnly(el) {
            if (!el || el.nodeType !== 1) return false;
            const cls = typeof el.className === 'string' ? el.className : '';
            return /\bsr-only\b|\bvisually-hidden\b|\bscreen-reader-text\b/i.test(cls);
          }

          function isGoogleBar(el) {
            if (!el || el.nodeType !== 1) return false;
            const cls = typeof el.className === 'string' ? el.className : '';
            return el.id === 'gb' || cls.includes('boqOnegooglelite') || cls.includes('header-feedback-btn');
          }

          function collectDeep(root, predicate, limit) {
            const out = [];
            const max = limit || 1600;
            const stack = [root || getAppRoot()];
            while (stack.length > 0 && out.length < max) {
              const cur = stack.pop();
              if (!cur) continue;
              if (cur.nodeType === 1) {
                if (isGoogleBar(cur)) continue;
                try {
                  if (predicate(cur)) out.push(cur);
                } catch (_) {}
                if (cur.shadowRoot) {
                  stack.push(cur.shadowRoot);
                }
              }
              const children = cur.children || cur.childNodes;
              if (children) {
                for (let i = children.length - 1; i >= 0; i--) {
                  if (children[i].nodeType === 1) stack.push(children[i]);
                }
              }
            }
            return out;
          }

          function cleanText(str) {
            return (str || '').replace(/\u00a0/g, ' ').replace(/[ \t]+/g, ' ').trim();
          }

          function simulateTap(el) {
            if (!el) return false;
            try {
              const rect = el.getBoundingClientRect();
              const cx = rect.left + rect.width / 2;
              const cy = rect.top + rect.height / 2;
              const common = {
                bubbles: true,
                cancelable: true,
                composed: true,
                view: window,
                clientX: cx,
                clientY: cy,
                button: 0,
                buttons: 1
              };
              if (typeof PointerEvent !== 'undefined') {
                el.dispatchEvent(new PointerEvent('pointerdown', Object.assign({}, common, { pointerId: 1, pointerType: 'touch', isPrimary: true })));
              }
              el.dispatchEvent(new MouseEvent('mousedown', common));
              if (typeof PointerEvent !== 'undefined') {
                el.dispatchEvent(new PointerEvent('pointerup', Object.assign({}, common, { buttons: 0, pointerId: 1, pointerType: 'touch', isPrimary: true })));
              }
              el.dispatchEvent(new MouseEvent('mouseup', Object.assign({}, common, { buttons: 0 })));
              el.click();
              return true;
            } catch (_) {
              try { el.click(); return true; } catch (__) { return false; }
            }
          }

          function dispatchNativeTap(el) {
            if (!el) return false;
            try {
              if (!window.AntigravityNative || !window.AntigravityNative.dispatchTrustedTap) return false;
              const rect = el.getBoundingClientRect();
              if (rect.width <= 0 || rect.height <= 0 || window.innerWidth <= 0 || window.innerHeight <= 0) return false;
              const normX = (rect.left + rect.width / 2) / window.innerWidth;
              const normY = (rect.top + rect.height / 2) / window.innerHeight;
              if (normX <= 0 || normX >= 1 || normY <= 0 || normY >= 1) return false;
              window.AntigravityNative.dispatchTrustedTap(normX, normY);
              return true;
            } catch (_) {
              return false;
            }
          }

          function waitFor(predicate, timeoutMs, intervalMs) {
            return new Promise(resolve => {
              const start = Date.now();
              const step = intervalMs || 50;
              function check() {
                try {
                  const res = predicate();
                  if (res) { resolve(res); return; }
                } catch (_) {}
                if (Date.now() - start >= (timeoutMs || 1200)) {
                  resolve(null);
                  return;
                }
                setTimeout(check, step);
              }
              check();
            });
          }

          function sleep(ms) {
            return new Promise(resolve => setTimeout(resolve, ms));
          }

          function extractTexFromMathNode(el) {
            const ann = el.querySelector && el.querySelector('annotation[encoding="application/x-tex"], annotation');
            if (ann && ann.textContent) return ann.textContent.trim();
            const script = el.querySelector && el.querySelector('script[type*="math/tex"]');
            if (script && script.textContent) return script.textContent.trim();
            const aria = el.getAttribute && el.getAttribute('aria-label');
            if (aria && aria.length > 0 && aria.length < 300) return aria.trim();
            return cleanText(el.textContent);
          }

          function isReasoningElement(el) {
            if (!el || el.nodeType !== 1) return false;
            const tid = (el.getAttribute('data-testid') || '').toLowerCase();
            if (tid === 'worked-for-collapsible' || tid.includes('thought') || tid.includes('reason')) return true;
            const tag = (el.tagName || '').toLowerCase();
            if (tag === 'details') return true;
            const cls = (typeof el.className === 'string' ? el.className : '').toLowerCase();
            return cls.includes('thought') || cls.includes('thinking') || cls.includes('reasoning');
          }

          function isExcludedFromMarkdown(el) {
            if (!el || el.nodeType !== 1) return false;
            if (isScreenReaderOnly(el)) return true;
            const tid = (el.getAttribute('data-testid') || '').toLowerCase();
            if (tid === 'worked-for-collapsible' ||
                tid === 'turn-cards-container' ||
                tid === 'artifact-card' ||
                tid === 'changed-files-card' ||
                tid === 'cascade-system-message-toolbar' ||
                tid === 'user-message-actions' ||
                tid === 'revert-button' ||
                tid === 'code-block-preview-toggle' ||
                tid === 'code-block-preview-button' ||
                tid === 'code-block-raw-button') {
              return true;
            }
            const tag = (el.tagName || '').toLowerCase();
            if (tag === 'fieldset' && /response actions/i.test(el.getAttribute('aria-label') || '')) return true;
            return false;
          }

          function isRelativeTimestampText(txt) {
            return /^(\d+\s*(?:s|sec|secs|m|min|mins|h|hr|hrs|d|w)\s*ago|\d+\s*[smhdw]|just now|now)$/i.test(txt);
          }

          function extractCodeText(el) {
            if (!el) return '';
            const raw = (el.textContent || '');
            // Walk block/line elements so syntax-highlighted spans/divs/br preserve newlines & indentation
            function walkCode(node) {
              if (!node) return '';
              if (node.nodeType === 3) return node.nodeValue || '';
              if (node.nodeType !== 1) return '';
              const t = (node.tagName || '').toLowerCase();
              if (t === 'button' || t === 'svg' || t === 'style' || t === 'script') return '';
              if (t === 'br') return '\n';
              const cls = (typeof node.className === 'string' ? node.className : '').toLowerCase();
              // Skip embedded code-block header/toolbar (language badge + copy button)
              if (cls.includes('code-header') || cls.includes('toolbar') || node.getAttribute('data-testid') === 'code-block-header') {
                return '';
              }
              let inner = '';
              const children = node.childNodes;
              for (let i = 0; i < children.length; i++) {
                inner += walkCode(children[i]);
              }
              const isLineBlock = t === 'div' || t === 'p' || t === 'li' || t === 'tr' ||
                (t === 'span' && /\bline\b|\bcode-line\b/.test(cls));
              if (isLineBlock && inner.length > 0 && !inner.endsWith('\n')) {
                return inner + '\n';
              }
              return inner;
            }
            const walked = walkCode(el).replace(/\u00a0/g, ' ').replace(/^\n+|\n+$/g, '');
            if (walked.includes('\n')) return walked;
            if (el.isConnected && el.innerText) {
              const inner = el.innerText.replace(/\u00a0/g, ' ').replace(/^\n+|\n+$/g, '');
              if (inner.includes('\n')) return inner;
            }
            return raw.replace(/\u00a0/g, ' ').replace(/^\n+|\n+$/g, '');
          }

          function isUiActionButton(node, label) {
            const lower = (label || '').toLowerCase();
            if (!lower) return true;
            const aria = (node.getAttribute('aria-label') || '').toLowerCase();
            const combined = lower + ' ' + aria;
            if (/^(copy|copied|copy code|copy latex|share|edit|revert|retry|regenerate|open|review|expand|collapse|stop|cancel|more actions|send feedback|good response|bad response|thumbs up|thumbs down)\b/i.test(lower)) {
              return true;
            }
            if (/copy|share|revert|feedback|thumb|expand reasoning|collapse reasoning|worked for/i.test(combined)) {
              return true;
            }
            if (node.closest && node.closest('[role="toolbar"], [data-testid="user-message-actions"], [data-testid="cascade-system-message-toolbar"]')) {
              return true;
            }
            return false;
          }

          function nodeToMarkdown(node, depth) {
            if (!node) return '';
            if (depth > 48) return '';
            if (node.nodeType === 3) return node.nodeValue || '';
            if (node.nodeType !== 1) return '';

            if (isExcludedFromMarkdown(node) || isReasoningElement(node)) return '';

            const tag = (node.tagName || '').toLowerCase();
            if (tag === 'script' || tag === 'style' || tag === 'svg' || tag === 'noscript') return '';
            if (node.getAttribute('aria-hidden') === 'true' && !node.classList.contains('katex')) return '';
            // Strip Antigravity numeric citation badges / superscript footnote markers in non-math prose
            if (tag === 'sup' && !node.closest('.katex, mjx-container')) {
              const supTxt = cleanText(node.textContent);
              if (!supTxt || /^[\d,\s\[\]()]+$/.test(supTxt)) return '';
            }

            // Preserve Antigravity entity pills (files, symbols, PRs) cleanly and make document/file pills tappable!
            const tid = (node.getAttribute('data-testid') || '').toLowerCase();
            if (tid === 'markdown-code-block-preview') {
              const innerMd = Array.from(node.childNodes).map(c => nodeToMarkdown(c, depth + 1)).join('').trim();
              if (!innerMd) return '';
              const quoted = innerMd.split('\n').map(l => '> ' + l).join('\n');
              return '\n\n' + quoted + '\n\n';
            }
            if (tid === 'entity-pill' || tid.includes('pill') || tid.includes('mention')) {
              const label = cleanText(node.textContent);
              if (!label) return '';
              const anchor = tag === 'a' ? node : (node.querySelector && node.querySelector('a'));
              const href = anchor ? (anchor.getAttribute('href') || '') : '';
              if (href && (href.startsWith('http://') || href.startsWith('https://'))) {
                return '[' + label + '](' + href + ')';
              }
              if (/[/.]/.test(label) && /\.(md|kt|kts|java|xml|yml|yaml|json|ts|tsx|js|py|gradle|txt|sh|properties)$/i.test(label)) {
                return '[' + label + '](ag-file://' + encodeURIComponent(label) + ')';
              }
              return '`' + label + '`';
            }

            if (tag === 'button') {
              const label = cleanText(node.textContent);
              if (isUiActionButton(node, label)) return '';
              if (label.length > 0 && label.length <= 64) {
                if (/[/.]/.test(label) && /\.(md|kt|kts|java|xml|yml|yaml|json|ts|tsx|js|py|gradle|txt|sh|properties)$/i.test(label)) {
                  return '[' + label + '](ag-file://' + encodeURIComponent(label) + ')';
                }
                return '`' + label + '`';
              }
              return '';
            }

            // Skip standalone relative timestamp chips (extracted to message header)
            if ((tag === 'time' || tag === 'span' || tag === 'div') && node.children.length === 0) {
              const leaf = cleanText(node.textContent);
              if (isRelativeTimestampText(leaf)) return '';
              if (/^(you said|agent said)$/i.test(leaf)) return '';
            }

            const cls = (typeof node.className === 'string' ? node.className : '').toLowerCase();
            if (cls.includes('katex-display') || (tag === 'mjx-container' && node.getAttribute('display') === 'true')) {
              const tex = extractTexFromMathNode(node);
              return tex ? ('\n\n$$\n' + tex + '\n$$\n\n') : '';
            }
            if (cls.includes('katex') || tag === 'mjx-container') {
              const tex = extractTexFromMathNode(node);
              return tex ? ('$' + tex + '$') : '';
            }

            if (tag === 'pre') {
              // Guard against Antigravity's rendered markdown preview containers inside <pre> (avoids truncating to 1st inline <code>)
              const mdPreview = node.querySelector('[data-testid="markdown-code-block-preview"]');
              if (mdPreview) {
                return nodeToMarkdown(mdPreview, depth + 1);
              }
              const hasRichBlocks = !!node.querySelector('ol, ul, h1, h2, h3, h4, table');
              const directCode = node.querySelector(':scope > code');
              const anyCode = directCode || node.querySelector('code');
              const nodeLen = cleanText(node.textContent).length;
              const codeLen = anyCode ? cleanText(anyCode.textContent).length : 0;
              if (hasRichBlocks && (!directCode || codeLen < nodeLen * 0.65)) {
                const richMd = Array.from(node.childNodes).map(c => nodeToMarkdown(c, depth + 1)).join('').trim();
                if (richMd) {
                  return '\n\n' + richMd.split('\n').map(l => '> ' + l).join('\n') + '\n\n';
                }
              }
              const codeEl = (directCode || (anyCode && codeLen >= nodeLen * 0.65)) ? anyCode : node;
              const codeCls = ((codeEl.className || '') + ' ' + (node.className || '')).toString();
              const langMatch = codeCls.match(/language-([a-zA-Z0-9_+-]+)/);
              let lang = (langMatch && langMatch[1]) ||
                codeEl.getAttribute('data-language') ||
                node.getAttribute('data-language') ||
                '';
              if (!lang && codeEl !== node) {
                // Check if <pre> contains a separate language badge sibling before <code>
                const preHeader = Array.from(node.children).find(ch => !ch.contains(codeEl) && ch !== codeEl);
                if (preHeader) {
                  const candidate = cleanText(preHeader.textContent).replace(/\bcopy\b/gi, '').trim();
                  if (/^[a-zA-Z0-9_+-]{1,20}$/.test(candidate)) lang = candidate;
                }
              }
              let rawCode = extractCodeText(codeEl);
              // Guard against a glued leading language label if a badge lived inside codeEl
              if (!lang && /^(kotlin|java|python|javascript|typescript|bash|sh|zsh|json|xml|yaml|sql|html|css|cpp|rust|go|swift|dart)\b/i.test(rawCode)) {
                const m = rawCode.match(/^(kotlin|java|python|javascript|typescript|bash|sh|zsh|json|xml|yaml|sql|html|css|cpp|rust|go|swift|dart)(?=\n|\s|$|[a-z]{2,})/i);
                if (m) {
                  const rest = rawCode.slice(m[1].length).replace(/^\n+/, '');
                  if (rest.length > 0 && (rawCode[m[1].length] === '\n' || /^[a-zA-Z({\[#@]/.test(rest))) {
                    lang = m[1].toLowerCase();
                    rawCode = rest;
                  }
                }
              } else if (lang && rawCode.toLowerCase().startsWith(lang.toLowerCase() + '\n')) {
                rawCode = rawCode.slice(lang.length + 1);
              }
              return '\n\n```' + lang + '\n' + rawCode + '\n```\n\n';
            }

            if (tag === 'code') {
              const txt = (node.textContent || '').replace(/`/g, "'").trim();
              return txt ? ('`' + txt + '`') : '';
            }

            if (tag === 'br') return '\n';
            if (tag === 'hr') return '\n\n---\n\n';

            const childrenMd = Array.from(node.childNodes)
              .map(c => nodeToMarkdown(c, depth + 1))
              .join('');

            switch (tag) {
              case 'h1': return '\n\n# ' + cleanText(childrenMd) + '\n\n';
              case 'h2': return '\n\n## ' + cleanText(childrenMd) + '\n\n';
              case 'h3': return '\n\n### ' + cleanText(childrenMd) + '\n\n';
              case 'h4': return '\n\n#### ' + cleanText(childrenMd) + '\n\n';
              case 'strong':
              case 'b': {
                const t = childrenMd.trim();
                return t ? ('**' + t + '**') : '';
              }
              case 'em':
              case 'i': {
                const t = childrenMd.trim();
                return t ? ('*' + t + '*') : '';
              }
              case 'del':
              case 's': {
                const t = childrenMd.trim();
                return t ? ('~~' + t + '~~') : '';
              }
              case 'a': {
                const href = node.getAttribute('href') || '';
                const label = cleanText(childrenMd) || href;
                if (href && (href.startsWith('http://') || href.startsWith('https://'))) {
                  return '[' + label + '](' + href + ')';
                }
                return label || '';
              }
              case 'p': {
                const t = childrenMd.trim();
                return t ? ('\n\n' + t + '\n\n') : '';
              }
              case 'blockquote': {
                const lines = childrenMd.trim().split('\n').map(l => '> ' + l).join('\n');
                return '\n\n' + lines + '\n\n';
              }
              case 'ul': {
                const listDepth = depth;
                const indent = node.parentElement && (node.parentElement.tagName || '').toLowerCase() === 'li' ? '  ' : '';
                const items = Array.from(node.children)
                  .filter(c => (c.tagName || '').toLowerCase() === 'li')
                  .map(li => {
                    const cb = li.querySelector && li.querySelector(':scope > input[type="checkbox"], :scope > p > input[type="checkbox"]');
                    const taskPrefix = cb ? (cb.checked ? '[x] ' : '[ ] ') : '';
                    const subLines = nodeToMarkdown(li, listDepth + 1).trim().split('\n')
                      .map((ln, idx) => idx === 0 ? ('- ' + taskPrefix + cleanText(ln)) : (indent + '  ' + ln.trimStart()));
                    return indent + subLines.join('\n');
                  })
                  .filter(s => s.trim().length > 2);
                return items.length ? ('\n\n' + items.join('\n') + '\n\n') : '';
              }
              case 'ol': {
                const indent = node.parentElement && (node.parentElement.tagName || '').toLowerCase() === 'li' ? '  ' : '';
                const items = Array.from(node.children)
                  .filter(c => (c.tagName || '').toLowerCase() === 'li')
                  .map((li, idx) => {
                    const subLines = nodeToMarkdown(li, depth + 1).trim().split('\n')
                      .map((ln, liIdx) => liIdx === 0 ? ((idx + 1) + '. ' + cleanText(ln)) : (indent + '  ' + ln.trimStart()));
                    return indent + subLines.join('\n');
                  })
                  .filter(s => s.trim().length > 3);
                return items.length ? ('\n\n' + items.join('\n') + '\n\n') : '';
              }
              case 'table': {
                const rows = Array.from(node.querySelectorAll('tr'));
                if (!rows.length) return '';
                const mdRows = rows.map(tr =>
                  Array.from(tr.children).map(cell => cleanText(nodeToMarkdown(cell, depth + 1)).replace(/\|/g, '\\|'))
                );
                const colCount = Math.max(...mdRows.map(r => r.length), 1);
                const header = '| ' + mdRows[0].join(' | ') + ' |';
                const sep = '| ' + Array(colCount).fill('---').join(' | ') + ' |';
                const body = mdRows.slice(1).map(r => '| ' + r.join(' | ') + ' |');
                return '\n\n' + [header, sep, ...body].join('\n') + '\n\n';
              }
              default:
                return childrenMd;
            }
          }

          function normalizeMarkdown(md) {
            const parts = (md || '').split(/(```[\s\S]*?```)/g);
            const normalizedParts = parts.map((part, idx) => {
              if (idx % 2 === 1) return part; // Preserve fenced code block whitespace & newlines verbatim
              return part
                .replace(/([^\n \t])[ \t]{2,}/g, '$1 ')
                // Strip stray Unicode superscript footnote numbers attached to words (e.g. physical¹)
                .replace(/([A-Za-z)\]`])[\u00B9\u00B2\u00B3\u2070\u2074-\u2079]+(?=\s|$|[.,;:!?)])/g, '$1')
                // Tighten spacing before punctuation & around parentheses/brackets (fixes "( Dark , AMOLED )")
                .replace(/[ \t]+([.,;:!?)\]])/g, '$1')
                .replace(/([(\[])[ \t]+/g, '$1')
                .replace(/\(\s*,\s*(?:,\s*)*\)/g, '')
                .replace(/\n{3,}/g, '\n\n');
            });
            return normalizedParts
              .join('')
              .replace(/^\s*(?:You said|Agent said)\s*/i, '')
              .trim();
          }

          function extractMessageTimestamp(container) {
            const timeEl = container.querySelector('time');
            if (timeEl) {
              const t = cleanText(timeEl.textContent);
              if (t && t.length <= 24) return t;
            }
            const leaves = container.querySelectorAll('span, div');
            for (let i = 0; i < Math.min(leaves.length, 60); i++) {
              const el = leaves[i];
              if (el.children.length > 0) continue;
              const t = cleanText(el.textContent);
              if (isRelativeTimestampText(t)) return t;
            }
            return '';
          }

          function extractReasoningBodyFromNode(el) {
            if (!el) return '';
            const tid = (el.getAttribute('data-testid') || '').toLowerCase();
            // Antigravity mounts step/thought rows inside el.nextElementSibling when aria-expanded="true"
            if (tid === 'worked-for-collapsible') {
              const sib = el.nextElementSibling;
              if (sib) {
                const stepRows = Array.from(sib.querySelectorAll('[data-testid="tool-group-collapsible"], [data-step-rows="true"] > div, p, li, blockquote, pre'))
                  .filter(node => {
                    const parentCollapsible = node.closest('[data-testid="tool-group-collapsible"]');
                    if (parentCollapsible && parentCollapsible !== node) return false;
                    return true;
                  });
                const lines = [];
                const seenLines = new Set();
                stepRows.forEach(row => {
                  const t = normalizeMarkdown(nodeToMarkdown(row, 0)) || cleanText(row.innerText || row.textContent);
                  if (!t || seenLines.has(t)) return;
                  seenLines.add(t);
                  if (t.includes('\n') || t.startsWith('- ') || t.startsWith('```')) {
                    lines.push(t);
                  } else {
                    lines.push('- ' + t);
                  }
                });
                if (lines.length > 0) return lines.join('\n');
                const fallbackTxt = normalizeMarkdown(nodeToMarkdown(sib, 0)) || cleanText(sib.textContent);
                if (fallbackTxt) return fallbackTxt;
              }
              return '';
            }
            const clone = el.cloneNode(true);
            const cloneSummary = clone.querySelector('summary, button, [role="button"]');
            if (cloneSummary && cloneSummary.parentNode) {
              cloneSummary.parentNode.removeChild(cloneSummary);
            }
            return normalizeMarkdown(nodeToMarkdown(clone, 0)) || cleanText(clone.textContent);
          }

          function extractReasoningBlocks(container, stableMsgId) {
            const blocks = [];
            const seen = new Set();
            const nodes = Array.from(container.querySelectorAll('[data-testid="worked-for-collapsible"], details'));
            nodes.forEach((el, idx) => {
              if (seen.has(el)) return;
              seen.add(el);

              const isBtn = (el.tagName || '').toLowerCase() === 'button';
              const summaryEl = isBtn ? el : el.querySelector('summary, button, [role="button"]');
              const rawSummary = summaryEl ? cleanText(summaryEl.textContent) : '';
              const durationMatch = rawSummary.match(/(\d+\s*(?:ms|s|sec|secs|seconds|m|min))/i);
              const duration = durationMatch ? durationMatch[1] : '';
              // Normalize title cleanly so "Worked for 1m" doesn't repeat "1m" in title + badge
              const title = /^worked\s+for\s+/i.test(rawSummary) ? 'Reasoning' : (rawSummary || 'Reasoning');
              const body = extractReasoningBodyFromNode(el);
              el.setAttribute('data-ag-reason-id', 'reason-' + stableMsgId + '-' + idx);

              blocks.push({
                id: 'reason-' + stableMsgId + '-' + idx,
                title: title,
                duration: duration,
                content: body,
                isStreaming: /thinking|working|reasoning\.\.\./i.test(rawSummary)
              });
            });
            return blocks;
          }

          function extractArtifacts(container, stableMsgId) {
            const artifacts = [];
            const artCards = Array.from(container.querySelectorAll('[data-testid="artifact-card"]'));
            artCards.forEach((card, idx) => {
              const anchor = card.matches('a[href]')
                ? card
                : (card.querySelector('a[href]') || card.closest('a[href]'));
              const url = (anchor && anchor.getAttribute('href')) ||
                card.getAttribute('data-url') ||
                card.getAttribute('data-href') ||
                '';
              const badgeBtn = card.querySelector('button');
              const lines = extractLinesFromNode(card)
                .filter(l => !/^(open|view|review)$/i.test(l));

              let title = '';
              let subtitle = '';

              if (lines.length >= 2) {
                title = lines[0].slice(0, 80);
                subtitle = lines.slice(1).join(' ').trim();
              } else {
                const badgeTxt = cleanText(badgeBtn ? badgeBtn.textContent : '');
                if (badgeTxt && badgeTxt.length <= 48) {
                  title = badgeTxt;
                  const full = lines[0] || cleanText(card.textContent);
                  subtitle = full.startsWith(badgeTxt)
                    ? full.slice(badgeTxt.length).trim()
                    : full.replace(badgeTxt, '').trim();
                } else if (lines.length === 1) {
                  title = lines[0].slice(0, 80);
                } else {
                  const header = card.querySelector('[data-testid="artifact-card-header"], h1, h2, h3, h4');
                  title = cleanText(header ? header.textContent : card.textContent).slice(0, 80);
                }
              }

              if (title) {
                artifacts.push({
                  id: 'art-' + stableMsgId + '-' + idx,
                  title: title,
                  subtitle: subtitle.slice(0, 160),
                  actionLabel: 'Open',
                  url: url,
                  addedLines: null,
                  removedLines: null
                });
              }
            });

            const diffCards = Array.from(container.querySelectorAll('[data-testid="changed-files-card"]'));
            diffCards.forEach((card, idx) => {
              const text = cleanText(card.textContent);
              const diffMatch = text.match(/(\d+\s+files?\s+changed).*?\+(\d+)\s*[-−](\d+)/i);
              if (diffMatch) {
                artifacts.push({
                  id: 'diff-' + stableMsgId + '-' + idx,
                  title: diffMatch[1],
                  subtitle: 'Code changes ready for review',
                  actionLabel: 'Review',
                  url: '',
                  addedLines: parseInt(diffMatch[2], 10),
                  removedLines: parseInt(diffMatch[3], 10)
                });
              }
            });
            return artifacts;
          }

          function findComposerInput() {
            const root = getAppRoot();
            const exact = root.querySelector('[data-testid="agent-input-box"] [aria-label="Message input"], [aria-label="Message input"]');
            if (exact && isVisible(exact)) return exact;
            const candidates = collectDeep(root, el => {
              const tag = (el.tagName || '').toLowerCase();
              if (tag === 'textarea') return !el.disabled && !el.readOnly && isVisible(el);
              if (el.isContentEditable && isVisible(el)) return true;
              if (el.getAttribute('role') === 'textbox' && isVisible(el)) return true;
              return false;
            }, 25);
            if (!candidates.length) return null;
            candidates.sort((a, b) => b.getBoundingClientRect().bottom - a.getBoundingClientRect().bottom);
            return candidates[0];
          }

          function findModelSelectorButton() {
            const root = getAppRoot();
            const exact = root.querySelector('[data-testid="model-selector-trigger"]');
            if (exact && isVisible(exact)) return exact;
            const byAria = root.querySelector('button[aria-label^="Select model"]');
            if (byAria && isVisible(byAria)) return byAria;
            return null;
          }

          function parseActiveModelName(btn) {
            if (!btn) return '';
            const aria = btn.getAttribute('aria-label') || '';
            const m = aria.match(/current:\s*(.+)$/i);
            const raw = (m && m[1] ? cleanText(m[1]) : cleanText(btn.textContent))
              .replace(/\.\s*Applies to messages sent after this turn\.?$/i, '')
              .trim();
            if (/^no model selected$|^select model$/i.test(raw)) return '';
            return raw;
          }

          function parseAriaCurrentValue(selector) {
            const el = getAppRoot().querySelector(selector);
            if (!el) return '';
            const aria = el.getAttribute('aria-label') || '';
            const m = aria.match(/current:\s*(.+)$/i);
            const val = m && m[1] ? cleanText(m[1]) : cleanText(el.textContent);
            if (/^no project$|^select project$/i.test(val)) return '';
            return val;
          }

          function dismissOverlays() {
            const backdrops = document.querySelectorAll('[data-testid="bottom-sheet-backdrop"]');
            backdrops.forEach(b => {
              if (isVisible(b)) simulateTap(b);
            });
            document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }));
          }

          function extractRelativeTime(row, cleanTitle) {
            const spans = Array.from(row.querySelectorAll('span, time'));
            for (const sp of spans) {
              const txt = cleanText(sp.textContent);
              if (!txt || txt === cleanTitle) continue;
              if (isRelativeTimestampText(txt)) return txt;
            }
            return '';
          }

          function extractConversations() {
            const root = getAppRoot();
            const rows = Array.from(root.querySelectorAll('[data-testid="conversation-row-sidebar"]'));
            if (rows.length === 0) {
              const breadcrumbEl = root.querySelector('[data-testid="breadcrumb-segment"]');
              const breadcrumbTitle = breadcrumbEl ? cleanText(breadcrumbEl.textContent) : '';
              cachedConversations = cachedConversations.map(c => {
                const activeByUrl = !!(c.url && c.url === window.location.href);
                const activeByTitle = !!(breadcrumbTitle && c.title === breadcrumbTitle);
                return Object.assign({}, c, { isActive: activeByUrl || activeByTitle });
              });
              return cachedConversations;
            }

            const result = [];
            const seen = new Set();
            rows.forEach((row, idx) => {
              const anchor = row.querySelector('a') || row.querySelector('button');
              if (!anchor) return;
              const ariaTitle = cleanText(anchor.getAttribute('aria-label'));
              const fallbackText = cleanText(anchor.textContent).replace(/(\d+\s*(?:s|m|h|d|w|mo|y))$/i, '').trim();
              const title = ariaTitle || fallbackText;
              if (!title || title.length < 2) return;

              const href = anchor.getAttribute('href') || '';
              let fullUrl = '';
              if (href && !href.startsWith('#')) {
                try { fullUrl = new URL(href, window.location.origin).href; } catch (_) {}
              }

              const updatedLabel = extractRelativeTime(row, title);
              const key = fullUrl || (title + '#' + idx);
              if (seen.has(key)) return;
              seen.add(key);

              const isActive = (fullUrl && fullUrl === window.location.href) ||
                anchor.getAttribute('aria-current') === 'page' ||
                row.getAttribute('data-active') === 'true';

              result.push({
                id: 'conv-' + idx + '-' + title.slice(0, 24),
                title: title,
                url: fullUrl,
                projectName: '',
                preview: '',
                updatedLabel: updatedLabel,
                isActive: isActive
              });
            });

            if (result.length > 0) {
              cachedConversations = result;
              saveStore();
            }
            return cachedConversations;
          }

          function extractMessages() {
            const root = getAppRoot();
            const convView = root.querySelector('[data-testid="conversation-view"]');
            if (!convView || !isVisible(convView)) {
              return [];
            }

            const turnNodes = Array.from(
              convView.querySelectorAll('[data-testid="user-message"], [data-testid="agent-response"]')
            );
            const rawMsgs = [];

            turnNodes.forEach((el) => {
              const tid = (el.getAttribute('data-testid') || '').toLowerCase();
              const isUser = tid === 'user-message';
              const timestamp = extractMessageTimestamp(el);

              if (isUser) {
                const contentEl = el.querySelector('[data-testid="user-message-content"]') || el;
                const md = normalizeMarkdown(nodeToMarkdown(contentEl, 0));
                if (!md) return;
                rawMsgs.push({ el, role: 'user', md, timestamp, baseHash: 'u-' + fastHash(md.slice(0, 160)) });
              } else {
                const md = normalizeMarkdown(nodeToMarkdown(el, 0));
                const reasoningCount = el.querySelectorAll('[data-testid="worked-for-collapsible"], details').length;
                const artCount = el.querySelectorAll('[data-testid="artifact-card"], [data-testid="changed-files-card"]').length;
                if (!md && reasoningCount === 0 && artCount === 0) return;
                const firstR = el.querySelector('[data-testid="worked-for-collapsible"], details');
                const sig = md ? md.slice(0, 160) : (firstR ? cleanText(firstR.textContent) : String(artCount));
                rawMsgs.push({ el, role: 'assistant', md, timestamp, baseHash: 'a-' + fastHash(sig) });
              }
            });

            // Assign prepend-invariant IDs by counting occurrences from the BOTTOM of the thread
            const bottomCounts = Object.create(null);
            const resolvedIds = new Array(rawMsgs.length);
            for (let i = rawMsgs.length - 1; i >= 0; i--) {
              const item = rawMsgs[i];
              const domId = item.el.getAttribute('data-message-id') || '';
              if (domId) {
                resolvedIds[i] = domId;
              } else {
                const occ = (bottomCounts[item.baseHash] || 0);
                bottomCounts[item.baseHash] = occ + 1;
                resolvedIds[i] = item.baseHash + '-' + occ;
              }
            }

            return rawMsgs.map((item, i) => {
              const id = resolvedIds[i];
              if (item.role === 'user') {
                return {
                  id: id,
                  role: 'user',
                  markdown: item.md,
                  timestamp: item.timestamp,
                  reasoning: [],
                  artifacts: [],
                  isStreaming: false
                };
              }
              return {
                id: id,
                role: 'assistant',
                markdown: item.md,
                timestamp: item.timestamp,
                reasoning: extractReasoningBlocks(item.el, id),
                artifacts: extractArtifacts(item.el, id),
                isStreaming: false
              };
            });
          }

          function defaultEffortsForModel(name) {
            const lower = (name || '').toLowerCase();
            if (lower.includes('neon') || lower.includes('hydrogen')) {
              return ['Low', 'Medium', 'High', 'Extra'];
            }
            return ['Low', 'Medium', 'High'];
          }

          function readModelsFromOpenPanel(activeModel) {
            const panel = document.querySelector('[data-testid="model-selector-panel"]');
            if (!panel) return [];
            const items = [];
            const seen = new Set();

            const buttons = Array.from(panel.querySelectorAll('button.agy-overlay-item'));
            buttons.forEach((btn, idx) => {
              const fullText = cleanText(btn.textContent);
              if (!fullText || /^view usage$/i.test(fullText)) return;

              const groupSpan = btn.querySelector('[data-testid="model-selector-effort-group"]');
              if (groupSpan) {
                const name = cleanText(groupSpan.textContent);
                if (!name || seen.has(name)) return;
                seen.add(name);
                const siblingSpans = Array.from(btn.querySelectorAll('span'))
                  .map(s => cleanText(s.textContent))
                  .filter(t => t && t !== name && /^(Low|Medium|High|Extra|Fast)$/i.test(t));
                const tier = siblingSpans[0] || '';
                const isSelected = activeModel.toLowerCase().startsWith(name.toLowerCase());
                const knownEfforts = cachedEffortMap[name.toLowerCase()] || defaultEffortsForModel(name);
                items.push({
                  id: 'effort-' + name.toLowerCase().replace(/[^a-z0-9]+/g, '-'),
                  name: name,
                  tier: tier,
                  description: '',
                  hasEffortLevels: true,
                  effortOptions: knownEfforts,
                  isSelected: isSelected
                });
                return;
              }

              if (btn.getAttribute('data-testid') === 'model-selector-item' || btn.getAttribute('role') === 'menuitem') {
                const name = fullText;
                if (!name || seen.has(name)) return;
                seen.add(name);
                const base = name.replace(/\s*\([^)]+\)\s*$/, '').toLowerCase();
                const isSelected = activeModel.toLowerCase().startsWith(base);
                items.push({
                  id: 'item-' + base.replace(/[^a-z0-9]+/g, '-'),
                  name: name,
                  tier: '',
                  description: '',
                  hasEffortLevels: false,
                  effortOptions: [],
                  isSelected: isSelected
                });
              }
            });

            return items;
          }

          function extractLinesFromNode(node) {
            if (!node) return [];
            if (node.isConnected && node.innerText) {
              return node.innerText
                .split('\n')
                .map(cleanText)
                .filter(Boolean);
            }
            const lines = [];
            const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT);
            let cur;
            while ((cur = walker.nextNode())) {
              const t = cleanText(cur.nodeValue);
              if (t) lines.push(t);
            }
            return lines;
          }

          function parseUsagePane(pane) {
            if (!pane) return null;
            const rawLines = extractLinesFromNode(pane)
              .filter(l => l.length > 0 && !/^(back|view usage|usage|quota|model|close|done)$/i.test(l));
            if (rawLines.length === 0) return null;

            const joined = rawLines.join(' • ');
            if (/gemini 4 neon.*gemini 4 carbon/i.test(joined) || /thinking effort/i.test(joined)) {
              return null;
            }

            const limits = [];
            if (/^\s*(refreshing|loading)\b/i.test(joined) && !/\d{1,3}\s*%/.test(joined)) {
              return null;
            }
            let currentGroup = '';
            let activeLimit = null;

            const isWindowLine = l => /^(?:5\s*h(?:our|r)?|weekly|hourly|daily|monthly|session)\s*limit\b/i.test(l) ||
              /\b(?:5\s*h(?:our|r)?|weekly)\s+limit\b/i.test(l);
            const extractPct = l => {
              const m = l.match(/(\d{1,3})\s*%\s*(left|remaining|used)?/i);
              if (!m) return null;
              const rawVal = Math.max(0, Math.min(100, parseFloat(m[1])));
              const kind = (m[2] || '').toLowerCase();
              return kind === 'used' ? (100 - rawVal) : rawVal;
            };
            const isResetLine = l => /^resets?\s+(?:in|at|on)\b/i.test(l);

            for (let i = 0; i < rawLines.length; i++) {
              const line = rawLines[i];

              if (isWindowLine(line)) {
                if (activeLimit) limits.push(activeLimit);
                const inlinePct = extractPct(line);
                const cleanWindow = /5\s*h/i.test(line)
                  ? '5h limit'
                  : (/weekly/i.test(line) ? 'Weekly limit' : line.replace(/\d{1,3}\s*%.*$/, '').trim());
                activeLimit = {
                  id: 'lim-' + limits.length + '-' + (currentGroup || 'all').toLowerCase().replace(/[^a-z0-9]+/g, '-'),
                  groupName: currentGroup || 'Antigravity',
                  windowLabel: cleanWindow || line,
                  leftPercent: inlinePct,
                  resetLabel: ''
                };
                continue;
              }

              const pctVal = extractPct(line);
              if (pctVal !== null && !isResetLine(line)) {
                if (activeLimit && activeLimit.leftPercent === null) {
                  activeLimit.leftPercent = pctVal;
                } else {
                  if (activeLimit) limits.push(activeLimit);
                  activeLimit = {
                    id: 'lim-' + limits.length + '-' + (currentGroup || 'all').toLowerCase().replace(/[^a-z0-9]+/g, '-'),
                    groupName: currentGroup || 'Antigravity',
                    windowLabel: /5\s*h/i.test(line) ? '5h limit' : (/weekly/i.test(line) ? 'Weekly limit' : 'Quota'),
                    leftPercent: pctVal,
                    resetLabel: ''
                  };
                }
                continue;
              }

              if (isResetLine(line)) {
                if (activeLimit) {
                  activeLimit.resetLabel = line;
                }
                continue;
              }

              // Likely a model-family group heading such as "Gemini Models" or "Claude and GPT models"
              if (line.length >= 3 && line.length <= 56 && !/(unlimited|available|credits)/i.test(line)) {
                if (activeLimit) {
                  limits.push(activeLimit);
                  activeLimit = null;
                }
                currentGroup = line;
              }
            }
            if (activeLimit) limits.push(activeLimit);

            // Pick active model's family group if available so top-level summary reflects the selected model
            const modelBtn = findModelSelectorButton();
            const activeModelLower = (parseActiveModelName(modelBtn) || '').toLowerCase();
            const isClaudeOrGpt = /claude|gpt|sonnet|opus|oss/i.test(activeModelLower);
            const preferredGroupLimits = limits.filter(l =>
              isClaudeOrGpt ? /claude|gpt/i.test(l.groupName) : /gemini/i.test(l.groupName)
            );
            const focusLimits = preferredGroupLimits.length > 0 ? preferredGroupLimits : limits;

            const fiveH = focusLimits.find(l => /5\s*h|hourly/i.test(l.windowLabel))
              || limits.find(l => /5\s*h|hourly/i.test(l.windowLabel))
              || null;
            const weekly = focusLimits.find(l => /weekly/i.test(l.windowLabel))
              || limits.find(l => /weekly/i.test(l.windowLabel))
              || null;

            let fallbackLeftPct = null;
            if (fiveH && fiveH.leftPercent !== null) {
              fallbackLeftPct = fiveH.leftPercent;
            } else if (weekly && weekly.leftPercent !== null) {
              fallbackLeftPct = weekly.leftPercent;
            } else if (limits.length > 0 && limits[0].leftPercent !== null) {
              fallbackLeftPct = limits[0].leftPercent;
            } else {
              const pb = pane.querySelector('[role="progressbar"], meter');
              if (pb) {
                const now = parseFloat(pb.getAttribute('aria-valuenow') || pb.value || '');
                const max = parseFloat(pb.getAttribute('aria-valuemax') || pb.max || '100');
                if (!isNaN(now) && max > 0) fallbackLeftPct = Math.round((now / max) * 100);
              }
              if (fallbackLeftPct === null) {
                fallbackLeftPct = extractPct(joined);
              }
            }

            const fiveHPct = fiveH ? fiveH.leftPercent : null;
            const weeklyPct = weekly ? weekly.leftPercent : null;
            const summaryParts = [];
            if (fiveHPct !== null) summaryParts.push('5h: ' + Math.round(fiveHPct) + '% left');
            if (weeklyPct !== null) summaryParts.push('Weekly: ' + Math.round(weeklyPct) + '% left');
            const summaryLine = summaryParts.length > 0
              ? summaryParts.join(' • ')
              : (rawLines.find(l => /(\d+\s*%|remaining|left|quota|limit|available|credits|unlimited)/i.test(l))
                  || rawLines.slice(0, 2).join(' • ')
                  || '');

            if (limits.length === 0 && fallbackLeftPct === null && !/(\d+\s*%|resets?\s+(?:in|at|on)|unlimited)/i.test(summaryLine)) {
              return null;
            }

            const resetLine = (fiveH && fiveH.resetLabel) || (weekly && weekly.resetLabel) || rawLines.find(isResetLine) || '';
            const planLine = rawLines.find(l => /(ultra|pro|standard|free|enterprise|workspace|plan)/i.test(l) && l.length < 36) || 'Antigravity';

            return {
              usedPercent: fallbackLeftPct !== null ? Math.max(0, Math.min(100, 100 - fallbackLeftPct)) : null,
              leftPercent: fallbackLeftPct,
              fiveHourLeftPercent: fiveHPct,
              fiveHourResetLabel: fiveH ? fiveH.resetLabel : '',
              weeklyLeftPercent: weeklyPct,
              weeklyResetLabel: weekly ? weekly.resetLabel : '',
              limits: limits,
              remainingLabel: summaryLine.slice(0, 88),
              resetLabel: resetLine.slice(0, 48),
              planLabel: planLine,
              rawSummary: summaryLine.slice(0, 96),
              isLive: true
            };
          }

          function detectStreaming() {
            const root = getAppRoot();
            const stopBtns = collectDeep(root, el => {
              const tag = (el.tagName || '').toLowerCase();
              const role = el.getAttribute('role') || '';
              if (tag !== 'button' && role !== 'button') return false;
              if (!isVisible(el)) return false;
              const label = ((el.getAttribute('aria-label') || '') + ' ' + cleanText(el.textContent)).toLowerCase();
              return label.includes('stop generating') || label === 'stop' || label.includes('cancel response');
            }, 15);
            return stopBtns.length > 0;
          }

          function detectAuthRequired() {
            const host = (window.location.hostname || '').toLowerCase();
            if (host.includes('accounts.google.') || host.includes('signin.google.') || host.includes('consent.google.')) {
              return true;
            }
            const signInButtons = collectDeep(getAppRoot(), el => {
              const tag = (el.tagName || '').toLowerCase();
              if (tag !== 'button' && tag !== 'a') return false;
              if (!isVisible(el)) return false;
              const txt = cleanText(el.textContent).toLowerCase();
              return txt === 'sign in' || txt === 'sign in with google' || txt === 'log in';
            }, 5);
            return signInButtons.length > 0 && !findComposerInput();
          }

          function buildSnapshot() {
            const authRequired = detectAuthRequired();
            const root = getAppRoot();
            const composer = findComposerInput();
            const modelBtn = findModelSelectorButton();
            const activeModel = parseActiveModelName(modelBtn);
            const activeProject = parseAriaCurrentValue('[data-testid="project-selector-trigger"]');
            const permissionsPreset = parseAriaCurrentValue('[data-testid="permissions-selector-trigger"]');
            const isStreaming = detectStreaming();
            const messages = authRequired ? [] : extractMessages();
            if (isStreaming && messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
              messages[messages.length - 1].isStreaming = true;
            }
            const conversations = authRequired ? [] : extractConversations();

            // Exact conversation title from [data-testid="breadcrumb-segment"] when inside a thread!
            const breadcrumbEl = root.querySelector('[data-testid="breadcrumb-segment"]');
            const breadcrumbTitle = breadcrumbEl ? cleanText(breadcrumbEl.textContent) : '';
            const activeConv = conversations.find(c => c.isActive);
            const title = breadcrumbTitle || (activeConv ? activeConv.title : '') || 'Antigravity';

            if (activeModel && cachedModels.length > 0) {
              cachedModels = cachedModels.map(m => {
                const base = m.name.toLowerCase().replace(/\s*\([^)]+\)\s*$/, '');
                const selected = activeModel.toLowerCase().startsWith(base);
                const effortMatch = selected ? activeModel.match(/\b(Low|Medium|High|Extra|Fast)\b/i) : null;
                return Object.assign({}, m, {
                  isSelected: selected,
                  tier: (effortMatch && effortMatch[1]) ? effortMatch[1] : m.tier
                });
              });
            }

            // Harvest visible sidebar projects whenever on Home
            const projectHeaders = Array.from(root.querySelectorAll('[data-testid="project-header"]'));
            if (projectHeaders.length > 0) {
              const seenP = new Set();
              const foundP = [];
              projectHeaders.forEach((ph, idx) => {
                const pName = cleanText(ph.textContent);
                if (pName && !seenP.has(pName)) {
                  seenP.add(pName);
                  foundP.push({
                    id: 'proj-' + idx + '-' + pName,
                    name: pName,
                    isSelected: activeProject.toLowerCase() === pName.toLowerCase()
                  });
                }
              });
              if (foundP.length > 0) cachedProjects = foundP;
            } else if (activeProject && cachedProjects.length === 0) {
              cachedProjects = [{ id: 'proj-0-' + activeProject, name: activeProject, isSelected: true }];
            } else if (cachedProjects.length > 0) {
              cachedProjects = cachedProjects.map(p => Object.assign({}, p, {
                isSelected: activeProject.toLowerCase() === p.name.toLowerCase()
              }));
            }

            const olderBtn = root.querySelector('[data-testid="load-older-messages-button"]');
            const olderText = olderBtn ? cleanText(olderBtn.textContent).toLowerCase() : '';
            const olderStyle = olderBtn ? window.getComputedStyle(olderBtn) : null;
            const hasOlderMessages = !!(
              olderBtn &&
              !olderBtn.disabled &&
              olderBtn.getAttribute('aria-disabled') !== 'true' &&
              !/no\s+more/i.test(olderText) &&
              /load\s+older/i.test(olderText) &&
              (!olderStyle || (olderStyle.display !== 'none' && olderStyle.visibility !== 'hidden'))
            );
            const hasSidePanel = !!root.querySelector('[data-testid="mobile-toggle-aux-sidebar"]');

            const diagBase = 'turns=' + messages.length + ', chats=' + conversations.length + ', models=' + cachedModels.length + ', composer=' + (!!composer);
            saveStore();
            return {
              url: window.location.href,
              title: title,
              authRequired: authRequired,
              isStreaming: isStreaming,
              activeModel: activeModel,
              activeProject: activeProject,
              permissionsPreset: permissionsPreset,
              models: cachedModels,
              projects: cachedProjects,
              conversations: conversations,
              messages: messages,
              quota: cachedQuota,
              hasOlderMessages: hasOlderMessages,
              hasSidePanel: hasSidePanel,
              composerReady: !!composer,
              diagnosticsNote: lastQuotaDiag ? (diagBase + ', ' + lastQuotaDiag) : diagBase
            };
          }

          function syncNow(force) {
            if (isInternalModalProbe && !force) return;
            if (!force && Date.now() < (store.modelMutationHoldUntil || 0)) return;
            try {
              if (document.getElementById('af-error-container')) {
                window.location.replace('https://antigravity.google.com/remote');
                return;
              }
              const snapshot = buildSnapshot();
              const json = JSON.stringify(snapshot);
              if (force || json !== lastPayloadHash) {
                lastPayloadHash = json;
                if (window.AntigravityNative && window.AntigravityNative.onStateSnapshot) {
                  window.AntigravityNative.onStateSnapshot(json);
                }
              }
            } catch (e) {
              if (window.AntigravityNative && window.AntigravityNative.onBridgeLog) {
                window.AntigravityNative.onBridgeLog('sync error: ' + e);
              }
            }
          }

          function scheduleSync() {
            if (isInternalModalProbe) return;
            if (syncTimer) return;
            const delay = detectStreaming() ? 380 : 950;
            syncTimer = setTimeout(() => {
              syncTimer = null;
              syncNow(false);
            }, delay);
          }

          function startStreamingHeartbeat() {
            if (streamingHeartbeatTimer) {
              clearInterval(streamingHeartbeatTimer);
              streamingHeartbeatTimer = null;
            }
            streamingHeartbeatTicks = 0;
            streamingHeartbeatTimer = setInterval(() => {
              streamingHeartbeatTicks++;
              syncNow(false);
              if (detectStreaming()) {
                streamingHeartbeatTicks = Math.min(streamingHeartbeatTicks, 12);
              } else if (streamingHeartbeatTicks >= 58) {
                clearInterval(streamingHeartbeatTimer);
                streamingHeartbeatTimer = null;
              }
            }, 600);
          }

          function focusComposer() {
            const input = findComposerInput();
            if (!input) return false;
            try {
              const rect = input.getBoundingClientRect();
              if (rect.top < 0 || rect.bottom > window.innerHeight) {
                input.scrollIntoView({ block: 'center', inline: 'center' });
              }
              simulateTap(input);
              dispatchNativeTap(input);
              input.focus();
              if (input.isContentEditable) {
                const range = document.createRange();
                range.selectNodeContents(input);
                if (cleanText(input.textContent).length === 0) {
                  range.collapse(false);
                }
                const sel = window.getSelection();
                if (sel) {
                  sel.removeAllRanges();
                  sel.addRange(range);
                }
              }
              return true;
            } catch (_) {
              return false;
            }
          }

          async function submitPromptAfterCommit(text) {
            const report = (ok, reason) => {
              try {
                if (window.AntigravityNative && window.AntigravityNative.onPromptSendResult) {
                  window.AntigravityNative.onPromptSendResult(!!ok, reason || '');
                }
              } catch (_) {}
            };

            const input = findComposerInput();
            if (!input || !text) {
              report(false, 'composer not found');
              return false;
            }

            try {
              await sleep(80);
              const existing = cleanText(input.textContent || input.value || '');
              if (existing.length === 0) {
                input.focus();
                const tag = (input.tagName || '').toLowerCase();
                if (tag === 'textarea' || tag === 'input') {
                  const proto = tag === 'textarea' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                  const desc = Object.getOwnPropertyDescriptor(proto, 'value');
                  if (desc && desc.set) desc.set.call(input, text);
                  else input.value = text;
                  input.dispatchEvent(new Event('input', { bubbles: true }));
                  input.dispatchEvent(new Event('change', { bubbles: true }));
                } else {
                  const range = document.createRange();
                  range.selectNodeContents(input);
                  const sel = window.getSelection();
                  if (sel) {
                    sel.removeAllRanges();
                    sel.addRange(range);
                  }

                  let inserted = false;
                  try {
                    document.execCommand('selectAll', false, null);
                    document.execCommand('insertText', false, text);
                    await sleep(60);
                    if (cleanText(input.textContent).length > 0) inserted = true;
                  } catch (_) {}

                  if (!inserted) {
                    try {
                      const dt = new DataTransfer();
                      dt.setData('text/plain', text);
                      input.dispatchEvent(new ClipboardEvent('paste', {
                        bubbles: true,
                        cancelable: true,
                        clipboardData: dt
                      }));
                      await sleep(60);
                      if (cleanText(input.textContent).length > 0) inserted = true;
                    } catch (_) {}
                  }

                  if (!inserted) {
                    input.dispatchEvent(new InputEvent('beforeinput', {
                      bubbles: true,
                      cancelable: true,
                      inputType: 'insertText',
                      data: text
                    }));
                    input.dispatchEvent(new InputEvent('input', {
                      bubbles: true,
                      inputType: 'insertText',
                      data: text
                    }));
                    await sleep(60);
                  }
                }
              }

              // Wait for [data-testid="send-button"] to become enabled
              const sendBtn = await waitFor(() => {
                const btn = getAppRoot().querySelector('[data-testid="send-button"], button[aria-label="Send message"]');
                return (btn && !btn.disabled && btn.getAttribute('aria-disabled') !== 'true' && isVisible(btn)) ? btn : null;
              }, 900, 45);

              if (sendBtn) {
                // 1. Genuine isTrusted Android MotionEvent tap on send-button
                dispatchNativeTap(sendBtn);
                await sleep(220);

                // 2. Fallback synthetic tap if input still has text
                if (cleanText(input.textContent || input.value || '').length > 0) {
                  simulateTap(sendBtn);
                  await sleep(180);
                }

                // 3. Fallback trusted Android KEYCODE_ENTER if input still has text
                if (cleanText(input.textContent || input.value || '').length > 0) {
                  input.focus();
                  if (window.AntigravityNative && window.AntigravityNative.dispatchTrustedEnter) {
                    window.AntigravityNative.dispatchTrustedEnter();
                  } else {
                    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true }));
                  }
                  await sleep(250);
                }

                const cleared = cleanText(input.textContent || input.value || '').length === 0;
                startStreamingHeartbeat();
                setTimeout(() => syncNow(true), 300);
                setTimeout(() => syncNow(true), 1000);
                report(cleared, cleared ? 'sent' : 'input did not clear');
                return cleared;
              } else {
                input.focus();
                if (window.AntigravityNative && window.AntigravityNative.dispatchTrustedEnter) {
                  window.AntigravityNative.dispatchTrustedEnter();
                } else {
                  input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true }));
                }
                await sleep(250);
                const cleared = cleanText(input.textContent || input.value || '').length === 0;
                if (cleared) {
                  startStreamingHeartbeat();
                  setTimeout(() => syncNow(true), 300);
                  setTimeout(() => syncNow(true), 1000);
                }
                report(cleared, cleared ? 'sent via enter' : 'send button stayed disabled');
                return cleared;
              }
            } catch (err) {
              report(false, String(err));
              return false;
            }
          }

          async function sendPrompt(text) {
            focusComposer();
            await sleep(70);
            return submitPromptAfterCommit(text);
          }

          function stopGeneration() {
            const stopBtns = collectDeep(getAppRoot(), el => {
              const t = (el.tagName || '').toLowerCase();
              if (t !== 'button' && el.getAttribute('role') !== 'button') return false;
              if (!isVisible(el)) return false;
              const label = ((el.getAttribute('aria-label') || '') + ' ' + cleanText(el.textContent)).toLowerCase();
              return label.includes('stop') || label.includes('cancel');
            }, 15);
            if (stopBtns.length > 0) {
              simulateTap(stopBtns[0]);
              setTimeout(() => syncNow(true), 250);
              return true;
            }
            return false;
          }

          async function prewarmModelsAndQuota() {
            const btn = findModelSelectorButton();
            if (!btn || isInternalModalProbe) return false;
            const activeModel = parseActiveModelName(btn);
            isInternalModalProbe = true;
            try {
              if (!document.querySelector('[data-testid="model-selector-panel"]')) {
                simulateTap(btn);
              }
              const panel = await waitFor(() => document.querySelector('[data-testid="model-selector-panel"]'), 1000, 40);
              if (panel) {
                const parsed = readModelsFromOpenPanel(activeModel);
                if (parsed.length > 0) cachedModels = parsed;
                if (!quotaPrewarmed) {
                  quotaPrewarmed = true;
                  const usageBtn = Array.from(panel.querySelectorAll('button.agy-overlay-item, a.agy-overlay-item, button, a'))
                    .find(b => /view usage/i.test(cleanText(b.textContent)));
                  if (usageBtn) {
                    simulateTap(usageBtn);
                    const startedAt = Date.now();
                    const usagePane = await waitFor(() => {
                      const allowLoose = (Date.now() - startedAt) > 2200;
                      return findUsageContainerFromSheet(panel, allowLoose);
                    }, 3800, 80);
                    if (usagePane) {
                      const parsedQuota = parseUsagePane(usagePane);
                      if (parsedQuota) {
                        cachedQuota = parsedQuota;
                        lastQuotaDiag = 'usage=ok(prewarm:' + parsedQuota.limits.length + ')';
                      }
                    }
                  }
                }
              }
              dismissOverlays();
              await sleep(140);
            } finally {
              isInternalModalProbe = false;
              syncNow(true);
            }
            return true;
          }

          async function refreshModels() {
            const btn = findModelSelectorButton();
            if (!btn || isInternalModalProbe) return false;
            const activeModel = parseActiveModelName(btn);
            isInternalModalProbe = true;
            try {
              if (!document.querySelector('[data-testid="model-selector-panel"]')) {
                simulateTap(btn);
              }
              const panel = await waitFor(() => document.querySelector('[data-testid="model-selector-panel"]'), 1000, 40);
              if (panel) {
                const parsed = readModelsFromOpenPanel(activeModel);
                if (parsed.length > 0) cachedModels = parsed;
                // Probe visible effort options for each effort-group model once so UI never shows unsupported tiers
                for (let i = 0; i < cachedModels.length; i++) {
                  const m = cachedModels[i];
                  if (!m.hasEffortLevels) continue;
                  const gEl = Array.from(panel.querySelectorAll('[data-testid="model-selector-effort-group"]'))
                    .find(s => cleanText(s.textContent).toLowerCase() === m.name.toLowerCase());
                  if (!gEl) continue;
                  simulateTap(gEl.closest('button') || gEl);
                  await sleep(220);
                  const visibleOpts = Array.from(document.querySelectorAll('[data-testid="model-selector-effort-option"]'))
                    .filter(isInViewport)
                    .map(o => cleanText(o.textContent))
                    .filter(Boolean);
                  if (visibleOpts.length > 0) {
                    cachedEffortMap[m.name.toLowerCase()] = visibleOpts;
                    cachedModels[i] = Object.assign({}, m, { effortOptions: visibleOpts });
                  }
                  const backBtn = Array.from(document.querySelectorAll('[role="dialog"] button'))
                    .find(b => isInViewport(b) && (b.textContent.includes(m.name) || (b.getAttribute('aria-label') || '').toLowerCase().includes('back')));
                  if (backBtn) {
                    simulateTap(backBtn);
                    await sleep(200);
                  }
                }
              }
              dismissOverlays();
              await sleep(160);
            } finally {
              isInternalModalProbe = false;
              syncNow(true);
            }
            return true;
          }

          async function selectModel(modelName, effortTier) {
            const btn = findModelSelectorButton();
            if (!btn) return false;
            if (isInternalModalProbe) {
              await waitFor(() => !isInternalModalProbe, 1200, 40);
            }
            isInternalModalProbe = true;
            store.modelMutationHoldUntil = Date.now() + 2000;
            try {
              if (!document.querySelector('[data-testid="model-selector-panel"]')) {
                simulateTap(btn);
              }
              const panel = await waitFor(() => document.querySelector('[data-testid="model-selector-panel"]'), 1200, 40);
              if (!panel) return false;

              const targetLower = (modelName || '').toLowerCase().trim();

              const directItems = Array.from(panel.querySelectorAll('button[data-testid="model-selector-item"]'));
              const directMatch = directItems.find(b => cleanText(b.textContent).toLowerCase().includes(targetLower));
              if (directMatch) {
                simulateTap(directMatch);
                await sleep(320);
                return true;
              }

              const groupSpans = Array.from(panel.querySelectorAll('[data-testid="model-selector-effort-group"]'));
              const groupMatch = groupSpans.find(s => cleanText(s.textContent).toLowerCase() === targetLower) ||
                groupSpans.find(s => cleanText(s.textContent).toLowerCase().includes(targetLower));

              if (!groupMatch) {
                dismissOverlays();
                return false;
              }

              simulateTap(groupMatch.closest('button') || groupMatch);
              await sleep(180);

              const optionSpans = await waitFor(() => {
                const opts = Array.from(document.querySelectorAll('[data-testid="model-selector-effort-option"]'))
                  .filter(isInViewport);
                return opts.length > 0 ? opts : null;
              }, 1100, 40);

              if (optionSpans) {
                const visibleLabels = optionSpans.map(s => cleanText(s.textContent)).filter(Boolean);
                if (visibleLabels.length > 0) {
                  cachedEffortMap[targetLower] = visibleLabels;
                  cachedModels = cachedModels.map(m =>
                    m.name.toLowerCase() === targetLower ? Object.assign({}, m, { effortOptions: visibleLabels }) : m
                  );
                }
                let desiredTier = (effortTier || 'Medium').toLowerCase().trim();
                let match = optionSpans.find(s => cleanText(s.textContent).toLowerCase() === desiredTier);
                if (!match && desiredTier === 'extra') {
                  match = optionSpans.find(s => cleanText(s.textContent).toLowerCase() === 'high');
                }
                if (!match) {
                  match = optionSpans[optionSpans.length - 1];
                }
                if (match) {
                  simulateTap(match.closest('button') || match);
                  await sleep(360);
                }
              }

              if (document.querySelector('[data-testid="bottom-sheet-backdrop"]')) {
                dismissOverlays();
                await sleep(140);
              }
            } finally {
              isInternalModalProbe = false;
              store.modelMutationHoldUntil = 0;
              syncNow(true);
            }
            return true;
          }

          function isUsageContentReady(el, allowLoose) {
            if (!el) return false;
            const txt = cleanText(el.innerText || el.textContent);
            if (!txt || txt.length < 6) return false;
            if (/thinking effort|gemini 4 neon.*gemini 4 carbon/i.test(txt)) return false;
            if (/^\s*(refreshing|loading)\b/i.test(txt) && !/\d{1,3}\s*%/.test(txt)) return false;
            const hasLoadedQuotaSignal = /(\d{1,3}\s*%|\b(?:5\s*h|weekly|hourly)\s+limit\b|resets?\s+(?:in|at|on)|unlimited)/i.test(txt);
            if (hasLoadedQuotaSignal) return true;
            return allowLoose && !/\b(refreshing|loading)\b/i.test(txt) && /(quota|usage|remaining|left|credits|plan)/i.test(txt);
          }

          function findUsageContainerFromSheet(panel, allowLoose) {
            const sheetRoot = panel && panel.closest ? panel.closest('[data-testid="bottom-sheet"], [role="dialog"]') : null;

            // 1. Walk ancestors from panel up to sheetRoot and check sibling subtrees (handles nested slide wrappers)
            if (panel && sheetRoot) {
              let node = panel;
              while (node && node !== sheetRoot) {
                const parent = node.parentElement;
                if (!parent) break;
                const siblings = Array.from(parent.children).filter(ch => ch !== node);
                for (const sib of siblings) {
                  if (sib.querySelector('[data-testid="model-selector-panel"], [data-testid="model-selector-effort-option"]')) {
                    continue;
                  }
                  if (isUsageContentReady(sib, allowLoose)) {
                    return sib;
                  }
                }
                node = parent;
              }
            }

            // 2. Check any newly opened separate dialog/sheet not containing model-selector-panel
            const dialogs = Array.from(document.querySelectorAll('[role="dialog"], [data-testid="bottom-sheet"]'))
              .filter(isVisible);
            for (let i = dialogs.length - 1; i >= 0; i--) {
              if (!dialogs[i].querySelector('[data-testid="model-selector-panel"]')) {
                if (isUsageContentReady(dialogs[i], allowLoose)) {
                  return dialogs[i];
                }
              }
            }

            // 3. Fallback: clone sheetRoot and strip model-selector-panel / effort-option nodes
            if (sheetRoot) {
              const clone = sheetRoot.cloneNode(true);
              clone.querySelectorAll('[data-testid="model-selector-panel"], [data-testid="model-selector-effort-option"], [data-testid="bottom-sheet-backdrop"]').forEach(el => {
                if (el.parentNode) el.parentNode.removeChild(el);
              });
              if (isUsageContentReady(clone, allowLoose)) {
                return clone;
              }
            }

            // 4. Fallback: check non-chat overlay inside appRoot
            const root = getAppRoot();
            const bars = Array.from(root.querySelectorAll('[role="progressbar"], meter')).filter(b => {
              if (!isVisible(b)) return false;
              return !b.closest('[data-testid="conversation-view"]') && !b.closest('[data-testid="conversation-list-sidebar"]');
            });
            for (const bar of bars) {
              const cand = bar.closest('section, article, [role="dialog"], div') || bar.parentElement;
              if (isUsageContentReady(cand, allowLoose)) return cand;
            }
            return null;
          }

          async function refreshQuota() {
            const btn = findModelSelectorButton();
            if (!btn) {
              lastQuotaDiag = 'usage=no-model-btn';
              syncNow(true);
              return false;
            }
            if (isInternalModalProbe) {
              await waitFor(() => !isInternalModalProbe, 1200, 40);
            }
            isInternalModalProbe = true;
            try {
              if (!document.querySelector('[data-testid="model-selector-panel"]')) {
                simulateTap(btn);
              }
              const panel = await waitFor(() => document.querySelector('[data-testid="model-selector-panel"]'), 1200, 40);
              const usageBtn = panel && Array.from(panel.querySelectorAll('button.agy-overlay-item, a.agy-overlay-item, button, a'))
                .find(b => /view usage/i.test(cleanText(b.textContent)));

              if (!usageBtn) {
                lastQuotaDiag = 'usage=no-view-btn';
                dismissOverlays();
                return false;
              }

              simulateTap(usageBtn);

              const startedAt = Date.now();
              const usagePane = await waitFor(() => {
                const allowLoose = (Date.now() - startedAt) > 2200;
                return findUsageContainerFromSheet(panel, allowLoose);
              }, 3800, 80);

              if (usagePane) {
                const parsed = parseUsagePane(usagePane);
                if (parsed) {
                  cachedQuota = parsed;
                  lastQuotaDiag = 'usage=ok(' + (parsed.leftPercent !== null ? parsed.leftPercent + '% left' : 'text') + ',limits=' + parsed.limits.length + ')';
                } else {
                  lastQuotaDiag = 'usage=parse-empty';
                }
              } else {
                const openSheets = document.querySelectorAll('[data-testid="bottom-sheet"], [role="dialog"]').length;
                lastQuotaDiag = 'usage=miss(sheets=' + openSheets + ',url=' + (window.location.pathname || '') + ')';
              }
              dismissOverlays();
              await sleep(160);
            } finally {
              isInternalModalProbe = false;
              syncNow(true);
            }
            return true;
          }

          function startNewChat() {
            const root = getAppRoot();
            const newBtn = root.querySelector('button[aria-label="New Conversation"], a[aria-label="New Conversation in Project"]');
            if (newBtn && isVisible(newBtn)) {
              simulateTap(newBtn);
              setTimeout(() => syncNow(true), 350);
              return true;
            }
            const backBtn = root.querySelector('[data-testid="mobile-back-to-home"]');
            if (backBtn && isVisible(backBtn)) {
              simulateTap(backBtn);
              setTimeout(() => syncNow(true), 350);
              return true;
            }
            window.location.href = 'https://antigravity.google.com/remote';
            return true;
          }

          function clickSidebarRow(url, title) {
            const root = getAppRoot();
            const rows = Array.from(root.querySelectorAll('[data-testid="conversation-row-sidebar"]'));
            const targetTitle = (title || '').trim().toLowerCase();

            for (const row of rows) {
              const anchor = row.querySelector('a') || row.querySelector('button');
              if (!anchor) continue;
              const aria = cleanText(anchor.getAttribute('aria-label')).toLowerCase();
              const href = anchor.getAttribute('href') || '';
              let fullUrl = '';
              if (href) {
                try { fullUrl = new URL(href, window.location.origin).href; } catch (_) {}
              }
              if ((url && fullUrl === url) || (targetTitle && aria === targetTitle)) {
                simulateTap(anchor);
                setTimeout(() => syncNow(true), 350);
                setTimeout(() => syncNow(true), 950);
                setTimeout(() => syncNow(true), 1800);
                return true;
              }
            }
            return false;
          }

          async function selectConversation(url, title) {
            if (clickSidebarRow(url, title)) return true;

            // Exact mobile back-to-home button discovered from thread DOM snapshot!
            const backBtn = getAppRoot().querySelector('[data-testid="mobile-back-to-home"], button[aria-label="Back to home"]');
            if (backBtn && isVisible(backBtn)) {
              simulateTap(backBtn);
              const rowsReady = await waitFor(
                () => getAppRoot().querySelectorAll('[data-testid="conversation-row-sidebar"]').length > 0,
                1800,
                50
              );
              if (rowsReady && clickSidebarRow(url, title)) {
                return true;
              }
            }

            if (url && url.startsWith('https://antigravity.google.com/remote')) {
              window.location.href = url;
              return true;
            }
            return false;
          }

          async function triggerFileUpload() {
            const reportUpload = (ok, reason) => {
              try {
                if (window.AntigravityNative && window.AntigravityNative.onFileUploadResult) {
                  window.AntigravityNative.onFileUploadResult(!!ok, reason || '');
                }
              } catch (_) {}
            };

            const root = getAppRoot();
            const findFileInput = () =>
              document.querySelector('[data-testid="agent-input-box"] input[type="file"], input[type="file"]');

            let fileInput = findFileInput();
            if (fileInput) {
              if (!dispatchNativeTap(fileInput)) fileInput.click();
              reportUpload(true, 'file-input');
              return true;
            }

            const addBtn = root.querySelector('button[aria-label="Add context"], [data-testid="add-context-button"]') ||
              collectDeep(root, el => {
                const tag = (el.tagName || '').toLowerCase();
                if (tag !== 'button' && el.getAttribute('role') !== 'button') return false;
                if (!isVisible(el)) return false;
                const aria = (el.getAttribute('aria-label') || '').toLowerCase();
                return aria.includes('add context') || aria.includes('attach') || aria.includes('upload');
              }, 5)[0];

            if (!addBtn || !isVisible(addBtn)) {
              reportUpload(false, 'Add context control unavailable');
              return false;
            }

            // Use genuine trusted MotionEvent first so Chromium/WebView honors user gesture for file chooser
            const tapped = dispatchNativeTap(addBtn) || simulateTap(addBtn);
            if (!tapped) {
              reportUpload(false, 'Unable to tap Add context');
              return false;
            }

            // Check if tapping Add context directly spawned <input type="file"> or opened an overlay menu
            const found = await waitFor(() => {
              const inp = findFileInput();
              if (inp) return { kind: 'input', el: inp };
              const items = Array.from(document.querySelectorAll('[role="menuitem"], [role="menu"] button, [data-testid="bottom-sheet"] button, button.agy-overlay-item'))
                .filter(isVisible);
              const uploadChoice = items.find(b => /upload|file|image|photo|attach|browse/i.test(cleanText(b.textContent) + ' ' + (b.getAttribute('aria-label') || '')));
              if (uploadChoice) return { kind: 'menu', el: uploadChoice };
              return null;
            }, 700, 50);

            if (found && found.kind === 'input') {
              found.el.click();
              reportUpload(true, 'file-input');
              return true;
            }
            if (found && found.kind === 'menu') {
              dispatchNativeTap(found.el) || simulateTap(found.el);
              await sleep(150);
              const inp2 = findFileInput();
              if (inp2) inp2.click();
              reportUpload(true, 'menu-upload');
              return true;
            }

            reportUpload(false, 'Attachment menu not supported in this view');
            return false;
          }

          function dumpDomSnapshot() {
            function redact(str) {
              return cleanText(str)
                .replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, '[email]')
                .slice(0, 60);
            }

            const appRoot = getAppRoot();
            const allTestIds = Array.from(new Set(
              collectDeep(appRoot, el => !!el.getAttribute('data-testid'), 500)
                .map(el => el.getAttribute('data-testid'))
            )).slice(0, 120);

            const allAriaLabels = Array.from(new Set(
              collectDeep(appRoot, el => !!el.getAttribute('aria-label') && isVisible(el), 300)
                .map(el => (el.tagName || '').toLowerCase() + ':' + redact(el.getAttribute('aria-label')))
            )).slice(0, 80);

            function walk(el, depth) {
              if (!el || el.nodeType !== 1 || depth > 14) return null;
              const tag = (el.tagName || '').toLowerCase();
              if (tag === 'script' || tag === 'style' || tag === 'svg' || tag === 'path') return null;

              const tid = el.getAttribute('data-testid');
              const role = el.getAttribute('role');
              const aria = el.getAttribute('aria-label');
              const expanded = el.getAttribute('aria-expanded');
              const ownText = redact(
                Array.from(el.childNodes).filter(c => c.nodeType === 3).map(c => c.nodeValue).join(' ')
              );

              const childSource = el.shadowRoot ? el.shadowRoot.children : el.children;
              const kids = [];
              if (childSource) {
                for (let i = 0; i < Math.min(childSource.length, 32); i++) {
                  const childNode = walk(childSource[i], depth + 1);
                  if (childNode) kids.push(childNode);
                }
              }

              if (tag === 'div' && !tid && !role && !aria && !el.id && !ownText && expanded === null && kids.length === 1) {
                return kids[0];
              }

              const node = { t: tag };
              if (el.id) node.id = el.id.slice(0, 32);
              if (tid) node.tid = tid;
              if (role) node.r = role;
              if (aria) node.a = redact(aria);
              if (expanded !== null) node.exp = expanded;
              if (el.isContentEditable) node.ce = true;
              if (typeof el.className === 'string' && el.className.trim()) {
                node.c = el.className.trim().split(/\s+/).slice(0, 3).join(' ');
              }
              if (ownText) node.tx = ownText;
              if (kids.length) node.ch = kids;
              return node;
            }

            const sheets = Array.from(document.querySelectorAll('[data-testid="bottom-sheet"], [role="dialog"]'))
              .filter(isVisible)
              .slice(0, 3)
              .map(s => walk(s, 0));

            return JSON.stringify({
              url: window.location.href,
              rootTag: (appRoot && appRoot.getAttribute && appRoot.getAttribute('data-testid')) || (appRoot && appRoot.tagName) || 'body',
              testIds: allTestIds,
              ariaLabels: allAriaLabels,
              mobileTree: walk(appRoot, 0),
              openDialogs: sheets
            });
          }

          function findAuxPanelRoot() {
            const dialogs = Array.from(document.querySelectorAll('.aux-drawer-popup, [data-testid="bottom-sheet"], [role="dialog"]'))
              .filter(el => isVisible(el) && !el.hasAttribute('data-closed') && !el.hasAttribute('data-ending-style'));
            return dialogs.length > 0 ? dialogs[dialogs.length - 1] : null;
          }

          function closeAuxPanel() {
            const toggleBtn = getAppRoot().querySelector('[data-testid="mobile-toggle-aux-sidebar"]');
            const panel = findAuxPanelRoot();
            if (panel) {
              dismissOverlays();
              if (toggleBtn && isVisible(toggleBtn) && findAuxPanelRoot()) {
                simulateTap(toggleBtn);
              }
            }
          }

          async function loadOlderMessages() {
            const root = getAppRoot();
            const btn = root.querySelector('[data-testid="load-older-messages-button"]');
            if (btn) {
              simulateTap(btn);
            }
            await sleep(750);
            syncNow(true);
            setTimeout(() => syncNow(true), 1500);
            return true;
          }

          async function expandReasoningBlock(blockId, blockTitle) {
            const root = getAppRoot();
            let el = blockId ? root.querySelector('[data-ag-reason-id="' + blockId.replace(/"/g, '') + '"]') : null;
            if (!el && blockTitle) {
              el = Array.from(root.querySelectorAll('[data-testid="worked-for-collapsible"], details'))
                .find(n => cleanText(n.textContent).toLowerCase().includes(blockTitle.toLowerCase()));
            }
            if (!el) return false;
            const trigger = (el.tagName || '').toLowerCase() === 'button' ? el : (el.querySelector('summary, button') || el);
            const wasExpanded = trigger.getAttribute('aria-expanded') === 'true' || el.open === true;
            if (!wasExpanded) {
              simulateTap(trigger);
              await sleep(280);
              const sib = el.nextElementSibling;
              if (sib) {
                const subBtns = Array.from(sib.querySelectorAll('[data-testid="tool-group-collapsible"][aria-expanded="false"]')).slice(0, 4);
                subBtns.forEach(sb => simulateTap(sb));
                if (subBtns.length > 0) await sleep(220);
              }
            }
            syncNow(true);
            return true;
          }

          function openExternalArtifact(cardTitle) {
            const root = getAppRoot();
            const lower = (cardTitle || '').trim().toLowerCase();
            if (!lower) return false;
            const card = Array.from(root.querySelectorAll('[data-testid="artifact-card"]'))
              .find(c => cleanText(c.textContent).toLowerCase().includes(lower));
            if (!card) return false;
            const anchor = card.matches('a[href]') ? card : (card.querySelector('a[href]') || card.closest('a[href]') || card);
            dispatchNativeTap(anchor) || simulateTap(anchor);
            return true;
          }

          function classifyDiffBgColor(el) {
            if (!el) return 'context';
            let cur = el;
            for (let depth = 0; depth < 3 && cur && cur.nodeType === 1; depth++) {
              const cls = (typeof cur.className === 'string' ? cur.className : '').toLowerCase();
              if (/\b(diff-add|added|insert|addition|bg-green|bg-emerald)\b/.test(cls)) return 'added';
              if (/\b(diff-del|removed|delete|deletion|bg-red|bg-rose)\b/.test(cls)) return 'removed';
              const bg = window.getComputedStyle(cur).backgroundColor || '';
              const m = bg.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?/);
              if (m) {
                const r = parseInt(m[1], 10), g = parseInt(m[2], 10), b = parseInt(m[3], 10);
                const a = m[4] !== undefined ? parseFloat(m[4]) : 1;
                if (a > 0.05) {
                  if (g > r + 14 && g > b + 10) return 'added';
                  if (r > g + 18 && r > b + 14) return 'removed';
                }
              }
              cur = cur.parentElement;
            }
            return 'context';
          }

          function queryAllDeep(root, selector) {
            const results = [];
            const visit = (node) => {
              if (!node) return;
              if (node.querySelectorAll) {
                try {
                  node.querySelectorAll(selector).forEach(el => results.push(el));
                } catch (_) {}
                const all = node.querySelectorAll('*');
                for (let i = 0; i < all.length; i++) {
                  if (all[i].shadowRoot) visit(all[i].shadowRoot);
                }
              }
            };
            visit(root);
            return results;
          }

          function extractDeepDiffLines(card, hdr, fileName, path) {
            const lines = [];
            const pushLine = (num, text, kind) => {
              const cleaned = (text || '').replace(/\u00a0/g, ' ').replace(/\r/g, '').trimEnd();
              const trimmed = cleaned.trim();
              if (!trimmed) return;
              if (trimmed === fileName || trimmed === path) return;
              if (/^[+−-]\s*\d+$/.test(trimmed) || /^\+\s*\d+\s*[-−]\s*\d+$/.test(trimmed)) return;
              if (/^\+\d+\s+more\s+lines$/i.test(trimmed)) {
                lines.push({ num: '⋯', text: trimmed, kind: 'hunk' });
                return;
              }
              if (/^\+\d+$/.test(trimmed) && trimmed.length <= 4) return;
              lines.push({ num: num || '', text: cleaned, kind: kind || 'context' });
            };

            // 1. Check Shadow DOM diff components (e.g. Pierre Diffs <diffs-container> with [data-line] / [data-line-type])
            const deepDataLines = queryAllDeep(card, '[data-line], [data-line-type], [data-code-line], tr, .cm-line, .view-line, [class*="diff-line"]');
            const validDeep = deepDataLines.filter(el => {
              if (el === hdr || (hdr && hdr.contains(el)) || (el.contains && hdr && el.contains(hdr))) return false;
              const t = (el.innerText || el.textContent || '').trim();
              return t.length > 0;
            });

            if (validDeep.length > 0) {
              // Deduplicate nested matches (prefer leaf line rows)
              const leafRows = validDeep.filter(el => !validDeep.some(other => other !== el && el.contains(other)));
              for (const row of leafRows.slice(0, 260)) {
                const rawBlock = (row.innerText || row.textContent || '').replace(/\u00a0/g, ' ').replace(/\r/g, '');
                const lineTypeAttr = (row.getAttribute('data-line-type') || row.getAttribute('data-change-type') || row.className || '').toString().toLowerCase();
                let baseKind = 'context';
                if (/add|insert|plus/.test(lineTypeAttr)) baseKind = 'added';
                else if (/del|rem|minus/.test(lineTypeAttr)) baseKind = 'removed';
                else if (/hunk|expand|sep/.test(lineTypeAttr)) baseKind = 'hunk';
                else baseKind = classifyDiffBgColor(row);

                const attrNum = row.getAttribute('data-line') || row.getAttribute('data-line-number') || '';
                const subLines = rawBlock.split('\n');
                for (const sub of subLines) {
                  const trimmedEnd = sub.trimEnd();
                  const trimmedSub = trimmedEnd.trim();
                  if (!trimmedSub) continue;
                  if (/^[+−-]\s*\d+$/.test(trimmedSub)) continue;
                  if (/^\+\s*\d+\s+more\s+lines?$/i.test(trimmedSub)) {
                    pushLine('⋯', trimmedSub, 'hunk');
                    continue;
                  }
                  const numMatch = trimmedEnd.match(/^\s*(\d+)\s+(.*)$/);
                  let num = attrNum || (numMatch ? numMatch[1] : '');
                  let codeLine = numMatch ? numMatch[2] : trimmedEnd;
                  let kind = baseKind;
                  if (kind === 'context') {
                    if (/^\+(?!\+\+)/.test(codeLine)) { kind = 'added'; codeLine = codeLine.slice(1); }
                    else if (/^-(?!--)/.test(codeLine)) { kind = 'removed'; codeLine = codeLine.slice(1); }
                    else if (/^@@.*@@/.test(codeLine)) { kind = 'hunk'; }
                  }
                  pushLine(num, codeLine, kind);
                }
              }
              return lines;
            }

            // 2. Fallback: walk leaf inline-only block rows inside card (including inside any shadowRoot)
            const blockTags = new Set(['DIV', 'TR', 'TABLE', 'TBODY', 'SECTION', 'ARTICLE', 'UL', 'OL', 'LI', 'HEADER', 'FOOTER']);
            const isInlineOnly = (el) => !Array.from(el.children || []).some(ch => blockTags.has(ch.tagName));
            const allBlocks = queryAllDeep(card, 'div, pre, code').filter(r => {
              if (r === hdr || (hdr && hdr.contains(r)) || (r.contains && hdr && r.contains(hdr))) return false;
              if (!isInlineOnly(r)) return false;
              const t = (r.innerText || r.textContent || '').trim();
              return t.length > 0;
            });
            for (const row of allBlocks.slice(0, 260)) {
              const rawBlock = (row.innerText || row.textContent || '').replace(/\u00a0/g, ' ').replace(/\r/g, '');
              const rowKind = classifyDiffBgColor(row);
              for (const sub of rawBlock.split('\n')) {
                const trimmedEnd = sub.trimEnd();
                const trimmedSub = trimmedEnd.trim();
                if (!trimmedSub) continue;
                if (/^[+−-]\s*\d+$/.test(trimmedSub)) continue;
                if (/^\+\s*\d+\s+more\s+lines?$/i.test(trimmedSub)) {
                  pushLine('⋯', trimmedSub, 'hunk');
                  continue;
                }
                const numMatch = trimmedEnd.match(/^\s*(\d+)\s+(.*)$/);
                let num = numMatch ? numMatch[1] : '';
                let codeLine = numMatch ? numMatch[2] : trimmedEnd;
                let kind = rowKind;
                if (kind === 'context') {
                  if (/^\+(?!\+\+)/.test(codeLine)) { kind = 'added'; codeLine = codeLine.slice(1); }
                  else if (/^-(?!--)/.test(codeLine)) { kind = 'removed'; codeLine = codeLine.slice(1); }
                  else if (/^@@.*@@/.test(codeLine)) { kind = 'hunk'; }
                }
                pushLine(num, codeLine, kind);
              }
            }
            return lines;
          }

          function parseDiffFilesFromPanel(panel) {
            if (!panel) return [];
            const files = [];
            const allNodes = Array.from(panel.querySelectorAll('div, button, header, section'));
            const rawMatches = [];
            for (const el of allNodes) {
              if (!isVisible(el)) continue;
              const txt = cleanText(el.textContent);
              if (!txt || txt.length > 220) continue;
              if (!/\+\s*\d+\s*[-−]\s*\d+/.test(txt)) continue;
              if (/\bfiles?\s+changed\b/i.test(txt)) continue;
              const beforeStat = txt.replace(/\+\s*\d+\s*[-−]\s*\d+.*$/, '').trim();
              if (beforeStat.length < 2) continue;
              const hasBetterChild = Array.from(el.children).some(ch => {
                const ct = cleanText(ch.textContent);
                if (!/\+\s*\d+\s*[-−]\s*\d+/.test(ct)) return false;
                return ct.replace(/\+\s*\d+\s*[-−]\s*\d+.*$/, '').trim().length >= 2;
              });
              if (hasBetterChild) continue;
              rawMatches.push(el);
            }
            const headerNodes = rawMatches.filter(el => !rawMatches.some(other => other !== el && el.contains(other)));

            const extractNameAndPath = (hdr) => {
              const parts = [];
              const walker = document.createTreeWalker(hdr, NodeFilter.SHOW_TEXT);
              let n;
              while ((n = walker.nextNode())) {
                const t = cleanText(n.nodeValue);
                if (!t) continue;
                if (/^[+−-]$/.test(t) || /^\d+$/.test(t) || /^[+−-]\s*\d+$/.test(t) || /^\+\s*\d+\s*[-−]\s*\d+$/.test(t)) continue;
                if (parts[parts.length - 1] !== t) parts.push(t);
              }
              const fileName = parts[0] || '';
              const path = parts.slice(1).join(' ')
                .replace(/(?:^|\s)[+−-]\s*\d+(?:\s+[+−-]\s*\d+)?\s*$/g, '')
                .trim();
              return { fileName, path };
            };

            headerNodes.forEach((hdr, fIdx) => {
              const hdrText = cleanText(hdr.textContent);
              const statM = hdrText.match(/\+\s*(\d+)\s*[-−]\s*(\d+)/);
              const added = statM ? parseInt(statM[1], 10) : 0;
              const removed = statM ? parseInt(statM[2], 10) : 0;
              const parsed = extractNameAndPath(hdr);
              const fileName = parsed.fileName || ('File ' + (fIdx + 1));
              const path = parsed.path;

              // Walk upward ONLY while container contains strictly THIS single file header
              let card = hdr;
              while (card.parentElement && card.parentElement !== panel) {
                const parent = card.parentElement;
                const headersInsideParent = headerNodes.filter(other => parent.contains(other));
                if (headersInsideParent.length > 1) break;
                card = parent;
              }
              // If card still only wraps the header, check immediate nextElementSibling (common accordion layout)
              let diffScope = card;
              if (card === hdr || card.textContent.length < hdr.textContent.length + 12) {
                const nextSib = card.nextElementSibling;
                if (nextSib && !headerNodes.some(other => nextSib.contains(other))) {
                  diffScope = nextSib;
                }
              }

              const lines = (diffScope && diffScope !== hdr && diffScope !== panel)
                ? extractDeepDiffLines(diffScope, hdr, fileName, path)
                : [];

              files.push({
                id: 'diff-file-' + fIdx + '-' + fileName,
                fileName: fileName,
                path: path,
                addedLines: added,
                removedLines: removed,
                lines: lines.slice(0, 240)
              });
            });
            return files;
          }

          async function openReviewDiffs(cardTitle) {
            const sendDiff = payload => {
              try {
                if (window.AntigravityNative && window.AntigravityNative.onDiffReviewLoaded) {
                  window.AntigravityNative.onDiffReviewLoaded(JSON.stringify(payload));
                }
              } catch (_) {}
            };

            if (isInternalModalProbe) {
              await waitFor(() => !isInternalModalProbe, 1200, 40);
            }
            isInternalModalProbe = true;
            try {
              const root = getAppRoot();
              let opened = false;
              const diffCards = Array.from(root.querySelectorAll('[data-testid="changed-files-card"]'));
              const matchCard = cardTitle
                ? diffCards.find(c => cleanText(c.textContent).toLowerCase().includes(cardTitle.toLowerCase()))
                : diffCards[diffCards.length - 1];
              if (matchCard) {
                const reviewBtn = Array.from(matchCard.querySelectorAll('button')).find(b => /review/i.test(cleanText(b.textContent))) || matchCard;
                simulateTap(reviewBtn);
                opened = true;
              } else {
                const toggleBtn = root.querySelector('[data-testid="mobile-toggle-aux-sidebar"]');
                if (toggleBtn) {
                  simulateTap(toggleBtn);
                  opened = true;
                }
              }

              if (!opened) {
                sendDiff({ title: 'Review Changes', files: [], error: 'No changed-files panel in this conversation' });
                return false;
              }

              const panel = await waitFor(() => findAuxPanelRoot(), 1800, 60);
              if (!panel) {
                sendDiff({ title: 'Review Changes', files: [], error: 'Review panel did not open' });
                return false;
              }

              // If Overview tab opened instead of Review tab, click the Review tab button
              if (!/\+\d+\s*[-−]\d+/.test(cleanText(panel.textContent))) {
                const reviewTab = Array.from(panel.querySelectorAll('button, [role="tab"]'))
                  .find(b => /review|files changed|diff/i.test((b.getAttribute('aria-label') || '') + ' ' + cleanText(b.textContent)));
                if (reviewTab) {
                  simulateTap(reviewTab);
                }
              }

              await waitFor(() => {
                const p = findAuxPanelRoot();
                return p && /\+\s*\d+\s*[-−]\s*\d+/.test(cleanText(p.textContent));
              }, 2600, 80);

              // Give diff code lines / Shadow DOM diff components time to finish mounting
              await waitFor(() => {
                const p = findAuxPanelRoot();
                if (!p) return false;
                const parsed = parseDiffFilesFromPanel(p);
                return parsed.some(f => f.lines && f.lines.length > 0);
              }, 2200, 120);

              const activePanel = findAuxPanelRoot() || panel;
              const files = parseDiffFilesFromPanel(activePanel);
              const panelText = cleanText(activePanel.textContent);
              const isPaused = /environment\s+paused/i.test(panelText) && files.length === 0;
              const summaryHeading = cleanText((activePanel.querySelector('h1, h2, h3') || {}).textContent || '') || 'Review Changes';
              closeAuxPanel();
              await sleep(160);
              sendDiff({
                title: cardTitle || (isPaused ? 'Review Changes' : summaryHeading),
                subtitle: files.length > 0 ? (files.length + ' file' + (files.length === 1 ? '' : 's') + ' changed') : '',
                files: files,
                error: files.length === 0
                  ? (isPaused
                      ? 'Environment paused — tap a "files changed" card in the conversation (or Load earlier messages) to inspect historical diffs.'
                      : 'No file diffs available for this turn')
                  : ''
              });
              return true;
            } catch (e) {
              closeAuxPanel();
              sendDiff({ title: 'Review Changes', files: [], error: String(e) });
              return false;
            } finally {
              isInternalModalProbe = false;
              syncNow(false);
            }
          }

          async function openDocumentViewer(targetTitle) {
            const sendDoc = payload => {
              try {
                if (window.AntigravityNative && window.AntigravityNative.onDocumentLoaded) {
                  window.AntigravityNative.onDocumentLoaded(JSON.stringify(payload));
                }
              } catch (_) {}
            };

            if (isInternalModalProbe) {
              await waitFor(() => !isInternalModalProbe, 1200, 40);
            }
            isInternalModalProbe = true;
            try {
              const root = getAppRoot();
              const lowerTarget = (targetTitle || '').trim().toLowerCase();
              let clicked = false;

              // 1. Check artifact cards in conversation (skip external URL banners!)
              const artCards = Array.from(root.querySelectorAll('[data-testid="artifact-card"]'));
              const artMatch = artCards.find(c => cleanText(c.textContent).toLowerCase().includes(lowerTarget));
              if (artMatch) {
                const anchor = artMatch.matches('a[href]') ? artMatch : (artMatch.querySelector('a[href]') || artMatch.closest('a[href]'));
                const href = anchor ? (anchor.getAttribute('href') || '') : '';
                if (href && /^https?:\/\//i.test(href)) {
                  sendDoc({ title: targetTitle, markdown: '', raw: '', error: 'EXTERNAL_LINK:' + href });
                  return false;
                }
                const btn = artMatch.querySelector('button') || artMatch;
                simulateTap(btn);
                clicked = true;
              }

              // 2. Check inline file pills / buttons inside conversation
              if (!clicked && lowerTarget) {
                const pills = Array.from(root.querySelectorAll('[data-testid="conversation-view"] [data-testid="entity-pill"], [data-testid="conversation-view"] button, [data-testid="conversation-view"] a'))
                  .filter(isVisible);
                const pillMatch = pills.find(el => {
                  const t = cleanText(el.textContent).toLowerCase();
                  return t === lowerTarget || t.endsWith('/' + lowerTarget) || lowerTarget.endsWith('/' + t);
                });
                if (pillMatch) {
                  simulateTap(pillMatch);
                  clicked = true;
                }
              }

              // 3. Fallback: open side panel Overview -> expand Artifacts ("See all") -> click matching artifact
              if (!clicked && lowerTarget) {
                const toggleBtn = root.querySelector('[data-testid="mobile-toggle-aux-sidebar"]');
                if (toggleBtn) {
                  simulateTap(toggleBtn);
                  const ovPanel = await waitFor(() => findAuxPanelRoot(), 1800, 60);
                  if (ovPanel) {
                    const seeAllBtn = Array.from(ovPanel.querySelectorAll('button')).filter(isVisible)
                      .find(b => /^see all\b/i.test(cleanText(b.textContent)));
                    if (seeAllBtn) {
                      simulateTap(seeAllBtn);
                      await sleep(250);
                    }
                    const artBtn = Array.from(ovPanel.querySelectorAll('button, a')).filter(isVisible)
                      .find(b => {
                        const t = cleanText(b.textContent).toLowerCase();
                        return t === lowerTarget || t.includes(lowerTarget);
                      });
                    if (artBtn) {
                      simulateTap(artBtn);
                      clicked = true;
                    } else {
                      closeAuxPanel();
                    }
                  }
                }
              }

              if (!clicked) {
                sendDoc({ title: targetTitle, markdown: '', raw: '', error: 'Document trigger not found in current view' });
                return false;
              }

              const panel = await waitFor(() => findAuxPanelRoot(), 2000, 60);
              if (!panel) {
                sendDoc({ title: targetTitle, markdown: '', raw: '', error: 'Document viewer did not open' });
                return false;
              }

              // Find the active Document Viewer pane specifically (NOT the hidden Overview tab with Artifacts!)
              const findDocPaneBody = (p) => {
                if (!p) return null;
                const buttons = Array.from(p.querySelectorAll('button')).filter(isVisible);
                const previewBtn = buttons.find(b => /^preview$/i.test(cleanText(b.textContent)));
                const rawBtn = buttons.find(b => /^raw$/i.test(cleanText(b.textContent)));
                const toggleAnchor = previewBtn || rawBtn;
                if (toggleAnchor) {
                  // Walk up past the Preview/Raw toolbar to find the sibling content area OR enclosing pane
                  let cur = toggleAnchor.parentElement;
                  while (cur && cur !== p) {
                    const sibs = Array.from(cur.parentElement ? cur.parentElement.children : []);
                    const bodySib = sibs.find(s => {
                      if (s === cur || !isVisible(s)) return false;
                      const sr = s.getBoundingClientRect();
                      if (sr.height < 80) return false;
                      const t = cleanText(s.innerText || s.textContent);
                      return t.length > 20 && !/^subagents\b.*\bfiles changed\b/i.test(t);
                    });
                    if (bodySib) {
                      return { docBody: bodySib, rawBtn, previewBtn, stripToolbar: false };
                    }
                    // If cur itself already holds rich content (e.g. sticky toolbar inside scroll pane)
                    const curText = cleanText(cur.innerText || cur.textContent);
                    const hasHeadingsOrPre = !!cur.querySelector('h1, h2, h3, p, pre, ul, ol, code, .cm-content');
                    if (hasHeadingsOrPre && curText.length > 60 && !/\bsubagents\b.*\bfiles changed\b/i.test(curText)) {
                      return { docBody: cur, rawBtn, previewBtn, stripToolbar: true };
                    }
                    cur = cur.parentElement;
                  }
                }
                // Fallback: pick the LARGEST visible markdown/document container (never a lone nested <pre> code block!)
                const candidates = Array.from(p.querySelectorAll('article, .prose, [class*="markdown"], [data-testid*="markdown"], .cm-content, [role="tabpanel"]'))
                  .filter(el => {
                    if (!isVisible(el)) return false;
                    if (el.tagName === 'PRE' || el.tagName === 'CODE') return false;
                    const t = cleanText(el.innerText || el.textContent);
                    return t.length > 40 && !/\bsubagents\b.*\bfiles changed\b/i.test(t);
                  })
                  .sort((a, b) => cleanText(b.innerText || b.textContent).length - cleanText(a.innerText || a.textContent).length);
                if (candidates.length > 0) {
                  return { docBody: candidates[0], rawBtn, previewBtn, stripToolbar: true };
                }
                // Secondary fallback: walk up from the first visible heading (h1/h2) to its document container
                const heading = Array.from(p.querySelectorAll('h1, h2')).find(el => isVisible(el));
                if (heading) {
                  let docRoot = heading.parentElement;
                  while (docRoot && docRoot.parentElement && docRoot.parentElement !== p && !/\bsubagents\b.*\bfiles changed\b/i.test(cleanText(docRoot.parentElement.textContent))) {
                    docRoot = docRoot.parentElement;
                  }
                  if (docRoot && docRoot !== p) {
                    return { docBody: docRoot, rawBtn, previewBtn, stripToolbar: true };
                  }
                }
                return null;
              };

              // Wait past the loading spinner until real document content mounts inside the active document body
              await waitFor(() => {
                const p = findAuxPanelRoot();
                const found = findDocPaneBody(p);
                if (!found || !found.docBody) return false;
                const hasSpinner = !!found.docBody.querySelector('[role="progressbar"], .animate-spin');
                const txt = cleanText(found.docBody.innerText || found.docBody.textContent);
                return !hasSpinner && txt.length > 30 && !/^loading/i.test(txt);
              }, 4500, 100);

              const extractCleanDoc = (foundObj) => {
                if (!foundObj || !foundObj.docBody) return { md: '', raw: '' };
                const clone = foundObj.docBody.cloneNode(true);
                // Always strip embedded <style>/<script>/nav/tablist chrome so CSS never leaks into Raw view
                clone.querySelectorAll('style, script, noscript, [role="tablist"], nav').forEach(el => {
                  if (el.parentNode) el.parentNode.removeChild(el);
                });
                if (foundObj.stripToolbar) {
                  // Strip the toolbar / breadcrumb bar containing Preview/Raw or file path crumbs
                  const cloneBtns = Array.from(clone.querySelectorAll('button'));
                  const tbAnchor = cloneBtns.find(b => /^(preview|raw)$/i.test(cleanText(b.textContent)));
                  if (tbAnchor) {
                    let tb = tbAnchor.parentElement;
                    while (tb && tb.parentElement && tb.parentElement !== clone && !tb.querySelector('h1, h2, p, ul, ol, pre, .cm-content')) {
                      tb = tb.parentElement;
                    }
                    // Also remove any sibling header/breadcrumb rows right before tb
                    if (tb && tb.parentNode) {
                      let prev = tb.previousElementSibling;
                      while (prev && !prev.querySelector('h1, h2, p, ul, ol, pre, .cm-content')) {
                        const toRemove = prev;
                        prev = prev.previousElementSibling;
                        if (toRemove.parentNode) toRemove.parentNode.removeChild(toRemove);
                      }
                      if (tb !== clone) tb.parentNode.removeChild(tb);
                    }
                  }
                }
                const mdOut = normalizeMarkdown(nodeToMarkdown(clone, 0));
                const rawOut = (clone.textContent || '')
                  .replace(/\r/g, '')
                  .replace(/\n{3,}/g, '\n\n')
                  .trim();
                return { md: mdOut, raw: mdOut || rawOut };
              };

              const activePanel = findAuxPanelRoot() || panel;
              const found = findDocPaneBody(activePanel);
              let md = '';
              let rawText = '';

              if (found && found.docBody) {
                const initial = extractCleanDoc(found);
                md = initial.md;
                rawText = initial.raw;
                // If Raw toggle button exists, briefly tap Raw to capture exact raw markdown source!
                if (found.rawBtn) {
                  simulateTap(found.rawBtn);
                  await sleep(280);
                  const afterRaw = findDocPaneBody(findAuxPanelRoot() || activePanel);
                  if (afterRaw && afterRaw.docBody) {
                    const rawExtracted = extractCleanDoc(afterRaw);
                    const candidateRaw = rawExtracted.raw;
                    if (candidateRaw.length > 20) {
                      rawText = candidateRaw;
                      if (!md || md.length < candidateRaw.length * 0.4) {
                        md = candidateRaw;
                      }
                    }
                  }
                }
              }
              closeAuxPanel();
              await sleep(160);
              sendDoc({
                title: targetTitle,
                markdown: md || rawText,
                raw: rawText || md,
                error: (!md && !rawText) ? 'Document content could not be read (environment may be paused)' : ''
              });
              return true;
            } catch (e) {
              closeAuxPanel();
              sendDoc({ title: targetTitle, markdown: '', raw: '', error: String(e) });
              return false;
            } finally {
              isInternalModalProbe = false;
              syncNow(false);
            }
          }

          async function fetchWorkspaceOverview() {
            const sendOverview = items => {
              try {
                if (window.AntigravityNative && window.AntigravityNative.onWorkspaceOverviewLoaded) {
                  window.AntigravityNative.onWorkspaceOverviewLoaded(JSON.stringify({ artifacts: items }));
                }
              } catch (_) {}
            };

            if (isInternalModalProbe) {
              await waitFor(() => !isInternalModalProbe, 1200, 40);
            }
            isInternalModalProbe = true;
            try {
              const toggleBtn = getAppRoot().querySelector('[data-testid="mobile-toggle-aux-sidebar"]');
              if (!toggleBtn) {
                sendOverview([]);
                return false;
              }
              simulateTap(toggleBtn);
              const panel = await waitFor(() => findAuxPanelRoot(), 1800, 60);
              if (!panel) {
                sendOverview([]);
                return false;
              }
              await waitFor(() => {
                const p = findAuxPanelRoot();
                return p && p.querySelectorAll('a, button, [role="button"], [role="listitem"]').length > 3;
              }, 1600, 80);
              let activeOverview = findAuxPanelRoot() || panel;
              // Always switch the aux panel to its Overview tab (first icon tab on the left) in case a document/review tab was left open
              const panelTop = activeOverview.getBoundingClientRect().top;
              const topTabBtns = Array.from(activeOverview.querySelectorAll('button, [role="tab"]'))
                .filter(b => {
                  if (!isVisible(b)) return false;
                  const r = b.getBoundingClientRect();
                  return r.top >= panelTop && r.top <= panelTop + 88 && r.width <= 56 && r.height <= 56 && r.left < 200;
                })
                .sort((a, b) => a.getBoundingClientRect().left - b.getBoundingClientRect().left);
              if (topTabBtns.length > 0 && !/subagents|files changed|artifacts/i.test(cleanText(activeOverview.textContent))) {
                simulateTap(topTabBtns[0]);
                await sleep(320);
                activeOverview = findAuxPanelRoot() || activeOverview;
              }
              // Expand "See all (N)" under Artifacts if present so all artifact items are listed
              const seeAllBtn = Array.from(activeOverview.querySelectorAll('button, [role="button"]'))
                .filter(isVisible)
                .find(b => /^see all\b/i.test(cleanText(b.textContent)));
              if (seeAllBtn) {
                simulateTap(seeAllBtn);
                await sleep(320);
              }
              const items = [];
              const seen = new Set();
              const isSectionOrControl = (text) => {
                const normalized = text.replace(/\s*\(?\d+\)?\s*$/g, '').trim();
                return /^(see all|see less|see more|show all|show less|show more|uncommitted|staged|unstaged|changes|tracked|untracked|all files|subagents|files changed|artifacts|uploads|background tasks|terminals|close|back|overview|files|review|terminal|workspace|preview|raw|no artifacts.*|no files.*|create.*|\+)$/i.test(normalized);
              };
              const isDocOrExternalRow = (el, firstLine, href) => {
                if (href && /^https?:\/\//i.test(href)) return true;
                if (/\.[a-z0-9]{1,8}$/i.test(firstLine) && !/\s/.test(firstLine)) return true;
                if (el.closest('[data-testid*="artifact"]') || el.matches('[data-testid*="artifact"]')) return true;
                return false;
              };
              const clickableRows = Array.from(activeOverview.querySelectorAll('a, button, [role="button"], [role="listitem"], [data-testid*="artifact"]'))
                .filter(el => {
                  if (!isVisible(el)) return false;
                  const r = el.getBoundingClientRect();
                  if (r.top <= panelTop + 84) return false;
                  return true;
                });
              clickableRows.forEach((el, idx) => {
                const lines = extractLinesFromNode(el);
                const firstLine = (lines[0] || cleanText(el.textContent)).trim();
                if (!firstLine || firstLine.length < 2) return;
                if (isSectionOrControl(firstLine) || isSectionOrControl(cleanText(el.textContent))) return;
                const anchor = el.matches('a[href]') ? el : (el.querySelector('a[href]') || el.closest('a[href]'));
                const href = (anchor && anchor.getAttribute('href')) || el.getAttribute('href') || '';
                if (!isDocOrExternalRow(el, firstLine, href)) return;
                const key = firstLine + '|' + href;
                if (seen.has(key)) return;
                seen.add(key);
                items.push({
                  id: 'ws-' + idx,
                  title: firstLine,
                  subtitle: lines.slice(1).join(' '),
                  url: href && /^https?:\/\//i.test(href) ? href : '',
                  kind: href ? 'link' : 'artifact'
                });
              });
              closeAuxPanel();
              await sleep(160);
              sendOverview(items.slice(0, 30));
              return true;
            } catch (_) {
              closeAuxPanel();
              sendOverview([]);
              return false;
            } finally {
              isInternalModalProbe = false;
              syncNow(false);
            }
          }

          async function refreshProjects() {
            const trigger = getAppRoot().querySelector('[data-testid="project-selector-trigger"]');
            if (!trigger || isInternalModalProbe) return false;
            isInternalModalProbe = true;
            try {
              simulateTap(trigger);
              const sheet = await waitFor(() => findAuxPanelRoot(), 1200, 40);
              if (sheet) {
                const currentProj = parseAriaCurrentValue('[data-testid="project-selector-trigger"]');
                const btns = Array.from(sheet.querySelectorAll('button, [role="menuitem"]')).filter(isVisible);
                const found = [];
                const seen = new Set();
                btns.forEach((b, idx) => {
                  const name = cleanText(b.textContent);
                  if (!name || /^create project$/i.test(name) || seen.has(name)) return;
                  seen.add(name);
                  found.push({
                    id: 'proj-' + idx + '-' + name,
                    name: name,
                    isSelected: (name.toLowerCase() === currentProj.toLowerCase()) ||
                      (currentProj === '' && /^no project$/i.test(name))
                  });
                });
                if (found.length > 0) cachedProjects = found;
              }
              dismissOverlays();
              await sleep(150);
            } finally {
              isInternalModalProbe = false;
              syncNow(true);
            }
            return true;
          }

          async function selectProject(projectName) {
            const trigger = getAppRoot().querySelector('[data-testid="project-selector-trigger"]');
            if (!trigger) return false;
            if (isInternalModalProbe) await waitFor(() => !isInternalModalProbe, 1200, 40);
            isInternalModalProbe = true;
            try {
              simulateTap(trigger);
              const sheet = await waitFor(() => findAuxPanelRoot(), 1200, 40);
              if (sheet) {
                const target = (projectName || 'No Project').toLowerCase().trim();
                const match = Array.from(sheet.querySelectorAll('button, [role="menuitem"]'))
                  .filter(isVisible)
                  .find(b => cleanText(b.textContent).toLowerCase() === target || cleanText(b.textContent).toLowerCase().includes(target));
                if (match) {
                  simulateTap(match);
                  await sleep(280);
                } else {
                  dismissOverlays();
                }
              }
            } finally {
              isInternalModalProbe = false;
              syncNow(true);
            }
            return true;
          }

          async function selectPermissions(preset) {
            const trigger = getAppRoot().querySelector('[data-testid="permissions-selector-trigger"]');
            if (!trigger) return false;
            if (isInternalModalProbe) await waitFor(() => !isInternalModalProbe, 1200, 40);
            isInternalModalProbe = true;
            try {
              simulateTap(trigger);
              const sheet = await waitFor(() => findAuxPanelRoot(), 1200, 40);
              if (sheet) {
                const target = (preset || '').toLowerCase().trim();
                const match = Array.from(sheet.querySelectorAll('button, [role="menuitem"], [role="option"]'))
                  .filter(isVisible)
                  .find(b => cleanText(b.textContent).toLowerCase().includes(target));
                if (match) {
                  simulateTap(match);
                  await sleep(260);
                } else {
                  dismissOverlays();
                }
              }
            } finally {
              isInternalModalProbe = false;
              syncNow(true);
            }
            return true;
          }

          async function runThreadAction(action, arg) {
            const moreBtn = getAppRoot().querySelector('[data-testid="titlebar-more-actions"]');
            if (!moreBtn) return false;
            if (isInternalModalProbe) await waitFor(() => !isInternalModalProbe, 1200, 40);
            isInternalModalProbe = true;
            try {
              simulateTap(moreBtn);
              const sheet = await waitFor(() => findAuxPanelRoot(), 1200, 40);
              if (!sheet) return false;
              const target = (action || '').toLowerCase();
              const match = Array.from(sheet.querySelectorAll('button, [role="menuitem"]'))
                .filter(isVisible)
                .find(b => cleanText(b.textContent).toLowerCase().includes(target));
              if (!match) {
                dismissOverlays();
                return false;
              }
              simulateTap(match);
              await sleep(260);
              if (target === 'rename' && arg) {
                const input = await waitFor(() => document.querySelector('[role="dialog"] input, input[type="text"]'), 1000, 50);
                if (input) {
                  const desc = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
                  if (desc && desc.set) desc.set.call(input, arg);
                  else input.value = arg;
                  input.dispatchEvent(new Event('input', { bubbles: true }));
                  input.dispatchEvent(new Event('change', { bubbles: true }));
                  await sleep(120);
                  const saveBtn = Array.from(document.querySelectorAll('[role="dialog"] button, button'))
                    .filter(isVisible)
                    .find(b => /^(save|rename|ok|confirm)$/i.test(cleanText(b.textContent)));
                  if (saveBtn) simulateTap(saveBtn);
                  else input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, bubbles: true }));
                  await sleep(300);
                }
              }
            } finally {
              isInternalModalProbe = false;
              syncNow(true);
            }
            return true;
          }

          const observer = new MutationObserver(scheduleSync);
          if (document.body) {
            observer.observe(document.body, { childList: true, subtree: true, characterData: true });
          }

          window.__agBridge = {
            version: BRIDGE_VERSION,
            syncNow: () => syncNow(true),
            focusComposer: focusComposer,
            submitPromptAfterCommit: submitPromptAfterCommit,
            sendPrompt: sendPrompt,
            stopGeneration: stopGeneration,
            refreshModels: refreshModels,
            selectModel: selectModel,
            refreshQuota: refreshQuota,
            startNewChat: startNewChat,
            selectConversation: selectConversation,
            loadOlderMessages: loadOlderMessages,
            expandReasoningBlock: expandReasoningBlock,
            openExternalArtifact: openExternalArtifact,
            openReviewDiffs: openReviewDiffs,
            openDocumentViewer: openDocumentViewer,
            fetchWorkspaceOverview: fetchWorkspaceOverview,
            refreshProjects: refreshProjects,
            selectProject: selectProject,
            selectPermissions: selectPermissions,
            runThreadAction: runThreadAction,
            triggerFileUpload: triggerFileUpload,
            dumpDomSnapshot: dumpDomSnapshot
          };

          syncNow(true);
          setTimeout(() => syncNow(true), 900);
          let hydrateTicks = 0;
          const hydrateInterval = setInterval(() => {
            hydrateTicks++;
            syncNow(false);
            const trigger = findModelSelectorButton();
            if (trigger) {
              if (!modelsPrewarmed && cachedModels.length === 0) {
                modelsPrewarmed = true;
                setTimeout(() => prewarmModelsAndQuota(), 450);
              }
              clearInterval(hydrateInterval);
            } else if (hydrateTicks >= 8) {
              clearInterval(hydrateInterval);
            }
          }, 1500);
        })();
    """.trimIndent()

    fun escapeJsString(raw: String): String {
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
