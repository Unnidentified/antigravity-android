package com.antigravity.wrapper.data

/**
 * Converts JSON snapshots emitted by the headless Antigravity DOM bridge into
 * strongly-typed native UI models.
 */
object BridgeStateParser {

    fun parseSnapshot(json: String?): BridgeSnapshot? {
        if (json.isNullOrBlank()) return null
        val root = try {
            SimpleJson.asObject(SimpleJson.parse(json))
        } catch (_: Exception) {
            null
        } ?: return null

        val conversations = SimpleJson.asArray(root["conversations"]).orEmpty().mapNotNull { entry ->
            val obj = SimpleJson.asObject(entry) ?: return@mapNotNull null
            val id = SimpleJson.asString(obj["id"])?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = SimpleJson.asString(obj["title"])?.trim()?.takeIf { it.isNotBlank() }
                ?: "Untitled chat"
            ChatConversation(
                id = id,
                title = title,
                url = SimpleJson.asString(obj["url"]).orEmpty(),
                preview = SimpleJson.asString(obj["preview"]).orEmpty(),
                updatedLabel = SimpleJson.asString(obj["updatedLabel"]).orEmpty(),
                isActive = SimpleJson.asBoolean(obj["isActive"]) == true
            )
        }

        val models = SimpleJson.asArray(root["models"]).orEmpty().mapNotNull { entry ->
            val obj = SimpleJson.asObject(entry) ?: return@mapNotNull null
            val name = SimpleJson.asString(obj["name"])?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val id = SimpleJson.asString(obj["id"])?.takeIf { it.isNotBlank() } ?: name
            ModelOption(
                id = id,
                name = name,
                tier = SimpleJson.asString(obj["tier"]).orEmpty(),
                description = SimpleJson.asString(obj["description"]).orEmpty(),
                isSelected = SimpleJson.asBoolean(obj["isSelected"]) == true
            )
        }

        val messages = SimpleJson.asArray(root["messages"]).orEmpty().mapIndexedNotNull { index, entry ->
            val obj = SimpleJson.asObject(entry) ?: return@mapIndexedNotNull null
            val roleRaw = SimpleJson.asString(obj["role"])?.lowercase()
            val role = if (roleRaw == "user") MessageRole.USER else MessageRole.ASSISTANT
            val markdown = SimpleJson.asString(obj["markdown"]).orEmpty().trim()

            val reasoning = SimpleJson.asArray(obj["reasoning"]).orEmpty().mapIndexedNotNull { rIdx, rEntry ->
                val rObj = SimpleJson.asObject(rEntry) ?: return@mapIndexedNotNull null
                val content = SimpleJson.asString(rObj["content"]).orEmpty().trim()
                val title = SimpleJson.asString(rObj["title"])?.trim()?.takeIf { it.isNotBlank() }
                    ?: "Reasoning"
                if (content.isBlank() && title == "Reasoning") return@mapIndexedNotNull null
                ReasoningBlock(
                    id = SimpleJson.asString(rObj["id"])?.takeIf { it.isNotBlank() } ?: "r-$index-$rIdx",
                    title = title,
                    durationLabel = SimpleJson.asString(rObj["duration"]).orEmpty(),
                    content = content,
                    isStreaming = SimpleJson.asBoolean(rObj["isStreaming"]) == true
                )
            }

            val artifacts = SimpleJson.asArray(obj["artifacts"]).orEmpty().mapIndexedNotNull { aIdx, aEntry ->
                val aObj = SimpleJson.asObject(aEntry) ?: return@mapIndexedNotNull null
                val title = SimpleJson.asString(aObj["title"])?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@mapIndexedNotNull null
                ArtifactCard(
                    id = SimpleJson.asString(aObj["id"])?.takeIf { it.isNotBlank() } ?: "a-$index-$aIdx",
                    title = title,
                    subtitle = SimpleJson.asString(aObj["subtitle"]).orEmpty(),
                    actionLabel = SimpleJson.asString(aObj["actionLabel"])?.takeIf { it.isNotBlank() } ?: "Open",
                    url = SimpleJson.asString(aObj["url"]).orEmpty(),
                    addedLines = SimpleJson.asInt(aObj["addedLines"]),
                    removedLines = SimpleJson.asInt(aObj["removedLines"])
                )
            }

            if (markdown.isBlank() && reasoning.isEmpty() && artifacts.isEmpty()) {
                return@mapIndexedNotNull null
            }

            ChatMessage(
                id = SimpleJson.asString(obj["id"])?.takeIf { it.isNotBlank() } ?: "msg-$index",
                role = role,
                markdownContent = markdown,
                reasoningBlocks = reasoning,
                artifacts = artifacts,
                isStreaming = SimpleJson.asBoolean(obj["isStreaming"]) == true
            )
        }

        val quotaObj = SimpleJson.asObject(root["quota"])
        val quota = if (quotaObj != null) {
            QuotaInfo(
                usedPercent = SimpleJson.asFloat(quotaObj["usedPercent"])?.coerceIn(0f, 100f),
                remainingLabel = SimpleJson.asString(quotaObj["remainingLabel"]).orEmpty(),
                resetLabel = SimpleJson.asString(quotaObj["resetLabel"]).orEmpty(),
                planLabel = SimpleJson.asString(quotaObj["planLabel"])?.takeIf { it.isNotBlank() } ?: "Antigravity",
                rawSummary = SimpleJson.asString(quotaObj["rawSummary"]).orEmpty(),
                isLiveFromServer = SimpleJson.asBoolean(quotaObj["isLive"]) == true
            )
        } else {
            QuotaInfo()
        }

        return BridgeSnapshot(
            currentUrl = SimpleJson.asString(root["url"]).orEmpty(),
            conversationTitle = SimpleJson.asString(root["title"])?.trim()
                ?.takeIf { it.isNotBlank() } ?: "Antigravity",
            authRequired = SimpleJson.asBoolean(root["authRequired"]) == true,
            isStreaming = SimpleJson.asBoolean(root["isStreaming"]) == true,
            activeModelName = SimpleJson.asString(root["activeModel"]).orEmpty().trim(),
            models = models,
            conversations = conversations,
            messages = messages,
            quota = quota,
            composerReady = SimpleJson.asBoolean(root["composerReady"]) == true,
            diagnosticsNote = SimpleJson.asString(root["diagnosticsNote"]).orEmpty()
        )
    }
}
