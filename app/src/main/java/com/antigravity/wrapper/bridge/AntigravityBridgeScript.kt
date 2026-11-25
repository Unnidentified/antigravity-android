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
          if (window.__agBridge && window.__agBridge.version === 7) {
            window.__agBridge.syncNow();
            return;
          }

          const BRIDGE_VERSION = 7;
          let cachedModels = [];
          let cachedConversations = [];
          let cachedQuota = {
            usedPercent: null,
            remainingLabel: '',
            resetLabel: '',
            planLabel: 'Antigravity',
            rawSummary: '',
            isLive: false
          };
          let syncTimer = null;
          let streamingHeartbeatTimer = null;
          let streamingHeartbeatTicks = 0;
          let lastQuotaDiag = '';
          let lastPayloadHash = '';
          let isInternalModalProbe = false;
          let modelsPrewarmed = false;

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
                tid === 'revert-button') {
              return true;
            }
            const tag = (el.tagName || '').toLowerCase();
            if (tag === 'fieldset' && /response actions/i.test(el.getAttribute('aria-label') || '')) return true;
            return false;
          }

          function isRelativeTimestampText(txt) {
            return /^(\d+\s*(?:s|sec|secs|m|min|mins|h|hr|hrs|d|w)\s*ago|\d+\s*[smhdw]|just now|now)$/i.test(txt);
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

            // Preserve Antigravity entity pills (files, symbols, PRs) cleanly instead of stripping buttons!
            const tid = (node.getAttribute('data-testid') || '').toLowerCase();
            if (tid === 'entity-pill') {
              const label = cleanText(node.textContent);
              if (!label) return '';
              const anchor = tag === 'a' ? node : (node.querySelector && node.querySelector('a'));
              const href = anchor ? (anchor.getAttribute('href') || '') : '';
              if (href && (href.startsWith('http://') || href.startsWith('https://'))) {
                return ' [' + label + '](' + href + ') ';
              }
              return ' `' + label + '` ';
            }

            if (tag === 'button' && cleanText(node.textContent).length <= 24) return '';

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
              return tex ? (' $' + tex + '$ ') : '';
            }

            if (tag === 'pre') {
              const codeEl = node.querySelector('code') || node;
              const codeCls = (codeEl.className || '').toString();
              const langMatch = codeCls.match(/language-([a-zA-Z0-9_+-]+)/);
              const lang = langMatch ? langMatch[1] : '';
              const rawCode = (codeEl.textContent || '').replace(/^\n+|\n+$/g, '');
              return '\n\n```' + lang + '\n' + rawCode + '\n```\n\n';
            }

            if (tag === 'code') {
              const txt = (node.textContent || '').replace(/`/g, "'").trim();
              return txt ? (' `' + txt + '` ') : '';
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
                return t ? (' **' + t + '** ') : '';
              }
              case 'em':
              case 'i': {
                const t = childrenMd.trim();
                return t ? (' *' + t + '* ') : '';
              }
              case 'del':
              case 's': {
                const t = childrenMd.trim();
                return t ? (' ~~' + t + '~~ ') : '';
              }
              case 'a': {
                const href = node.getAttribute('href') || '';
                const label = cleanText(childrenMd) || href;
                if (href && (href.startsWith('http://') || href.startsWith('https://'))) {
                  return ' [' + label + '](' + href + ') ';
                }
                return label ? (' ' + label + ' ') : '';
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
                const items = Array.from(node.children)
                  .filter(c => (c.tagName || '').toLowerCase() === 'li')
                  .map(li => '- ' + cleanText(nodeToMarkdown(li, depth + 1)))
                  .filter(s => s.length > 2);
                return items.length ? ('\n\n' + items.join('\n') + '\n\n') : '';
              }
              case 'ol': {
                const items = Array.from(node.children)
                  .filter(c => (c.tagName || '').toLowerCase() === 'li')
                  .map((li, idx) => (idx + 1) + '. ' + cleanText(nodeToMarkdown(li, depth + 1)))
                  .filter(s => s.length > 3);
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
            return (md || '')
              .replace(/^\s*(?:You said|Agent said)\s*/i, '')
              .replace(/[ \t]{2,}/g, ' ')
              .replace(/ ([.,;:!?])/g, '$1')
              .replace(/\(\s*,\s*(?:,\s*)*\)/g, '')
              .replace(/\n{3,}/g, '\n\n')
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

          function extractReasoningBlocks(container, msgIndex) {
            const blocks = [];
            const seen = new Set();
            const nodes = Array.from(container.querySelectorAll('[data-testid="worked-for-collapsible"], details'));
            nodes.forEach((el, idx) => {
              if (seen.has(el)) return;
              seen.add(el);

              const summaryEl = el.querySelector('summary, button, [role="button"]');
              const rawSummary = summaryEl ? cleanText(summaryEl.textContent) : '';
              const durationMatch = rawSummary.match(/(\d+\s*(?:ms|s|sec|secs|seconds|m|min))/i);
              const duration = durationMatch ? durationMatch[1] : '';
              const title = rawSummary || 'Reasoning';

              const clone = el.cloneNode(true);
              const cloneSummary = clone.querySelector('summary, button, [role="button"]');
              if (cloneSummary && cloneSummary.parentNode) {
                cloneSummary.parentNode.removeChild(cloneSummary);
              }
              const body = normalizeMarkdown(nodeToMarkdown(clone, 0)) || cleanText(clone.textContent);
              blocks.push({
                id: 'reason-' + msgIndex + '-' + idx,
                title: title,
                duration: duration,
                content: body || 'Reasoning steps completed.',
                isStreaming: /thinking|working|reasoning\.\.\./i.test(rawSummary)
              });
            });
            return blocks;
          }

          function extractArtifacts(container, msgIndex) {
            const artifacts = [];
            const artCards = Array.from(container.querySelectorAll('[data-testid="artifact-card"]'));
            artCards.forEach((card, idx) => {
              const header = card.querySelector('[data-testid="artifact-card-header"]');
              const title = cleanText(header ? header.textContent : card.textContent).slice(0, 80);
              const anchor = card.querySelector('a[href]');
              const url = anchor ? (anchor.getAttribute('href') || '') : '';
              const fullText = cleanText(card.textContent);
              const subtitle = title ? fullText.replace(title, '').replace(/\bOpen\b/i, '').trim().slice(0, 120) : '';
              if (title) {
                artifacts.push({
                  id: 'art-' + msgIndex + '-' + idx,
                  title: title,
                  subtitle: subtitle,
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
                  id: 'diff-' + msgIndex + '-' + idx,
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
            const raw = m && m[1] ? cleanText(m[1]) : cleanText(btn.textContent);
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
            const messages = [];

            turnNodes.forEach((el, idx) => {
              const tid = (el.getAttribute('data-testid') || '').toLowerCase();
              const isUser = tid === 'user-message';
              const timestamp = extractMessageTimestamp(el);

              if (isUser) {
                const contentEl = el.querySelector('[data-testid="user-message-content"]') || el;
                const md = normalizeMarkdown(nodeToMarkdown(contentEl, 0));
                if (!md) return;
                messages.push({
                  id: el.id || ('user-' + idx + '-' + md.slice(0, 12)),
                  role: 'user',
                  markdown: md,
                  timestamp: timestamp,
                  reasoning: [],
                  artifacts: [],
                  isStreaming: false
                });
              } else {
                const reasoning = extractReasoningBlocks(el, idx);
                const artifacts = extractArtifacts(el, idx);
                const md = normalizeMarkdown(nodeToMarkdown(el, 0));
                if (!md && reasoning.length === 0 && artifacts.length === 0) return;
                messages.push({
                  id: el.id || ('agent-' + idx + '-' + md.slice(0, 12)),
                  role: 'assistant',
                  markdown: md,
                  timestamp: timestamp,
                  reasoning: reasoning,
                  artifacts: artifacts,
                  isStreaming: false
                });
              }
            });

            return messages;
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
                items.push({
                  id: 'effort-' + idx + '-' + name,
                  name: name,
                  tier: tier,
                  description: '',
                  hasEffortLevels: true,
                  effortOptions: ['Low', 'Medium', 'High', 'Extra'],
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
                  id: 'item-' + idx + '-' + name,
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

            let usedPct = null;
            const pb = pane.querySelector('[role="progressbar"], meter');
            if (pb) {
              const now = parseFloat(pb.getAttribute('aria-valuenow') || pb.value || '');
              const max = parseFloat(pb.getAttribute('aria-valuemax') || pb.max || '100');
              if (!isNaN(now) && max > 0) usedPct = Math.round((now / max) * 100);
            }
            if (usedPct === null) {
              const pctMatch = joined.match(/(\d{1,3})\s*%/);
              if (pctMatch) usedPct = parseFloat(pctMatch[1]);
            }

            const resetLine = rawLines.find(l => /resets?\s+(?:in|at|on)/i.test(l)) || '';
            const statusLine = rawLines.find(l => l !== resetLine && /(\d+\s*%|remaining|used|quota|limit|available|credits|unlimited)/i.test(l))
              || rawLines.filter(l => l !== resetLine).slice(0, 2).join(' • ')
              || '';
            const planLine = rawLines.find(l => /(ultra|pro|standard|free|enterprise|workspace|plan)/i.test(l) && l.length < 36) || 'Antigravity';

            return {
              usedPercent: usedPct,
              remainingLabel: statusLine.slice(0, 88),
              resetLabel: resetLine.slice(0, 48),
              planLabel: planLine,
              rawSummary: statusLine.slice(0, 96),
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

            const diagBase = 'turns=' + messages.length + ', chats=' + conversations.length + ', models=' + cachedModels.length + ', composer=' + (!!composer);
            return {
              url: window.location.href,
              title: title,
              authRequired: authRequired,
              isStreaming: isStreaming,
              activeModel: activeModel,
              activeProject: activeProject,
              permissionsPreset: permissionsPreset,
              models: cachedModels,
              conversations: conversations,
              messages: messages,
              quota: cachedQuota,
              composerReady: !!composer,
              diagnosticsNote: lastQuotaDiag ? (diagBase + ', ' + lastQuotaDiag) : diagBase
            };
          }

          function syncNow(force) {
            if (isInternalModalProbe && !force) return;
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
                await sleep(300);
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

              const optionSpans = await waitFor(() => {
                const opts = Array.from(document.querySelectorAll('[data-testid="model-selector-effort-option"]'))
                  .filter(isVisible);
                return opts.length > 0 ? opts : null;
              }, 1000, 40);

              if (optionSpans) {
                const desiredTier = (effortTier || 'Medium').toLowerCase().trim();
                const match =
                  optionSpans.find(s => cleanText(s.textContent).toLowerCase() === desiredTier) ||
                  optionSpans.find(s => cleanText(s.textContent).toLowerCase() === 'medium') ||
                  optionSpans[0];
                if (match) {
                  simulateTap(match.closest('button') || match);
                  await sleep(300);
                }
              }

              if (document.querySelector('[data-testid="bottom-sheet-backdrop"]')) {
                dismissOverlays();
                await sleep(140);
              }
            } finally {
              isInternalModalProbe = false;
              syncNow(true);
            }
            return true;
          }

          function findUsageContainerFromSheet(panel, allowLoose) {
            const usageSignalRe = /(%|resets?\s+(?:in|at|on)|quota|usage|remaining|limit|credits|available|unlimited)/i;
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
                  const hasBar = !!sib.querySelector('[role="progressbar"], meter');
                  const txt = cleanText(sib.innerText || sib.textContent);
                  if (hasBar || (txt.length > 4 && !/thinking effort/i.test(txt) && (usageSignalRe.test(txt) || (allowLoose && txt.length > 10)))) {
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
                const hasBar = !!dialogs[i].querySelector('[role="progressbar"], meter');
                const txt = cleanText(dialogs[i].innerText || dialogs[i].textContent);
                if (hasBar || (txt.length > 4 && (usageSignalRe.test(txt) || (allowLoose && txt.length > 10)))) {
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
              const hasBar = !!clone.querySelector('[role="progressbar"], meter');
              const txt = cleanText(clone.textContent);
              if (hasBar || (txt.length > 6 && !/thinking effort/i.test(txt) && (usageSignalRe.test(txt) || (allowLoose && txt.length > 14)))) {
                return clone;
              }
            }

            // 4. Fallback: check non-chat overlay inside appRoot (e.g. if Usage renders as an inline pane)
            const root = getAppRoot();
            const bars = Array.from(root.querySelectorAll('[role="progressbar"], meter')).filter(b => {
              if (!isVisible(b)) return false;
              return !b.closest('[data-testid="conversation-view"]') && !b.closest('[data-testid="conversation-list-sidebar"]');
            });
            if (bars.length > 0) {
              return bars[0].closest('section, article, [role="dialog"], div') || bars[0].parentElement;
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
                const allowLoose = (Date.now() - startedAt) > 1500;
                return findUsageContainerFromSheet(panel, allowLoose);
              }, 3000, 80);

              if (usagePane) {
                const parsed = parseUsagePane(usagePane);
                if (parsed) {
                  cachedQuota = parsed;
                  lastQuotaDiag = 'usage=ok(' + (parsed.usedPercent !== null ? parsed.usedPercent + '%' : 'text') + ')';
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

          function triggerFileUpload() {
            const root = getAppRoot();
            const fileInput = root.querySelector('[data-testid="agent-input-box"] input[type="file"], input[type="file"]');
            if (fileInput) {
              fileInput.click();
              return true;
            }
            const addBtn = root.querySelector('button[aria-label="Add context"]');
            if (addBtn && isVisible(addBtn)) {
              simulateTap(addBtn);
              return true;
            }
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
                setTimeout(() => refreshModels(), 500);
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
