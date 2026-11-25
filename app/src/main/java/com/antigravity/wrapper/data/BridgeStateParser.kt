package com.antigravity.wrapper.data

/**
 * Converts JSON snapshots emitted by the headless Antigravity DOM bridge into
 * strongly-typed native UI models, normalizing model names/tiers and titles consistently.
 */
object BridgeStateParser {

    private val TRAILING_TIER_PAREN_REGEX =
        Regex("^(.+?)\\s*\\((Low|Medium|High|Extra|Fast)\\)\\s*$", RegexOption.IGNORE_CASE)

    fun normalizeModelNameAndTier(rawName: String, rawTier: String): Pair<String, String> {
        val cleanName = rawName.trim()
        val cleanTier = rawTier.trim()
        if (cleanTier.isNotBlank()) {
            return cleanName to cleanTier
        }
        val match = TRAILING_TIER_PAREN_REGEX.matchEntire(cleanName)
        return if (match != null) {
            match.groupValues[1].trim() to match.groupValues[2].trim()
        } else {
            cleanName to ""
        }
    }

    fun normalizeConversationTitle(rawTitle: String?): String {
        val cleaned = rawTitle?.trim().orEmpty()
        if (cleaned.isBlank() ||
            cleaned.equals("Google Antigravity", ignoreCase = true) ||
            cleaned.equals("Antigravity", ignoreCase = true)
        ) {
            return "Antigravity"
        }
        return cleaned
    }

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
                projectName = SimpleJson.asString(obj["projectName"]).orEmpty(),
                preview = SimpleJson.asString(obj["preview"]).orEmpty(),
                updatedLabel = SimpleJson.asString(obj["updatedLabel"]).orEmpty().trim(),
                isActive = SimpleJson.asBoolean(obj["isActive"]) == true
            )
        }

        val models = SimpleJson.asArray(root["models"]).orEmpty().mapNotNull { entry ->
            val obj = SimpleJson.asObject(entry) ?: return@mapNotNull null
            val rawName = SimpleJson.asString(obj["name"])?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val rawTier = SimpleJson.asString(obj["tier"]).orEmpty()
            val (name, tier) = normalizeModelNameAndTier(rawName, rawTier)
            val id = SimpleJson.asString(obj["id"])?.takeIf { it.isNotBlank() } ?: name
            val effortOptions = SimpleJson.asArray(obj["effortOptions"])
                .orEmpty()
                .mapNotNull { SimpleJson.asString(it)?.trim()?.takeIf { s -> s.isNotEmpty() } }
            ModelOption(
                id = id,
                name = name,
                tier = tier,
                description = SimpleJson.asString(obj["description"]).orEmpty(),
                hasEffortLevels = SimpleJson.asBoolean(obj["hasEffortLevels"]) == true,
                effortOptions = effortOptions,
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
                timestampLabel = SimpleJson.asString(obj["timestamp"]).orEmpty().trim(),
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

        val rawActiveModel = SimpleJson.asString(root["activeModel"]).orEmpty().trim()
        val cleanActiveModel = if (rawActiveModel.equals("No Model Selected", ignoreCase = true) ||
            rawActiveModel.equals("Select model", ignoreCase = true)
        ) {
            ""
        } else {
            rawActiveModel
        }

        return BridgeSnapshot(
            currentUrl = SimpleJson.asString(root["url"]).orEmpty(),
            conversationTitle = normalizeConversationTitle(SimpleJson.asString(root["title"])),
            authRequired = SimpleJson.asBoolean(root["authRequired"]) == true,
            isStreaming = SimpleJson.asBoolean(root["isStreaming"]) == true,
            activeModelName = cleanActiveModel,
            activeProject = SimpleJson.asString(root["activeProject"]).orEmpty().trim(),
            permissionsPreset = SimpleJson.asString(root["permissionsPreset"]).orEmpty().trim(),
            models = models,
            conversations = conversations,
            messages = messages,
            quota = quota,
            composerReady = SimpleJson.asBoolean(root["composerReady"]) == true,
            diagnosticsNote = SimpleJson.asString(root["diagnosticsNote"]).orEmpty()
        )
    }
}
