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
            val parsedLimits = SimpleJson.asArray(quotaObj["limits"]).orEmpty().mapIndexedNotNull { lIdx, lEntry ->
                val lObj = SimpleJson.asObject(lEntry) ?: return@mapIndexedNotNull null
                val windowLabel = SimpleJson.asString(lObj["windowLabel"])?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@mapIndexedNotNull null
                QuotaLimitItem(
                    id = SimpleJson.asString(lObj["id"])?.takeIf { it.isNotBlank() } ?: "lim-$lIdx",
                    groupName = SimpleJson.asString(lObj["groupName"]).orEmpty().trim(),
                    windowLabel = windowLabel,
                    leftPercent = SimpleJson.asFloat(lObj["leftPercent"])?.coerceIn(0f, 100f),
                    resetLabel = SimpleJson.asString(lObj["resetLabel"]).orEmpty().trim()
                )
            }
            QuotaInfo(
                usedPercent = SimpleJson.asFloat(quotaObj["usedPercent"])?.coerceIn(0f, 100f),
                leftPercent = SimpleJson.asFloat(quotaObj["leftPercent"])?.coerceIn(0f, 100f),
                fiveHourLeftPercent = SimpleJson.asFloat(quotaObj["fiveHourLeftPercent"])?.coerceIn(0f, 100f),
                fiveHourResetLabel = SimpleJson.asString(quotaObj["fiveHourResetLabel"]).orEmpty(),
                weeklyLeftPercent = SimpleJson.asFloat(quotaObj["weeklyLeftPercent"])?.coerceIn(0f, 100f),
                weeklyResetLabel = SimpleJson.asString(quotaObj["weeklyResetLabel"]).orEmpty(),
                limits = parsedLimits,
                remainingLabel = SimpleJson.asString(quotaObj["remainingLabel"]).orEmpty(),
                resetLabel = SimpleJson.asString(quotaObj["resetLabel"]).orEmpty(),
                planLabel = SimpleJson.asString(quotaObj["planLabel"])?.takeIf { it.isNotBlank() } ?: "Antigravity",
                rawSummary = SimpleJson.asString(quotaObj["rawSummary"]).orEmpty(),
                isLiveFromServer = SimpleJson.asBoolean(quotaObj["isLive"]) == true
            )
        } else {
            QuotaInfo()
        }

        val rawActiveModel = SimpleJson.asString(root["activeModel"]).orEmpty()
            .replace(Regex("\\.\\s*Applies to messages sent after this turn\\.?$", RegexOption.IGNORE_CASE), "")
            .trim()
        val cleanActiveModel = if (rawActiveModel.equals("No Model Selected", ignoreCase = true) ||
            rawActiveModel.equals("Select model", ignoreCase = true)
        ) {
            ""
        } else {
            rawActiveModel
        }

        val rawProject = SimpleJson.asString(root["activeProject"]).orEmpty().trim()
        val cleanProject = if (rawProject.equals("No Project", ignoreCase = true) ||
            rawProject.equals("Select project", ignoreCase = true)
        ) {
            ""
        } else {
            rawProject
        }

        val projects = SimpleJson.asArray(root["projects"]).orEmpty().mapIndexedNotNull { pIdx, entry ->
            val obj = SimpleJson.asObject(entry) ?: return@mapIndexedNotNull null
            val name = SimpleJson.asString(obj["name"])?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            ProjectOption(
                id = SimpleJson.asString(obj["id"])?.takeIf { it.isNotBlank() } ?: "proj-$pIdx",
                name = name,
                isSelected = SimpleJson.asBoolean(obj["isSelected"]) == true
            )
        }

        return BridgeSnapshot(
            currentUrl = SimpleJson.asString(root["url"]).orEmpty(),
            conversationTitle = normalizeConversationTitle(SimpleJson.asString(root["title"])),
            authRequired = SimpleJson.asBoolean(root["authRequired"]) == true,
            isStreaming = SimpleJson.asBoolean(root["isStreaming"]) == true,
            activeModelName = cleanActiveModel,
            activeProject = cleanProject,
            permissionsPreset = SimpleJson.asString(root["permissionsPreset"]).orEmpty().trim(),
            models = models,
            projects = projects,
            conversations = conversations,
            messages = messages,
            quota = quota,
            hasOlderMessages = SimpleJson.asBoolean(root["hasOlderMessages"]) == true,
            hasSidePanel = SimpleJson.asBoolean(root["hasSidePanel"]) == true,
            composerReady = SimpleJson.asBoolean(root["composerReady"]) == true,
            diagnosticsNote = SimpleJson.asString(root["diagnosticsNote"]).orEmpty()
        )
    }

    fun parseDiffReview(json: String?): DiffReviewData {
        if (json.isNullOrBlank()) return DiffReviewData(error = "No diff data returned")
        val root = try {
            SimpleJson.asObject(SimpleJson.parse(json))
        } catch (_: Exception) {
            null
        } ?: return DiffReviewData(error = "Unable to parse diff data")

        val files = SimpleJson.asArray(root["files"]).orEmpty().mapIndexedNotNull { fIdx, entry ->
            val obj = SimpleJson.asObject(entry) ?: return@mapIndexedNotNull null
            val fileName = SimpleJson.asString(obj["fileName"])?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            val lines = SimpleJson.asArray(obj["lines"]).orEmpty().mapNotNull { lEntry ->
                val lObj = SimpleJson.asObject(lEntry) ?: return@mapNotNull null
                val kindStr = SimpleJson.asString(lObj["kind"]).orEmpty().lowercase()
                val kind = when (kindStr) {
                    "added" -> DiffLineKind.ADDED
                    "removed" -> DiffLineKind.REMOVED
                    "hunk" -> DiffLineKind.HUNK
                    else -> DiffLineKind.CONTEXT
                }
                DiffLine(
                    lineNumber = SimpleJson.asString(lObj["num"]).orEmpty(),
                    text = SimpleJson.asString(lObj["text"]).orEmpty(),
                    kind = kind
                )
            }
            DiffFile(
                id = SimpleJson.asString(obj["id"])?.takeIf { it.isNotBlank() } ?: "file-$fIdx",
                fileName = fileName,
                path = SimpleJson.asString(obj["path"]).orEmpty().trim(),
                addedLines = SimpleJson.asInt(obj["addedLines"]) ?: 0,
                removedLines = SimpleJson.asInt(obj["removedLines"]) ?: 0,
                lines = lines
            )
        }
        val err = SimpleJson.asString(root["error"])?.takeIf { it.isNotBlank() }
        return DiffReviewData(
            title = SimpleJson.asString(root["title"])?.takeIf { it.isNotBlank() } ?: "Review Changes",
            subtitle = SimpleJson.asString(root["subtitle"]).orEmpty(),
            files = files,
            isLoading = false,
            error = if (files.isEmpty()) (err ?: "No file diffs available in this view") else null
        )
    }

    fun parseDocumentViewer(json: String?): DocumentViewerData {
        if (json.isNullOrBlank()) return DocumentViewerData(error = "No document content returned")
        val root = try {
            SimpleJson.asObject(SimpleJson.parse(json))
        } catch (_: Exception) {
            null
        } ?: return DocumentViewerData(error = "Unable to parse document content")

        val title = SimpleJson.asString(root["title"]).orEmpty().trim()
        val md = SimpleJson.asString(root["markdown"]).orEmpty().trim()
        val raw = SimpleJson.asString(root["raw"]).orEmpty().trim()
        val err = SimpleJson.asString(root["error"])?.takeIf { it.isNotBlank() }
        return DocumentViewerData(
            title = title.ifBlank { "Document" },
            markdownContent = md,
            rawContent = raw.ifBlank { md },
            isLoading = false,
            error = if (md.isBlank() && raw.isBlank()) (err ?: "Document is empty or environment is paused") else null
        )
    }

    fun parseWorkspaceArtifacts(json: String?): List<WorkspaceArtifactItem> {
        if (json.isNullOrBlank()) return emptyList()
        val root = try {
            SimpleJson.asObject(SimpleJson.parse(json))
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        return SimpleJson.asArray(root["artifacts"]).orEmpty().mapIndexedNotNull { idx, entry ->
            val obj = SimpleJson.asObject(entry) ?: return@mapIndexedNotNull null
            val title = SimpleJson.asString(obj["title"])?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            WorkspaceArtifactItem(
                id = SimpleJson.asString(obj["id"])?.takeIf { it.isNotBlank() } ?: "ws-art-$idx",
                title = title,
                subtitle = SimpleJson.asString(obj["subtitle"]).orEmpty().trim(),
                url = SimpleJson.asString(obj["url"]).orEmpty().trim(),
                kind = SimpleJson.asString(obj["kind"])?.takeIf { it.isNotBlank() } ?: "artifact"
            )
        }
    }
}
