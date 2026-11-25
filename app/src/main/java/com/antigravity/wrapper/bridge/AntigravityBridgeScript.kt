package com.antigravity.wrapper.bridge

/**
 * Self-contained JavaScript engine calibrated to Antigravity's mobile DOM (`[data-testid="mobile-layout"]`):
 * - Conversations: `[data-testid="conversation-row-sidebar"]` (`a[aria-label]`)
 * - Model selector: `[data-testid="model-selector-trigger"]`, `[data-testid="model-selector-panel"]`,
 *   two-step `[data-testid="model-selector-effort-group"]` -> `[data-testid="model-selector-effort-option"]`,
 *   and single-step `[data-testid="model-selector-item"]`
 * - Usage quota: `"View Usage"` inside `[data-testid="model-selector-panel"]`
 * - Composer: `[data-testid="agent-input-box"]`, `[aria-label="Message input"]`, `[data-testid="send-button"]`
 */
object AntigravityBridgeScript {

    fun buildBootstrapScript(): String = """
        (function() {
          if (document.getElementById('af-error-container')) {
            window.location.replace('https://antigravity.google.com/remote');
            return;
          }
          if (window.__agBridge && window.__agBridge.version === 4) {
            window.__agBridge.syncNow();
            return;
          }

          const BRIDGE_VERSION = 4;
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
          let lastPayloadHash = '';
          let isInternalModalProbe = false;

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

          function isGoogleBar(el) {
            if (!el || el.nodeType !== 1) return false;
            const cls = typeof el.className === 'string' ? el.className : '';
            return el.id === 'gb' || cls.includes('boqOnegooglelite') || cls.includes('header-feedback-btn');
          }

          function collectDeep(root, predicate, limit) {
            const out = [];
            const max = limit || 2000;
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
            const tag = (el.tagName || '').toLowerCase();
            const cls = (typeof el.className === 'string' ? el.className : '').toLowerCase();
            const testId = (el.getAttribute('data-testid') || '').toLowerCase();
            const aria = (el.getAttribute('aria-label') || '').toLowerCase();
            if (tag === 'details') return true;
            return cls.includes('thought') || cls.includes('thinking') || cls.includes('reasoning') ||
              testId.includes('thought') || testId.includes('reason') ||
              aria.includes('thought') || aria.includes('reasoning');
          }

          function nodeToMarkdown(node, depth) {
            if (!node) return '';
            if (depth > 48) return '';
            if (node.nodeType === 3) return node.nodeValue || '';
            if (node.nodeType !== 1) return '';

            const tag = (node.tagName || '').toLowerCase();
            if (tag === 'script' || tag === 'style' || tag === 'svg' || tag === 'noscript') return '';
            if (node.getAttribute('aria-hidden') === 'true' && !node.classList.contains('katex')) return '';
            if (tag === 'button' && cleanText(node.textContent).length <= 24) return '';

            const cls = (typeof node.className === 'string' ? node.className : '').toLowerCase();
            if (cls.includes('katex-display') || (tag === 'mjx-container' && node.getAttribute('display') === 'true')) {
              const tex = extractTexFromMathNode(node);
              return tex ? ('\n\n$$\n' + tex + '\n$$\n\n') : '';
            }
            if (cls.includes('katex') || tag === 'mjx-container') {
              const tex = extractTexFromMathNode(node);
              return tex ? (' $' + tex + '$ ') : '';
            }

            if (isReasoningElement(node)) return '';

            if (tag === 'pre') {
              const codeEl = node.querySelector('code') || node;
              const codeCls = (codeEl.className || '').toString();
              const langMatch = codeCls.match(/language-([a-zA-Z0-9_+-]+)/);
              const lang = langMatch ? langMatch[1] : '';
              const rawCode = (codeEl.textContent || '').replace(/^\n+|\n+$/g, '');
              return '\n\n```' + lang + '\n' + rawCode + '\n```\n\n';
            }

            if (tag === 'code') {
              const txt = (node.textContent || '').replace(/`/g, "'");
              return '`' + txt + '`';
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
                return label;
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
            return (md || '').replace(/\n{3,}/g, '\n\n').trim();
          }

          function extractReasoningBlocks(container, msgIndex) {
            const blocks = [];
            const seen = new Set();
            const nodes = collectDeep(container, el => isReasoningElement(el), 40);
            nodes.forEach((el, idx) => {
              if (seen.has(el)) return;
              for (const prev of seen) {
                if (prev.contains && prev.contains(el)) return;
              }
              seen.add(el);

              const summaryEl = el.querySelector('summary, [role="button"], button');
              const rawSummary = summaryEl ? cleanText(summaryEl.textContent) : '';
              const durationMatch = rawSummary.match(/(\d+\s*(?:ms|s|sec|secs|seconds|m|min))/i);
              const duration = durationMatch ? durationMatch[1] : '';
              const title = rawSummary || 'Reasoning';

              const clone = el.cloneNode(true);
              const cloneSummary = clone.querySelector('summary, [role="button"], button');
              if (cloneSummary && cloneSummary.parentNode) {
                cloneSummary.parentNode.removeChild(cloneSummary);
              }
              const body = normalizeMarkdown(nodeToMarkdown(clone, 0)) || cleanText(clone.textContent);
              if (body || rawSummary) {
                blocks.push({
                  id: 'reason-' + msgIndex + '-' + idx,
                  title: title,
                  duration: duration,
                  content: body || 'Tap to view reasoning details.',
                  isStreaming: /thinking|reasoning\.\.\./i.test(rawSummary)
                });
              }
            });
            return blocks;
          }

          function extractArtifacts(container, msgIndex) {
            const artifacts = [];
            const text = container.innerText || '';
            const diffMatch = text.match(/(\d+\s+files?\s+changed)\s*\+(\d+)\s*[-−](\d+)/i);
            if (diffMatch) {
              artifacts.push({
                id: 'diff-' + msgIndex,
                title: diffMatch[1],
                subtitle: 'Code changes ready for review',
                actionLabel: 'Review',
                url: '',
                addedLines: parseInt(diffMatch[2], 10),
                removedLines: parseInt(diffMatch[3], 10)
              });
            }
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
            }, 30);
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
            if (m && m[1]) return cleanText(m[1]);
            return cleanText(btn.textContent);
          }

          function parseAriaCurrentValue(selector) {
            const el = getAppRoot().querySelector(selector);
            if (!el) return '';
            const aria = el.getAttribute('aria-label') || '';
            const m = aria.match(/current:\s*(.+)$/i);
            return m && m[1] ? cleanText(m[1]) : cleanText(el.textContent);
          }

          function dismissOverlays() {
            const backdrops = document.querySelectorAll('[data-testid="bottom-sheet-backdrop"]');
            backdrops.forEach(b => {
              if (isVisible(b)) b.click();
            });
            document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
          }

          function extractRelativeTime(row, cleanTitle) {
            const spans = Array.from(row.querySelectorAll('span, time'));
            for (const sp of spans) {
              const txt = cleanText(sp.textContent);
              if (!txt || txt === cleanTitle) continue;
              if (/^(\d+\s*(?:s|m|h|d|w|mo|y)|just now|now|today|yesterday)$/i.test(txt)) {
                return txt;
              }
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
                try {
                  fullUrl = new URL(href, window.location.origin).href;
                } catch (_) {}
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
            // Do not mistake the sidebar conversation list screen for messages
            const sidebarList = root.querySelector('[data-testid="conversation-list-sidebar"]');
            const sidebarVisible = sidebarList && isVisible(sidebarList) &&
              sidebarList.getBoundingClientRect().width > window.innerWidth * 0.6;
            if (sidebarVisible) {
              return [];
            }

            let turns = collectDeep(root, el => {
              if (sidebarList && sidebarList.contains(el)) return false;
              const roleAttr = (el.getAttribute('data-message-author-role') || el.getAttribute('data-role') || el.getAttribute('data-author') || '').toLowerCase();
              if (roleAttr === 'user' || roleAttr === 'assistant' || roleAttr === 'model' || roleAttr === 'agent') return true;
              const testId = (el.getAttribute('data-testid') || '').toLowerCase();
              if (testId.includes('message') || testId.includes('turn') || testId.includes('response')) {
                return isVisible(el) && cleanText(el.textContent).length > 0;
              }
              return false;
            }, 300);

            if (turns.length === 0) {
              turns = collectDeep(root, el => {
                if (sidebarList && sidebarList.contains(el)) return false;
                const tag = (el.tagName || '').toLowerCase();
                const cls = (typeof el.className === 'string' ? el.className : '').toLowerCase();
                if (tag === 'article') return isVisible(el);
                if ((cls.includes('message') || cls.includes('chat-turn') || cls.includes('response-container') || cls.includes('user-query') || cls.includes('prose') || cls.includes('markdown')) &&
                    isVisible(el) && cleanText(el.textContent).length > 0) {
                  return true;
                }
                return false;
              }, 300);
            }

            const topTurns = turns.filter((el, idx) => {
              for (let j = 0; j < turns.length; j++) {
                if (idx !== j && turns[j].contains && turns[j].contains(el)) return false;
              }
              return true;
            });

            const inputBox = root.querySelector('[data-testid="agent-input-box"]');
            const messages = [];
            topTurns.forEach((el, idx) => {
              if (inputBox && (el === inputBox || el.contains(inputBox) || inputBox.contains(el))) return;

              const roleHint = (
                (el.getAttribute('data-message-author-role') || '') + ' ' +
                (el.getAttribute('data-role') || '') + ' ' +
                (el.getAttribute('data-testid') || '') + ' ' +
                (typeof el.className === 'string' ? el.className : '')
              ).toLowerCase();

              const isUser = /\b(user|human|prompt|query|request)\b/.test(roleHint) &&
                !/\b(assistant|model|agent|response|reply)\b/.test(roleHint);

              const reasoning = isUser ? [] : extractReasoningBlocks(el, idx);
              const artifacts = isUser ? [] : extractArtifacts(el, idx);
              const markdown = normalizeMarkdown(nodeToMarkdown(el, 0));

              if (!markdown && reasoning.length === 0 && artifacts.length === 0) return;

              messages.push({
                id: el.getAttribute('data-message-id') || el.id || ('msg-' + idx),
                role: isUser ? 'user' : 'assistant',
                markdown: markdown,
                reasoning: reasoning,
                artifacts: artifacts,
                isStreaming: false
              });
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
                const isSelected = activeModel.toLowerCase().includes(name.toLowerCase().replace(/\s*\([^)]+\)\s*$/, ''));
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

          function extractQuotaFromScope(scope) {
            if (!scope) return null;
            const meters = collectDeep(scope, el => {
              const role = el.getAttribute('role') || '';
              const aria = (el.getAttribute('aria-label') || '').toLowerCase();
              const cls = (typeof el.className === 'string' ? el.className : '').toLowerCase();
              const tid = (el.getAttribute('data-testid') || '').toLowerCase();
              return role === 'progressbar' || (el.tagName || '').toLowerCase() === 'meter' ||
                aria.includes('quota') || aria.includes('usage') || aria.includes('limit') ||
                cls.includes('quota') || cls.includes('usage') || tid.includes('quota') || tid.includes('usage');
            }, 40);

            for (const el of meters) {
              const now = parseFloat(el.getAttribute('aria-valuenow') || el.value || '');
              const max = parseFloat(el.getAttribute('aria-valuemax') || el.max || '100');
              const txt = cleanText(el.getAttribute('aria-label') || el.textContent);
              const pctMatch = txt.match(/(\d{1,3})\s*%/);
              const pct = !isNaN(now) && max > 0 ? (now / max) * 100 : (pctMatch ? parseFloat(pctMatch[1]) : null);
              if (pct !== null || /quota|usage|remaining|limit|reset/i.test(txt)) {
                return {
                  usedPercent: pct,
                  remainingLabel: txt.slice(0, 90),
                  resetLabel: '',
                  planLabel: 'Antigravity',
                  rawSummary: txt.slice(0, 180),
                  isLive: true
                };
              }
            }

            const dialogText = cleanText(scope.innerText || '');
            if (dialogText && /(usage|quota|limit|remaining|reset|credits|%)/i.test(dialogText)) {
              const pctMatch = dialogText.match(/(\d{1,3})\s*%/);
              const resetMatch = dialogText.match(/(resets?\s+[^\n.]{2,40})/i);
              return {
                usedPercent: pctMatch ? parseFloat(pctMatch[1]) : null,
                remainingLabel: dialogText.slice(0, 90),
                resetLabel: resetMatch ? resetMatch[1] : '',
                planLabel: 'Antigravity',
                rawSummary: dialogText.slice(0, 200),
                isLive: true
              };
            }
            return null;
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

            const liveQuota = authRequired ? null : extractQuotaFromScope(getAppRoot());
            if (liveQuota) cachedQuota = liveQuota;

            const activeConv = conversations.find(c => c.isActive);
            let title = activeConv ? activeConv.title : 'Antigravity';
            if (!activeConv) {
              const headerHeading = getAppRoot().querySelector('header h1, header [role="heading"]');
              const rawHeader = headerHeading ? cleanText(headerHeading.textContent) : '';
              if (rawHeader && !/google antigravity|remote conversations/i.test(rawHeader)) {
                title = rawHeader;
              }
            }

            if (activeModel && cachedModels.length === 0) {
              cachedModels = [{
                id: activeModel,
                name: activeModel,
                tier: '',
                description: '',
                hasEffortLevels: false,
                effortOptions: [],
                isSelected: true
              }];
            } else if (activeModel) {
              cachedModels = cachedModels.map(m => {
                const selected = activeModel.toLowerCase().startsWith(m.name.toLowerCase().replace(/\s*\([^)]+\)\s*$/, ''));
                const effortMatch = selected ? activeModel.match(/\b(Low|Medium|High|Extra|Fast)\b/i) : null;
                return Object.assign({}, m, {
                  isSelected: selected,
                  tier: (effortMatch && effortMatch[1]) ? effortMatch[1] : m.tier
                });
              });
            }

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
              diagnosticsNote: 'turns=' + messages.length + ', chats=' + conversations.length + ', models=' + cachedModels.length + ', composer=' + (!!composer)
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
            if (syncTimer) clearTimeout(syncTimer);
            syncTimer = setTimeout(syncNow, 220);
          }

          function sendPrompt(text) {
            const input = findComposerInput();
            if (!input) return false;
            input.focus();
            const tag = (input.tagName || '').toLowerCase();
            if (tag === 'textarea' || tag === 'input') {
              const proto = tag === 'textarea' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
              const desc = Object.getOwnPropertyDescriptor(proto, 'value');
              if (desc && desc.set) desc.set.call(input, text);
              else input.value = text;
              input.dispatchEvent(new Event('input', { bubbles: true }));
              input.dispatchEvent(new Event('change', { bubbles: true }));
            } else if (input.isContentEditable) {
              input.textContent = '';
              document.execCommand('insertText', false, text);
              input.dispatchEvent(new Event('input', { bubbles: true }));
            }

            setTimeout(() => {
              const sendBtn = getAppRoot().querySelector('[data-testid="send-button"], button[aria-label="Send message"]');
              if (sendBtn && !sendBtn.disabled && isVisible(sendBtn)) {
                sendBtn.click();
              } else {
                const enterEv = new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true });
                input.dispatchEvent(enterEv);
              }
              setTimeout(() => syncNow(true), 250);
              setTimeout(() => syncNow(true), 900);
            }, 90);
            return true;
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
              stopBtns[0].click();
              scheduleSync();
              return true;
            }
            return false;
          }

          function refreshModels() {
            const btn = findModelSelectorButton();
            if (!btn) return false;
            const activeModel = parseActiveModelName(btn);
            isInternalModalProbe = true;
            const alreadyOpen = !!document.querySelector('[data-testid="model-selector-panel"]');
            if (!alreadyOpen) btn.click();

            setTimeout(() => {
              const parsed = readModelsFromOpenPanel(activeModel);
              if (parsed.length > 0) cachedModels = parsed;
              dismissOverlays();
              setTimeout(() => {
                isInternalModalProbe = false;
                syncNow(true);
              }, 120);
            }, 260);
            return true;
          }

          function selectModel(modelName, effortTier) {
            const btn = findModelSelectorButton();
            if (!btn) return false;
            isInternalModalProbe = true;
            const alreadyOpen = !!document.querySelector('[data-testid="model-selector-panel"]');
            if (!alreadyOpen) btn.click();

            setTimeout(() => {
              const panel = document.querySelector('[data-testid="model-selector-panel"]');
              if (!panel) {
                dismissOverlays();
                isInternalModalProbe = false;
                syncNow(true);
                return;
              }

              const targetLower = (modelName || '').toLowerCase();
              // 1. Check direct model-selector-item buttons first (e.g. GPT-OSS 120B)
              const directItems = Array.from(panel.querySelectorAll('button[data-testid="model-selector-item"]'));
              const directMatch = directItems.find(b => cleanText(b.textContent).toLowerCase().includes(targetLower));
              if (directMatch) {
                directMatch.click();
                setTimeout(() => {
                  dismissOverlays();
                  isInternalModalProbe = false;
                  syncNow(true);
                }, 220);
                return;
              }

              // 2. Check two-step effort-group buttons (e.g. Gemini 4 Neon -> High/Medium/Low/Extra)
              const groupSpans = Array.from(panel.querySelectorAll('[data-testid="model-selector-effort-group"]'));
              const groupMatch = groupSpans.find(s => cleanText(s.textContent).toLowerCase() === targetLower) ||
                groupSpans.find(s => cleanText(s.textContent).toLowerCase().includes(targetLower));

              if (!groupMatch) {
                dismissOverlays();
                isInternalModalProbe = false;
                syncNow(true);
                return;
              }

              const groupBtn = groupMatch.closest('button') || groupMatch;
              groupBtn.click();

              // 3. Pick effort tier in the sub-panel
              setTimeout(() => {
                const optionSpans = Array.from(document.querySelectorAll('[data-testid="model-selector-effort-option"]'))
                  .filter(isVisible);
                const desiredTier = (effortTier || 'Medium').toLowerCase();
                const optionMatch =
                  optionSpans.find(s => cleanText(s.textContent).toLowerCase() === desiredTier) ||
                  optionSpans.find(s => cleanText(s.textContent).toLowerCase() === 'medium') ||
                  optionSpans[0];

                if (optionMatch) {
                  const optBtn = optionMatch.closest('button') || optionMatch;
                  optBtn.click();
                } else {
                  dismissOverlays();
                }

                setTimeout(() => {
                  dismissOverlays();
                  isInternalModalProbe = false;
                  syncNow(true);
                }, 260);
              }, 260);
            }, 260);
            return true;
          }

          function refreshQuota() {
            const btn = findModelSelectorButton();
            if (!btn) {
              syncNow(true);
              return false;
            }
            isInternalModalProbe = true;
            const alreadyOpen = !!document.querySelector('[data-testid="model-selector-panel"]');
            if (!alreadyOpen) btn.click();

            setTimeout(() => {
              const panel = document.querySelector('[data-testid="model-selector-panel"]');
              const usageBtn = panel && Array.from(panel.querySelectorAll('button.agy-overlay-item'))
                .find(b => /view usage/i.test(cleanText(b.textContent)));

              if (!usageBtn) {
                dismissOverlays();
                isInternalModalProbe = false;
                syncNow(true);
                return;
              }

              usageBtn.click();
              setTimeout(() => {
                const dialogs = Array.from(document.querySelectorAll('[role="dialog"], [data-testid="bottom-sheet"]'))
                  .filter(isVisible);
                for (let i = dialogs.length - 1; i >= 0; i--) {
                  const parsed = extractQuotaFromScope(dialogs[i]);
                  if (parsed) {
                    cachedQuota = parsed;
                    break;
                  }
                }
                dismissOverlays();
                setTimeout(() => {
                  isInternalModalProbe = false;
                  syncNow(true);
                }, 160);
              }, 500);
            }, 260);
            return true;
          }

          function startNewChat() {
            const root = getAppRoot();
            const newBtn = root.querySelector('button[aria-label="New Conversation"], a[aria-label="New Conversation in Project"]');
            if (newBtn && isVisible(newBtn)) {
              newBtn.click();
              setTimeout(() => syncNow(true), 300);
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
                anchor.click();
                setTimeout(() => syncNow(true), 300);
                setTimeout(() => syncNow(true), 900);
                setTimeout(() => syncNow(true), 1800);
                return true;
              }
            }
            return false;
          }

          function selectConversation(url, title) {
            if (clickSidebarRow(url, title)) {
              return true;
            }
            // If currently inside a thread on mobile, navigate back to the sidebar first, then click the row
            const backBtn = getAppRoot().querySelector('button[aria-label="Back"], button[aria-label*="Conversations" i]');
            if (backBtn && isVisible(backBtn)) {
              backBtn.click();
              setTimeout(() => {
                if (!clickSidebarRow(url, title) && url && url.startsWith('https://antigravity.google.com/remote')) {
                  window.location.href = url;
                }
              }, 320);
              return true;
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
              addBtn.click();
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
            observer.observe(document.body, { childList: true, subtree: true, characterData: true, attributes: true });
          }

          window.__agBridge = {
            version: BRIDGE_VERSION,
            syncNow: () => syncNow(true),
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
          setTimeout(() => syncNow(true), 2200);
          let hydrateTicks = 0;
          const hydrateInterval = setInterval(() => {
            hydrateTicks++;
            syncNow(false);
            if (hydrateTicks >= 6 || document.querySelector('[data-testid="model-selector-trigger"]')) {
              clearInterval(hydrateInterval);
            }
          }, 1800);
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
