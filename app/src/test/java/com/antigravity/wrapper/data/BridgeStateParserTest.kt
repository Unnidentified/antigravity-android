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
                "remainingLabel": "58% remaining",
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
    fun returnsNullOnMalformedOrBlankInput() {
        assertEquals(null, BridgeStateParser.parseSnapshot(null))
        assertEquals(null, BridgeStateParser.parseSnapshot(""))
        assertEquals(null, BridgeStateParser.parseSnapshot("{bad json"))
    }
}
