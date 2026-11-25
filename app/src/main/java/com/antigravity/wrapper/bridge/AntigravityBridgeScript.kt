package com.antigravity.wrapper.bridge

/**
 * Self-contained JavaScript engine injected into the headless Antigravity WebView.
 * Observes DOM and open Shadow DOM mutations, extracts structured chat state
 * (conversations, messages with lossless Markdown/LaTeX, collapsible reasoning,
 * artifacts, active model, model list, usage quota), drives native actions,
 * and exports a sanitized structural DOM snapshot for selector calibration.
 */
object AntigravityBridgeScript {

    fun buildBootstrapScript(): String = """
        (function() {
          if (window.__agBridge && window.__agBridge.version === 1) {
            window.__agBridge.syncNow();
            return;
          }

          const BRIDGE_VERSION = 1;
          let cachedModels = [];
          let cachedConversations = [];
          let syncTimer = null;
          let lastPayloadHash = '';

          function isVisible(el) {
            if (!el || el.nodeType !== 1) return false;
            const style = window.getComputedStyle(el);
            if (!style || style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
              return false;
            }
            const rect = el.getBoundingClientRect();
            return rect.width > 0 || rect.height > 0;
          }

          function collectDeep(root, predicate, limit) {
            const out = [];
            const max = limit || 400;
            const stack = [root || document.body];
            while (stack.length > 0 && out.length < max) {
              const cur = stack.pop();
              if (!cur) continue;
              if (cur.nodeType === 1) {
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

          function nodeToMarkdown(node, depth) {
            if (!node) return '';
            if (depth > 40) return '';
            if (node.nodeType === 3) {
              return node.nodeValue || '';
            }
            if (node.nodeType !== 1) return '';

            const tag = (node.tagName || '').toLowerCase();
            if (tag === 'script' || tag === 'style' || tag === 'svg' || tag === 'noscript') return '';
            if (node.getAttribute('aria-hidden') === 'true' && !node.classList.contains('katex')) return '';
            if (tag === 'button' && cleanText(node.textContent).length <= 24) return '';

            // KaTeX / MathJax lossless LaTeX recovery
            const cls = (typeof node.className === 'string' ? node.className : '').toLowerCase();
            if (cls.includes('katex-display') || tag === 'mjx-container' && node.getAttribute('display') === 'true') {
              const tex = extractTexFromMathNode(node);
              return tex ? ('\n\n$$\n' + tex + '\n$$\n\n') : '';
            }
            if (cls.includes('katex') || tag === 'mjx-container') {
              const tex = extractTexFromMathNode(node);
              return tex ? (' $' + tex + '$ ') : '';
            }

            // Skip reasoning blocks here (extracted separately)
            if (isReasoningElement(node)) return '';

            // Fenced code blocks
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
            return (md || '')
              .replace(/\n{3,}/g, '\n\n')
              .trim();
          }

          function isReasoningElement(el) {
            if (!el || el.nodeType !== 1) return false;
            const tag = (el.tagName || '').toLowerCase();
            const cls = (typeof el.className === 'string' ? el.className : '').toLowerCase();
            const testId = (el.getAttribute('data-testid') || '').toLowerCase();
            const aria = (el.getAttribute('aria-label') || '').toLowerCase();
            if (tag === 'details') return true;
            if (cls.includes('thought') || cls.includes('thinking') || cls.includes('reasoning') ||
                testId.includes('thought') || testId.includes('reason') ||
                aria.includes('thought') || aria.includes('reasoning')) {
              return true;
            }
            return false;
          }

          function extractReasoningBlocks(container, msgIndex) {
            const blocks = [];
            const seen = new Set();
            const nodes = collectDeep(container, el => isReasoningElement(el), 30);
            nodes.forEach((el, idx) => {
              if (seen.has(el)) return;
              // Skip nested matches inside an already captured reasoning node
              for (const prev of seen) {
                if (prev.contains && prev.contains(el)) return;
              }
              seen.add(el);

              const summaryEl = el.querySelector('summary, [role="button"], button');
              const rawSummary = summaryEl ? cleanText(summaryEl.textContent) : '';
              const durationMatch = rawSummary.match(/(\d+\s*(?:ms|s|sec|secs|seconds|m|min))/i);
              const duration = durationMatch ? durationMatch[1] : '';
              const title = rawSummary || 'Reasoning';

              // Extract body excluding the summary trigger
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
            // Match file diff summaries like "31 files changed +5240 -323"
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
            const candidates = collectDeep(document.body, el => {
              const tag = (el.tagName || '').toLowerCase();
              if (tag === 'textarea') return !el.disabled && !el.readOnly && isVisible(el);
              if (el.isContentEditable && isVisible(el)) return true;
              if (el.getAttribute('role') === 'textbox' && isVisible(el)) return true;
              return false;
            }, 30);
            if (!candidates.length) return null;
            // Prefer the input closest to the bottom of the viewport
            candidates.sort((a, b) => b.getBoundingClientRect().bottom - a.getBoundingClientRect().bottom);
            return candidates[0];
          }

          function findModelSelectorButton() {
            const composer = findComposerInput();
            const composerRect = composer ? composer.getBoundingClientRect() : null;
            const buttons = collectDeep(document.body, el => {
              const tag = (el.tagName || '').toLowerCase();
              const role = el.getAttribute('role') || '';
              if (tag !== 'button' && role !== 'button' && role !== 'combobox') return false;
              if (!isVisible(el)) return false;
              const txt = cleanText(el.textContent);
              if (!txt || txt.length > 48) return false;
              const hasModelKeyword = /(gemini|claude|gpt|o1|o3|sonnet|opus|haiku|flash|pro|neon|ultra|model)/i.test(txt);
              const hasPopup = el.getAttribute('aria-haspopup') === 'listbox' || el.getAttribute('aria-haspopup') === 'menu';
              return hasModelKeyword || (hasPopup && composerRect && Math.abs(el.getBoundingClientRect().top - composerRect.bottom) < 120);
            }, 40);
            if (!buttons.length) return null;
            if (composerRect) {
              buttons.sort((a, b) => {
                const da = Math.abs(a.getBoundingClientRect().top - composerRect.bottom);
                const db = Math.abs(b.getBoundingClientRect().top - composerRect.bottom);
                return da - db;
              });
            }
            return buttons[0];
          }

          function extractConversations() {
            const links = collectDeep(document.body, el => {
              const tag = (el.tagName || '').toLowerCase();
              const role = el.getAttribute('role') || '';
              if (tag === 'a') {
                const href = el.getAttribute('href') || '';
                if (/\/(c|chat|conversation|session|thread|project)\//i.test(href)) return true;
              }
              if ((role === 'listitem' || role === 'option' || role === 'treeitem') && el.closest && el.closest('nav, aside, [role="navigation"]')) {
                return cleanText(el.textContent).length > 1;
              }
              return false;
            }, 80);

            const result = [];
            const seen = new Set();
            links.forEach((el, idx) => {
              const title = cleanText(el.textContent).split('\n')[0].slice(0, 80);
              if (!title || title.length < 2) return;
              const href = el.getAttribute('href') || '';
              const fullUrl = href ? new URL(href, window.location.origin).href : '';
              const key = fullUrl || title;
              if (seen.has(key)) return;
              seen.add(key);
              const isActive = (fullUrl && fullUrl === window.location.href) ||
                el.getAttribute('aria-current') === 'page' ||
                el.getAttribute('aria-selected') === 'true';
              result.push({
                id: 'conv-' + idx + '-' + title.slice(0, 16),
                title: title,
                url: fullUrl,
                preview: '',
                updatedLabel: '',
                isActive: isActive
              });
            });

            if (result.length > 0) {
              cachedConversations = result;
            }
            return cachedConversations;
          }

          function extractMessages() {
            // Strategy 1: explicit author-role / message testid containers
            let turns = collectDeep(document.body, el => {
              const roleAttr = (el.getAttribute('data-message-author-role') || el.getAttribute('data-role') || el.getAttribute('data-author') || '').toLowerCase();
              if (roleAttr === 'user' || roleAttr === 'assistant' || roleAttr === 'model' || roleAttr === 'agent') return true;
              const testId = (el.getAttribute('data-testid') || '').toLowerCase();
              if (testId.includes('message') || testId.includes('turn')) return true;
              return false;
            }, 150);

            // Strategy 2: fallback semantic turn containers inside <main> / [role="main"] / [role="log"]
            if (turns.length === 0) {
              const mainRoot = document.querySelector('[role="log"], main, [role="main"]') || document.body;
              turns = collectDeep(mainRoot, el => {
                const tag = (el.tagName || '').toLowerCase();
                const cls = (typeof el.className === 'string' ? el.className : '').toLowerCase();
                if (tag === 'article') return isVisible(el);
                if ((cls.includes('message') || cls.includes('chat-turn') || cls.includes('response-container') || cls.includes('user-query')) &&
                    isVisible(el) && cleanText(el.textContent).length > 0) {
                  return true;
                }
                return false;
              }, 150);
            }

            // Filter out nested containers so each turn is emitted once
            const topTurns = turns.filter((el, idx) => {
              for (let j = 0; j < turns.length; j++) {
                if (idx !== j && turns[j].contains && turns[j].contains(el)) return false;
              }
              return true;
            });

            const composer = findComposerInput();
            const messages = [];
            topTurns.forEach((el, idx) => {
              if (composer && (el === composer || el.contains(composer))) return;

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

          function extractQuota() {
            const meters = collectDeep(document.body, el => {
              const role = el.getAttribute('role') || '';
              const aria = (el.getAttribute('aria-label') || '').toLowerCase();
              const cls = (typeof el.className === 'string' ? el.className : '').toLowerCase();
              return role === 'progressbar' || (el.tagName || '').toLowerCase() === 'meter' ||
                aria.includes('quota') || aria.includes('usage') || aria.includes('limit') ||
                cls.includes('quota') || cls.includes('usage');
            }, 25);

            for (const el of meters) {
              const now = parseFloat(el.getAttribute('aria-valuenow') || el.value || '');
              const max = parseFloat(el.getAttribute('aria-valuemax') || el.max || '100');
              const txt = cleanText(el.getAttribute('aria-label') || el.textContent);
              const pctMatch = txt.match(/(\d{1,3})\s*%/);
              const pct = !isNaN(now) && max > 0 ? (now / max) * 100 : (pctMatch ? parseFloat(pctMatch[1]) : null);
              if (pct !== null || /quota|usage|remaining|limit|reset/i.test(txt)) {
                return {
                  usedPercent: pct,
                  remainingLabel: txt.slice(0, 80),
                  resetLabel: '',
                  planLabel: 'Antigravity',
                  rawSummary: txt.slice(0, 140),
                  isLive: true
                };
              }
            }
            return {
              usedPercent: null,
              remainingLabel: '',
              resetLabel: '',
              planLabel: 'Antigravity',
              rawSummary: '',
              isLive: false
            };
          }

          function detectStreaming() {
            const stopBtns = collectDeep(document.body, el => {
              const tag = (el.tagName || '').toLowerCase();
              const role = el.getAttribute('role') || '';
              if (tag !== 'button' && role !== 'button') return false;
              if (!isVisible(el)) return false;
              const label = ((el.getAttribute('aria-label') || '') + ' ' + cleanText(el.textContent)).toLowerCase();
              return label.includes('stop generating') || label === 'stop' || label.includes('cancel response');
            }, 10);
            return stopBtns.length > 0;
          }

          function detectAuthRequired() {
            const host = (window.location.hostname || '').toLowerCase();
            if (host.includes('accounts.google.') || host.includes('signin.google.') || host.includes('consent.google.')) {
              return true;
            }
            const signInButtons = collectDeep(document.body, el => {
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
            const activeModel = modelBtn ? cleanText(modelBtn.textContent) : '';
            const isStreaming = detectStreaming();
            const messages = authRequired ? [] : extractMessages();
            if (isStreaming && messages.length > 0 && messages[messages.length - 1].role === 'assistant') {
              messages[messages.length - 1].isStreaming = true;
            }
            const conversations = authRequired ? [] : extractConversations();
            const quota = authRequired ? { isLive: false } : extractQuota();

            let title = cleanText(document.title).replace(/\s*[-|•]\s*Antigravity.*$/i, '').trim();
            const headerTitle = document.querySelector('header h1, header [role="heading"], [class*="conversation-title" i]');
            if (headerTitle && cleanText(headerTitle.textContent)) {
              title = cleanText(headerTitle.textContent);
            }

            if (activeModel && cachedModels.length === 0) {
              cachedModels = [{
                id: activeModel,
                name: activeModel,
                tier: '',
                description: 'Active model on Antigravity',
                isSelected: true
              }];
            } else if (activeModel) {
              cachedModels = cachedModels.map(m => Object.assign({}, m, { isSelected: m.name === activeModel }));
            }

            return {
              url: window.location.href,
              title: title || 'Antigravity',
              authRequired: authRequired,
              isStreaming: isStreaming,
              activeModel: activeModel,
              models: cachedModels,
              conversations: conversations,
              messages: messages,
              quota: quota,
              composerReady: !!composer,
              diagnosticsNote: 'turns=' + messages.length + ', chats=' + conversations.length + ', composer=' + (!!composer)
            };
          }

          function syncNow() {
            try {
              const snapshot = buildSnapshot();
              const json = JSON.stringify(snapshot);
              if (json !== lastPayloadHash) {
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
              const form = input.closest && input.closest('form');
              const scope = form || input.parentElement?.parentElement || document.body;
              const sendBtns = collectDeep(scope, el => {
                const t = (el.tagName || '').toLowerCase();
                if (t !== 'button' && el.getAttribute('role') !== 'button') return false;
                if (el.disabled || !isVisible(el)) return false;
                const label = ((el.getAttribute('aria-label') || '') + ' ' + cleanText(el.textContent)).toLowerCase();
                if (el.type === 'submit' || label.includes('send') || label.includes('submit') || label.includes('ask')) return true;
                return false;
              }, 10);

              if (sendBtns.length > 0) {
                sendBtns[sendBtns.length - 1].click();
              } else {
                const enterEv = new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true });
                input.dispatchEvent(enterEv);
              }
              scheduleSync();
            }, 80);
            return true;
          }

          function stopGeneration() {
            const stopBtns = collectDeep(document.body, el => {
              const t = (el.tagName || '').toLowerCase();
              if (t !== 'button' && el.getAttribute('role') !== 'button') return false;
              if (!isVisible(el)) return false;
              const label = ((el.getAttribute('aria-label') || '') + ' ' + cleanText(el.textContent)).toLowerCase();
              return label.includes('stop') || label.includes('cancel');
            }, 10);
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
            btn.click();
            setTimeout(() => {
              const items = collectDeep(document.body, el => {
                const role = el.getAttribute('role') || '';
                return (role === 'menuitem' || role === 'option' || role === 'menuitemradio') && isVisible(el);
              }, 40);
              const parsed = [];
              const seen = new Set();
              items.forEach((item, idx) => {
                const lines = (item.innerText || '').split('\n').map(cleanText).filter(Boolean);
                if (!lines.length) return;
                const name = lines[0];
                if (seen.has(name)) return;
                seen.add(name);
                parsed.push({
                  id: 'model-' + idx + '-' + name,
                  name: name,
                  tier: '',
                  description: lines.slice(1).join(' • ').slice(0, 100),
                  isSelected: item.getAttribute('aria-checked') === 'true' || item.getAttribute('aria-selected') === 'true'
                });
              });
              if (parsed.length > 0) cachedModels = parsed;
              document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
              lastPayloadHash = '';
              syncNow();
            }, 220);
            return true;
          }

          function selectModel(modelName) {
            const btn = findModelSelectorButton();
            if (!btn) return false;
            btn.click();
            setTimeout(() => {
              const items = collectDeep(document.body, el => {
                const role = el.getAttribute('role') || '';
                return (role === 'menuitem' || role === 'option' || role === 'menuitemradio') && isVisible(el);
              }, 40);
              const target = items.find(el => cleanText(el.textContent).toLowerCase().includes(modelName.toLowerCase()));
              if (target) {
                target.click();
              } else {
                document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
              }
              setTimeout(() => {
                lastPayloadHash = '';
                syncNow();
              }, 200);
            }, 220);
            return true;
          }

          function startNewChat() {
            const newBtns = collectDeep(document.body, el => {
              const tag = (el.tagName || '').toLowerCase();
              const role = el.getAttribute('role') || '';
              if (tag !== 'button' && tag !== 'a' && role !== 'button') return false;
              const label = ((el.getAttribute('aria-label') || '') + ' ' + cleanText(el.textContent)).toLowerCase();
              return label.includes('new chat') || label.includes('new conversation') || label.includes('start new');
            }, 10);
            if (newBtns.length > 0) {
              newBtns[0].click();
              setTimeout(() => { lastPayloadHash = ''; syncNow(); }, 300);
              return true;
            }
            window.location.href = 'https://antigravity.google.com';
            return true;
          }

          function selectConversation(url, title) {
            if (url && url.startsWith('https://')) {
              window.location.href = url;
              return true;
            }
            const candidates = collectDeep(document.body, el => {
              const txt = cleanText(el.textContent);
              return txt && txt.toLowerCase().includes((title || '').toLowerCase());
            }, 20);
            if (candidates.length > 0) {
              candidates[candidates.length - 1].click();
              setTimeout(() => { lastPayloadHash = ''; syncNow(); }, 300);
              return true;
            }
            return false;
          }

          function triggerFileUpload() {
            const fileInput = collectDeep(document.body, el =>
              (el.tagName || '').toLowerCase() === 'input' && el.type === 'file', 5
            )[0];
            if (fileInput) {
              fileInput.click();
              return true;
            }
            const plusBtns = collectDeep(document.body, el => {
              const tag = (el.tagName || '').toLowerCase();
              if (tag !== 'button' && el.getAttribute('role') !== 'button') return false;
              const label = ((el.getAttribute('aria-label') || '') + ' ' + cleanText(el.textContent)).toLowerCase();
              return label.includes('attach') || label.includes('upload') || label.includes('add file') || label === '+';
            }, 10);
            if (plusBtns.length > 0) {
              plusBtns[0].click();
              return true;
            }
            return false;
          }

          function dumpDomSnapshot() {
            function redact(str) {
              return cleanText(str)
                .replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, '[email]')
                .slice(0, 48);
            }
            function walk(el, depth) {
              if (!el || el.nodeType !== 1 || depth > 11) return null;
              const tag = (el.tagName || '').toLowerCase();
              if (tag === 'script' || tag === 'style' || tag === 'svg' || tag === 'path') return null;
              const node = { t: tag };
              if (el.id) node.id = el.id.slice(0, 32);
              const role = el.getAttribute('role');
              if (role) node.r = role;
              const aria = el.getAttribute('aria-label');
              if (aria) node.a = redact(aria);
              const testId = el.getAttribute('data-testid');
              if (testId) node.tid = testId;
              const expanded = el.getAttribute('aria-expanded');
              if (expanded !== null) node.exp = expanded;
              if (typeof el.className === 'string' && el.className.trim()) {
                node.c = el.className.trim().split(/\s+/).slice(0, 4).join(' ');
              }
              const ownText = Array.from(el.childNodes)
                .filter(c => c.nodeType === 3)
                .map(c => c.nodeValue)
                .join(' ');
              const cleanOwn = redact(ownText);
              if (cleanOwn) node.tx = cleanOwn;

              const kids = [];
              const childSource = el.shadowRoot ? el.shadowRoot.children : el.children;
              if (el.shadowRoot) node.shadow = true;
              if (childSource) {
                for (let i = 0; i < Math.min(childSource.length, 24); i++) {
                  const childNode = walk(childSource[i], depth + 1);
                  if (childNode) kids.push(childNode);
                }
              }
              if (kids.length) node.ch = kids;
              return node;
            }
            return JSON.stringify({
              url: window.location.href,
              title: document.title,
              tree: walk(document.body, 0)
            });
          }

          const observer = new MutationObserver(scheduleSync);
          if (document.body) {
            observer.observe(document.body, { childList: true, subtree: true, characterData: true, attributes: true });
          }

          window.__agBridge = {
            version: BRIDGE_VERSION,
            syncNow: syncNow,
            sendPrompt: sendPrompt,
            stopGeneration: stopGeneration,
            refreshModels: refreshModels,
            selectModel: selectModel,
            startNewChat: startNewChat,
            selectConversation: selectConversation,
            triggerFileUpload: triggerFileUpload,
            dumpDomSnapshot: dumpDomSnapshot
          };

          syncNow();
          setTimeout(syncNow, 900);
          setTimeout(syncNow, 2200);
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
