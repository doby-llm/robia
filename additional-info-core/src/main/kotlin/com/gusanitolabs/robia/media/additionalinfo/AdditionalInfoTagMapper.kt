package com.gusanitolabs.robia.media.additionalinfo

import com.gusanitolabs.robia.core.model.AdditionalInfoLabelScore
import com.gusanitolabs.robia.core.model.AdditionalInfoPrediction

object AdditionalInfoTagMapper {
    fun map(
        scoresByHead: Map<String, FloatArray>,
        availableTagIds: Set<String>,
        config: AdditionalInfoModelConfig,
    ): AdditionalInfoPrediction? {
        val categoryHead = config.requireHead(CATEGORY_HEAD)
        val seasonHead = config.requireHead(SEASON_HEAD)
        val occasionHead = config.requireHead(OCCASION_HEAD)
        val categoryScores = labelScores(categoryHead, scoresByHead[CATEGORY_HEAD]) ?: return null
        val seasonScores = labelScores(seasonHead, scoresByHead[SEASON_HEAD]) ?: return null
        val occasionScores = labelScores(occasionHead, scoresByHead[OCCASION_HEAD]) ?: return null

        val selected = buildSet {
            addAll(selectCategory(categoryScores, categoryHead, availableTagIds))
            addAll(selectMultiHead(seasonScores, seasonHead, availableTagIds))
            addAll(selectMultiHead(occasionScores, occasionHead, availableTagIds))
        }
        return AdditionalInfoPrediction(
            selectedTagIds = selected,
            categoryScores = categoryScores,
            seasonScores = seasonScores,
            occasionScores = occasionScores,
        )
    }

    fun unmappedRequiredLabels(
        config: AdditionalInfoModelConfig,
        defaultTagIds: Set<String>,
    ): List<String> = config.heads.flatMap { head ->
        head.labels.zip(head.tagIds).mapNotNull { (label, tagId) ->
            when {
                label == head.multiSeasonLabel -> null
                tagId == null || tagId !in defaultTagIds -> "${head.name}:$label"
                else -> null
            }
        }
    }

    private fun labelScores(head: AdditionalInfoHeadSpec, scores: FloatArray?): List<AdditionalInfoLabelScore>? {
        if (scores == null || scores.size != head.width) return null
        return head.labels.indices.map { index ->
            AdditionalInfoLabelScore(
                label = head.labels[index],
                tagId = head.tagIds[index],
                score = scores[index],
            )
        }
    }

    private fun selectCategory(
        scores: List<AdditionalInfoLabelScore>,
        head: AdditionalInfoHeadSpec,
        availableTagIds: Set<String>,
    ): Set<String> {
        val sorted = scores.sortedByDescending(AdditionalInfoLabelScore::score)
        val top = sorted.firstOrNull() ?: return emptySet()
        val secondScore = sorted.getOrNull(1)?.score ?: 0f
        val tagId = top.tagId ?: return emptySet()
        val margin = head.margin ?: 0f
        return if (top.score >= head.threshold && top.score - secondScore >= margin && tagId in availableTagIds) {
            setOf(tagId)
        } else {
            emptySet()
        }
    }

    private fun selectMultiHead(
        scores: List<AdditionalInfoLabelScore>,
        head: AdditionalInfoHeadSpec,
        availableTagIds: Set<String>,
    ): Set<String> {
        val top = scores.maxByOrNull(AdditionalInfoLabelScore::score) ?: return emptySet()
        if (head.multiSeasonLabel != null && top.label == head.multiSeasonLabel && top.score >= head.threshold) {
            return head.multiSeasonTagIds.filterTo(linkedSetOf()) { tagId -> tagId in availableTagIds }
        }
        if (top.score < head.threshold) return emptySet()

        val multiThreshold = head.multiSelectThreshold ?: head.threshold
        val nearTieMargin = head.nearTieMargin ?: 0f
        return scores.mapNotNullTo(linkedSetOf()) { score ->
            val tagId = score.tagId
            when {
                tagId == null || tagId !in availableTagIds -> null
                score.score >= multiThreshold -> tagId
                top.score - score.score <= nearTieMargin -> tagId
                else -> null
            }
        }
    }

    private const val CATEGORY_HEAD = "category"
    private const val SEASON_HEAD = "season"
    private const val OCCASION_HEAD = "occasion"
}
