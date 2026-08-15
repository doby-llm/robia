package com.gusanitolabs.robia.media

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePipelineContractTest {
    @Test
    fun cacheKeyChangesForRevisionPurposeAndMeasuredTarget() {
        val base = ImageCacheKey(
            sourceRevision = "garment-revision-a",
            purpose = ImagePurpose.Browse,
            target = ImageTargetBounds(384, 288),
        )

        assertNotEquals(base.stableId, base.copy(sourceRevision = "garment-revision-b").stableId)
        assertNotEquals(base.stableId, base.copy(purpose = ImagePurpose.Detail).stableId)
        assertNotEquals(base.stableId, base.copy(target = ImageTargetBounds(512, 384)).stableId)
        assertTrue(!base.stableId.contains("garment-revision"))
    }

    @Test
    fun requestCacheKeyIncludesSourceUriWhenRevisionIsShared() {
        val first = ImageRequest(
            sourceUri = Uri.parse("content://com.example/first.jpg"),
            purpose = ImagePurpose.BatchPreview,
            targetBounds = ImageTargetBounds(384, 384),
            sourceRevision = "draft-1",
        )
        val second = first.copy(sourceUri = Uri.parse("content://com.example/second.jpg"))

        assertNotEquals(first.cacheKey().stableId, second.cacheKey().stableId)
    }

    @Test
    fun originalRequestsUseAnUnboundedTargetIdentity() {
        val request = ImageRequest(
            sourceUri = Uri.parse("content://com.example/detail.jpg"),
            purpose = ImagePurpose.Detail,
            targetBounds = ImageTargetBounds(512, 512),
            allowOriginal = true,
        )

        assertEquals(ImageTargetBounds(0, 0), request.effectiveTargetBounds())
    }

    @Test
    fun measuredBoundsAreClampedWithoutAllowingAnOriginalFallback() {
        val measured = ImageTargetBounds(1080, 1920).boundedBy(512)

        assertTrue(measured.maxEdgePx <= 512)
        assertTrue(measured.widthPx > 0)
        assertTrue(measured.heightPx > 0)
    }

    @Test
    fun telemetryNeverLogsRawSourceUri() {
        val messages = mutableListOf<String>()
        val sink = SampledImageTelemetrySink(sampleEvery = 1) { _, message -> messages += message }
        sink.record(
            ImageTelemetryEvent(
                stage = "error",
                requestId = "hashed-request-id",
                purpose = ImagePurpose.Detail,
                priority = ImagePriority.Visible,
                target = ImageTargetBounds(512, 384),
                error = "content://com.example/private/garment.jpg /data/user/0/secret",
            ),
        )

        assertTrue(messages.size == 1)
        assertTrue(!messages.single().contains("content://"))
        assertTrue(!messages.single().contains("/data/user"))
        assertTrue(messages.single().contains("requestId=hashed-request-id"))
    }

    @Test
    fun telemetryUsesTheSanitizedEvictionStageContract() {
        val messages = mutableListOf<String>()
        val sink = SampledImageTelemetrySink(sampleEvery = 1) { _, message -> messages += message }
        sink.record(
            ImageTelemetryEvent(
                stage = "eviction",
                requestId = "hashed-request-id",
                purpose = ImagePurpose.Browse,
                priority = ImagePriority.Visible,
                target = ImageTargetBounds(256, 256),
                evictionReason = "memory capacity /private/cache",
            ),
        )

        assertTrue(messages.single().contains("stage=eviction"))
        assertTrue(messages.single().contains("evictionReason=memory_capacity__private_cache"))
        assertTrue(!messages.single().contains("/private/cache"))
    }

    @Test
    fun requiredImageStagesBypassSamplingSoPerformanceArtifactsContainTheFullPipeline() {
        val messages = mutableListOf<String>()
        val sink = SampledImageTelemetrySink(sampleEvery = 100) { _, message -> messages += message }

        REQUIRED_IMAGE_STAGES.forEach { stage ->
            sink.record(
                ImageTelemetryEvent(
                    stage = stage,
                    requestId = "hashed-request-id-$stage",
                    purpose = ImagePurpose.Browse,
                    priority = ImagePriority.Visible,
                    target = ImageTargetBounds(256, 256),
                ),
            )
        }

        assertEquals(REQUIRED_IMAGE_STAGES.size, messages.size)
        REQUIRED_IMAGE_STAGES.forEach { stage ->
            assertTrue(messages.any { message -> message.contains("stage=$stage") })
        }
    }
}
