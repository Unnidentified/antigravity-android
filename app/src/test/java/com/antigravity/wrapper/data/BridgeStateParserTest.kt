package com.antigravity.wrapper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeStateParserTest {

    @Test
    fun parsesFullBridgeSnapshotWithReasoningArtifactsModelsAndQuota() {
        val json = """
            {
              "url": "https://antigravity.google.com/c/abc",
              "title": "Native UI Rebuild",
              "authRequired": false,
              "isStreaming": true,
              "activeModel": "Gemini 4 Neon High",
              "composerReady": true,
              "models": [
                {"id": "m1", "name": "Gemini 4 Neon High", "description": "Deep reasoning", "isSelected": true},
                {"id": "m2", "name": "Gemini 4 Flash", "description": "Fast responses", "isSelected": false}
              ],
              "conversations": [
                {"id": "c1", "title": "Native UI Rebuild", "url": "https://antigravity.google.com/c/abc", "isActive": true}
              ],
              "messages": [
                {"id": "u1", "role": "user", "markdown": "Explain Bayes' theorem"},
                {
                  "id": "a1",
                  "role": "assistant",
                  "markdown": "Here is the formula:\n\n$$\nP(A|B) = \\frac{P(B|A)P(A)}{P(B)}\n$$",
                  "isStreaming": true,
                  "reasoning": [
                    {"id": "r1", "title": "Thought for 3s", "duration": "3s", "content": "Recall conditional probability."}
                  ],
                  "artifacts": [
                    {"id": "diff-1", "title": "31 files changed", "subtitle": "Ready", "addedLines": 5240, "removedLines": 323}
                  ]
                }
              ],
              "quota": {
                "usedPercent": 42.5,
                "leftPercent": 78.0,
                "fiveHourLeftPercent": 78.0,
                "fiveHourResetLabel": "Resets in 3h 43m",
                "weeklyLeftPercent": 93.0,
                "weeklyResetLabel": "Resets in 6d 22h",
                "limits": [
                  {"id": "l1", "groupName": "Gemini Models", "windowLabel": "Weekly limit", "leftPercent": 93.0, "resetLabel": "Resets in 6d 22h"},
                  {"id": "l2", "groupName": "Gemini Models", "windowLabel": "5h limit", "leftPercent": 78.0, "resetLabel": "Resets in 3h 43m"},
                  {"id": "l3", "groupName": "Claude and GPT models", "windowLabel": "Weekly limit", "leftPercent": 100.0, "resetLabel": "Resets in 6d 23h"},
                  {"id": "l4", "groupName": "Claude and GPT models", "windowLabel": "5h limit", "leftPercent": 100.0, "resetLabel": "Resets in 4h 13m"}
                ],
                "remainingLabel": "5h: 78% left • Weekly: 93% left",
                "planLabel": "Antigravity Pro",
                "isLive": true
              }
            }
        """.trimIndent()

        val snapshot = BridgeStateParser.parseSnapshot(json)
        assertNotNull(snapshot)
        snapshot!!

        assertEquals("Native UI Rebuild", snapshot.conversationTitle)
        assertFalse(snapshot.authRequired)
        assertTrue(snapshot.isStreaming)
        assertEquals("Gemini 4 Neon High", snapshot.activeModelName)
        assertEquals(2, snapshot.models.size)
        assertEquals(1, snapshot.conversations.size)
        assertEquals(2, snapshot.messages.size)
        assertEquals(MessageRole.USER, snapshot.messages[0].role)
        assertEquals(MessageRole.ASSISTANT, snapshot.messages[1].role)
        assertEquals(1, snapshot.messages[1].reasoningBlocks.size)
        assertEquals("3s", snapshot.messages[1].reasoningBlocks[0].durationLabel)
        assertEquals(1, snapshot.messages[1].artifacts.size)
        assertEquals(5240, snapshot.messages[1].artifacts[0].addedLines)
        assertEquals(323, snapshot.messages[1].artifacts[0].removedLines)
        assertEquals(42.5f, snapshot.quota.usedPercent)
        assertEquals(78.0f, snapshot.quota.fiveHourLeftPercent)
        assertEquals("Resets in 3h 43m", snapshot.quota.fiveHourResetLabel)
        assertEquals(93.0f, snapshot.quota.weeklyLeftPercent)
        assertEquals("Resets in 6d 22h", snapshot.quota.weeklyResetLabel)
        assertEquals(4, snapshot.quota.limits.size)
        assertEquals(78.0f, snapshot.quota.headlineLeftPercent)
        assertTrue(snapshot.quota.isLiveFromServer)
    }

    @Test
    fun normalizesParenthesizedModelTierAndStartupHeaderTitle() {
        val (name1, tier1) = BridgeStateParser.normalizeModelNameAndTier("GPT-OSS 120B (Medium)", "")
        assertEquals("GPT-OSS 120B", name1)
        assertEquals("Medium", tier1)

        val (name2, tier2) = BridgeStateParser.normalizeModelNameAndTier("Gemini 4 Neon", "High")
        assertEquals("Gemini 4 Neon", name2)
        assertEquals("High", tier2)

        assertEquals("Antigravity", BridgeStateParser.normalizeConversationTitle("Google Antigravity"))
        assertEquals("Antigravity", BridgeStateParser.normalizeConversationTitle(""))
        assertEquals("AI Agent Handoff Process", BridgeStateParser.normalizeConversationTitle("AI Agent Handoff Process"))
    }

    @Test
    fun stripsTurnSuffixFromActiveModelAndPlaceholderProject() {
        val json = """
            {
              "url": "https://antigravity.google.com/c/xyz",
              "title": "Test",
              "activeModel": "GPT-OSS 120B (Medium). Applies to messages sent after this turn",
              "activeProject": "No Project"
            }
        """.trimIndent()

        val snapshot = BridgeStateParser.parseSnapshot(json)
        assertNotNull(snapshot)
        snapshot!!
        assertEquals("GPT-OSS 120B (Medium)", snapshot.activeModelName)
        assertEquals("", snapshot.activeProject)
    }

    @Test
    fun returnsNullOnMalformedOrBlankInput() {
        assertEquals(null, BridgeStateParser.parseSnapshot(null))
        assertEquals(null, BridgeStateParser.parseSnapshot(""))
        assertEquals(null, BridgeStateParser.parseSnapshot("{bad json"))
    }

    @Test
    fun antigravityDarkIsDefaultAndAllThemesResolveCleanly() {
        assertEquals(ThemeMode.ANTIGRAVITY_DARK, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.ANTIGRAVITY_DARK, ThemeMode.fromKey("unknown_key"))
        assertEquals(ThemeMode.ANTIGRAVITY_DARK, ThemeMode.fromKey("dark"))
        assertEquals(ThemeMode.PURE_OLED_BLACK, ThemeMode.fromKey("amoled"))
        assertEquals(ThemeMode.ANTIGRAVITY_LIGHT, ThemeMode.fromKey("light"))
        assertEquals(ThemeMode.SLATE, ThemeMode.fromKey("slate"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("system"))
        assertEquals(ThemeMode.ANTIGRAVITY_DARK, AppSettings().themeMode)
        assertTrue(ThemeMode.entries.size >= 20)
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromKey(mode.storageKey))
        }
    }

    @Test
    fun parsesThreeLevelReasoningSectionsAndStepItems() {
        val json = """
            {
              "url": "https://antigravity.google.com/remote/r1",
              "title": "Reasoning Test",
              "messages": [
                {
                  "id": "m1",
                  "role": "assistant",
                  "markdown": "Done.",
                  "reasoning": [
                    {
                      "id": "r1",
                      "title": "Worked for 45s",
                      "duration": "45s",
                      "content": "",
                      "sections": [
                        {
                          "id": "sec1",
                          "title": "Explored files, ran commands, edited files",
                          "defaultExpanded": true,
                          "steps": [
                            {
                              "id": "st1",
                              "title": "List commits, tags, and releases",
                              "kind": "command",
                              "detail": "```bash\ngit tag\n```",
                              "isExpandable": true
                            },
                            {
                              "id": "st2",
                              "title": "README.md",
                              "kind": "file",
                              "fileName": "README.md",
                              "lineRange": "#L1-19",
                              "badgeText": "MD",
                              "actionVerb": "Analyzed",
                              "isExpandable": false
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val snap = BridgeStateParser.parseSnapshot(json)!!
        assertEquals(1, snap.messages.size)
        val block = snap.messages[0].reasoningBlocks[0]
        assertEquals("Worked for 45s", block.title)
        assertEquals(1, block.sections.size)
        assertEquals("Explored files, ran commands, edited files", block.sections[0].title)
        assertEquals(2, block.sections[0].steps.size)
        assertEquals(ReasoningStepKind.COMMAND, block.sections[0].steps[0].kind)
        assertEquals(ReasoningStepKind.FILE, block.sections[0].steps[1].kind)
        assertEquals("README.md", block.sections[0].steps[1].fileName)
        assertEquals("#L1-19", block.sections[0].steps[1].lineRange)
        assertEquals("MD", block.sections[0].steps[1].badgeText)
    }

    @Test
    fun parsesWorkspaceParityFieldsAndDiffAndDocumentPayloads() {
        val snapJson = """
            {
              "url": "https://antigravity.google.com/remote/abc",
              "title": "AI Agent Handoff Process",
              "hasOlderMessages": true,
              "hasSidePanel": true,
              "projects": [
                {"id": "p1", "name": "Unnidentified/antigravity-android", "isSelected": true},
                {"id": "p2", "name": "No Project", "isSelected": false}
              ]
            }
        """.trimIndent()
        val snap = BridgeStateParser.parseSnapshot(snapJson)
        assertNotNull(snap)
        snap!!
        assertTrue(snap.hasOlderMessages)
        assertTrue(snap.hasSidePanel)
        assertEquals(2, snap.projects.size)
        assertEquals("Unnidentified/antigravity-android", snap.projects[0].name)

        val diffJson = """
            {
              "title": "6 files changed",
              "subtitle": "+292 -25",
              "files": [
                {
                  "id": "f0",
                  "fileName": "release.yml",
                  "path": ".github/workflows",
                  "addedLines": 10,
                  "removedLines": 2,
                  "lines": [
                    {"kind": "hunk", "num": "", "text": "@@ -1,3 +1,5 @@"},
                    {"kind": "context", "num": "1", "text": "name: Release"},
                    {"kind": "removed", "num": "2", "text": "old: true"},
                    {"kind": "added", "num": "2", "text": "new: true"}
                  ]
                }
              ]
            }
        """.trimIndent()
        val diff = BridgeStateParser.parseDiffReview(diffJson)
        assertEquals("6 files changed", diff.title)
        assertEquals(1, diff.files.size)
        assertEquals("release.yml", diff.files[0].fileName)
        assertEquals(4, diff.files[0].lines.size)
        assertEquals("2", diff.files[0].lines[3].lineNumber)
        assertEquals(DiffLineKind.ADDED, diff.files[0].lines[3].kind)

        val docJson = """
            {
              "title": "docs/development.md",
              "markdown": "# Development\nRun `./gradlew test`",
              "raw": "# Development\nRun `./gradlew test`"
            }
        """.trimIndent()
        val doc = BridgeStateParser.parseDocumentViewer(docJson)
        assertEquals("docs/development.md", doc.title)
        assertTrue(doc.markdownContent.contains("# Development"))

        val artifactsJson = """
            {
              "artifacts": [
                {"id": "a1", "title": "HANDOFF.md", "subtitle": "Artifact", "kind": "artifact", "url": ""},
                {"id": "a2", "title": "PR #12", "subtitle": "GitHub", "kind": "link", "url": "https://github.com/example/pr/12"}
              ]
            }
        """.trimIndent()
        val artifacts = BridgeStateParser.parseWorkspaceArtifacts(artifactsJson)
        assertEquals(2, artifacts.size)
        assertEquals("HANDOFF.md", artifacts[0].title)
        assertEquals("https://github.com/example/pr/12", artifacts[1].url)
    }

    @Test
    fun parsesPerModelEffortOptionsAndPreservesLimits() {
        val json = """
            {
              "url": "https://antigravity.google.com/remote/thread-1",
              "title": "Test",
              "models": [
                {
                  "id": "m1",
                  "name": "Gemini 4 Neon",
                  "tier": "High",
                  "hasEffortLevels": true,
                  "effortOptions": ["Low", "Medium", "High", "Extra"],
                  "isSelected": true
                },
                {
                  "id": "m2",
                  "name": "Gemini 4 Argon",
                  "tier": "High",
                  "hasEffortLevels": true,
                  "effortOptions": ["Low", "Medium", "High"],
                  "isSelected": false
                },
                {
                  "id": "m3",
                  "name": "GPT-OSS 120B (Medium)",
                  "tier": "Medium",
                  "hasEffortLevels": false,
                  "effortOptions": [],
                  "isSelected": false
                }
              ]
            }
        """.trimIndent()
        val snap = BridgeStateParser.parseSnapshot(json)!!
        assertEquals(3, snap.models.size)
        assertEquals(listOf("Low", "Medium", "High", "Extra"), snap.models[0].effortOptions)
        assertEquals(listOf("Low", "Medium", "High"), snap.models[1].effortOptions)
        assertEquals(emptyList<String>(), snap.models[2].effortOptions)
    }

    @Test
    fun parsesMarkdownInlineCodeWithoutTrailingSuperscripts() {
        val blocks = com.antigravity.wrapper.ui.markdown.MarkdownParser.parse(
            "Themes `(Dark, AMOLED)` and file `Agent.kt`."
        )
        assertTrue(blocks.isNotEmpty())
        val first = blocks.first() as com.antigravity.wrapper.ui.markdown.MarkdownBlock.Paragraph
        val inlineCodes = first.inlines.filterIsInstance<com.antigravity.wrapper.ui.markdown.MarkdownInline.InlineCode>()
        assertEquals(2, inlineCodes.size)
        assertEquals("(Dark, AMOLED)", inlineCodes[0].code)
        assertEquals("Agent.kt", inlineCodes[1].code)
    }
}
