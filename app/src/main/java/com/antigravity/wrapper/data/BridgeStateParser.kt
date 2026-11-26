package com.antigravity.wrapper.data

/**
 * Converts JSON snapshots emitted by the headless Antigravity DOM bridge into
 * strongly-typed native UI models, normalizing model names/tiers and titles consistently.
 */
object BridgeStateParser {

    private val TRAILING_TIER_PAREN_REGEX =
        Regex("^(.+?)\\s*\\((Minimal|Low|Medium|High|Extra|Fast)\\)\\s*$", RegexOption.IGNORE_CASE)

    private val TRAILING_ACTIVE_MODEL_NOTE_REGEX =
        Regex("\\.\\s*Applies to messages sent after this turn\\.?$", RegexOption.IGNORE_CASE)

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
                projectName = SimpleJson.asString(obj["projectName"]).orEmpty().trim(),
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
                parseReasoningBlock(rObj, index, rIdx)
            }
            val reasoningById = if (reasoning.isNotEmpty()) {
                HashMap<String, ReasoningBlock>(reasoning.size * 2).apply {
                    for (block in reasoning) {
                        put(block.id, block)
                    }
                }
            } else {
                emptyMap()
            }

            val segments = SimpleJson.asArray(obj["segments"]).orEmpty().mapIndexedNotNull { segIdx, segEntry ->
                val segObj = SimpleJson.asObject(segEntry) ?: return@mapIndexedNotNull null
                val segId = SimpleJson.asString(segObj["id"])?.takeIf { it.isNotBlank() }
                    ?: "seg-$index-$segIdx"
                val kindRaw = SimpleJson.asString(segObj["kind"]).orEmpty().lowercase()
                when (kindRaw) {
                    "reasoning" -> {
                        val inlineObj = SimpleJson.asObject(segObj["reasoning"])
                        val block = if (inlineObj != null) {
                            parseReasoningBlock(inlineObj, index, segIdx)
                        } else {
                            val refId = SimpleJson.asString(segObj["reasoningId"]).orEmpty()
                            reasoningById[refId]
                        } ?: return@mapIndexedNotNull null
                        AssistantSegment(
                            id = segId,
                            kind = AssistantSegmentKind.REASONING,
                            reasoningBlock = block
                        )
                    }
                    "markdown" -> {
                        val segMd = SimpleJson.asString(segObj["markdown"]).orEmpty().trim()
                        if (segMd.isBlank()) return@mapIndexedNotNull null
                        AssistantSegment(
                            id = segId,
                            kind = AssistantSegmentKind.MARKDOWN,
                            markdown = segMd
                        )
                    }
                    else -> null
                }
            }

            val effectiveMarkdown = markdown.ifBlank {
                segments.asSequence()
                    .filter { it.kind == AssistantSegmentKind.MARKDOWN && it.markdown.isNotBlank() }
                    .joinToString("\n\n") { it.markdown }
            }
            val effectiveReasoning = reasoning.ifEmpty {
                segments.mapNotNull { it.reasoningBlock }
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

            val isMsgStreaming = SimpleJson.asBoolean(obj["isStreaming"]) == true
            if (effectiveMarkdown.isBlank() && effectiveReasoning.isEmpty() && segments.isEmpty() && artifacts.isEmpty() && !isMsgStreaming) {
                return@mapIndexedNotNull null
            }

            ChatMessage(
                id = SimpleJson.asString(obj["id"])?.takeIf { it.isNotBlank() } ?: "msg-$index",
                role = role,
                markdownContent = effectiveMarkdown,
                timestampLabel = SimpleJson.asString(obj["timestamp"]).orEmpty().trim(),
                reasoningBlocks = effectiveReasoning,
                artifacts = artifacts,
                isStreaming = isMsgStreaming,
                segments = segments
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
            .replace(TRAILING_ACTIVE_MODEL_NOTE_REGEX, "")
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

    private fun parseReasoningBlock(
        rObj: Map<String, Any?>,
        msgIndex: Int,
        rIdx: Int
    ): ReasoningBlock? {
        val content = SimpleJson.asString(rObj["content"]).orEmpty().trim()
        val title = SimpleJson.asString(rObj["title"])?.trim()?.takeIf { it.isNotBlank() }
            ?: "Worked"
        val sections = SimpleJson.asArray(rObj["sections"]).orEmpty().mapIndexedNotNull { sIdx, sEntry ->
            val sObj = SimpleJson.asObject(sEntry) ?: return@mapIndexedNotNull null
            val steps = SimpleJson.asArray(sObj["steps"]).orEmpty().mapIndexedNotNull { stIdx, stEntry ->
                val stObj = SimpleJson.asObject(stEntry) ?: return@mapIndexedNotNull null
                val stTitle = SimpleJson.asString(stObj["title"])?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@mapIndexedNotNull null
                val kindRaw = SimpleJson.asString(stObj["kind"]).orEmpty().lowercase()
                val kind = when (kindRaw) {
                    "command" -> ReasoningStepKind.COMMAND
                    "file" -> ReasoningStepKind.FILE
                    "section" -> ReasoningStepKind.SECTION
                    else -> ReasoningStepKind.NOTE
                }
                ReasoningStepItem(
                    id = SimpleJson.asString(stObj["id"])?.takeIf { it.isNotBlank() }
                        ?: "st-$msgIndex-$rIdx-$sIdx-$stIdx",
                    title = stTitle,
                    kind = kind,
                    detail = SimpleJson.asString(stObj["detail"]).orEmpty().trim(),
                    fileName = SimpleJson.asString(stObj["fileName"]).orEmpty().trim(),
                    lineRange = SimpleJson.asString(stObj["lineRange"]).orEmpty().trim(),
                    badgeText = SimpleJson.asString(stObj["badgeText"]).orEmpty().trim(),
                    actionVerb = SimpleJson.asString(stObj["actionVerb"]).orEmpty().trim(),
                    isExpandable = SimpleJson.asBoolean(stObj["isExpandable"]) == true
                )
            }
            val secTitle = SimpleJson.asString(sObj["title"]).orEmpty().trim()
            if (secTitle.isBlank() && steps.isEmpty()) return@mapIndexedNotNull null
            ReasoningSection(
                id = SimpleJson.asString(sObj["id"])?.takeIf { it.isNotBlank() }
                    ?: "sec-$msgIndex-$rIdx-$sIdx",
                title = secTitle,
                steps = steps,
                defaultExpanded = SimpleJson.asBoolean(sObj["defaultExpanded"]) == true
            )
        }
        if (content.isBlank() && sections.isEmpty() && title == "Worked") return null
        return ReasoningBlock(
            id = SimpleJson.asString(rObj["id"])?.takeIf { it.isNotBlank() } ?: "r-$msgIndex-$rIdx",
            title = title,
            durationLabel = SimpleJson.asString(rObj["duration"]).orEmpty(),
            content = content,
            sections = sections,
            isStreaming = SimpleJson.asBoolean(rObj["isStreaming"]) == true
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

    fun extractExternalLink(errorOrUrl: String?): String? {
        val raw = errorOrUrl?.trim().orEmpty()
        if (raw.startsWith("EXTERNAL_LINK:", ignoreCase = true)) {
            val url = raw.substringAfter(':').trim()
            return url.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        }
        return null
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
        val externalUrl = SimpleJson.asString(root["externalUrl"])
            ?.trim()
            ?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            ?: extractExternalLink(err)
        val resolvedError = if (md.isBlank() && raw.isBlank()) {
            when {
                externalUrl != null -> "EXTERNAL_LINK:$externalUrl"
                err != null -> err
                else -> "Document is empty or environment is paused"
            }
        } else {
            null
        }
        return DocumentViewerData(
            title = title.ifBlank { "Document" },
            markdownContent = md,
            rawContent = raw.ifBlank { md },
            isLoading = false,
            error = resolvedError
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
