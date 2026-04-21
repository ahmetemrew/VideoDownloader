package com.basitce.videodownloader.ui.platform

import com.basitce.videodownloader.data.model.DownloadProfile
import com.basitce.videodownloader.data.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformBatchPlannerTest {

    @Test
    fun `mergePendingLinks removes duplicates and preserves insertion order`() {
        val existing = listOf(
            PendingBatchLink(
                url = "https://www.youtube.com/watch?v=first1234567",
                platform = Platform.YOUTUBE,
                videoId = "first1234567"
            ),
            PendingBatchLink(
                url = "https://www.instagram.com/reel/abc123/",
                platform = Platform.INSTAGRAM,
                videoId = "abc123"
            )
        )
        val incoming = listOf(
            PendingBatchLink(
                url = "https://www.instagram.com/reel/abc123/",
                platform = Platform.INSTAGRAM,
                videoId = "abc123"
            ),
            PendingBatchLink(
                url = "https://www.tiktok.com/@user/video/1234567890",
                platform = Platform.TIKTOK,
                videoId = "1234567890"
            )
        )

        val merged = PlatformBatchPlanner.mergePendingLinks(existing, incoming)

        assertEquals(
            listOf(
                "https://www.youtube.com/watch?v=first1234567",
                "https://www.instagram.com/reel/abc123/",
                "https://www.tiktok.com/@user/video/1234567890"
            ),
            merged.map { it.url }
        )
    }

    @Test
    fun `readyCountText formats empty single and plural labels`() {
        assertEquals("Hazır link yok", PlatformBatchPlanner.readyCountText(0))
        assertEquals("1 link hazır", PlatformBatchPlanner.readyCountText(1))
        assertEquals("4 link hazır", PlatformBatchPlanner.readyCountText(4))
    }

    @Test
    fun `buildQueuedRequests uses selected profile and stable request fields`() {
        val links = listOf(
            PendingBatchLink(
                url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                platform = Platform.YOUTUBE,
                videoId = "dQw4w9WgXcQ"
            ),
            PendingBatchLink(
                url = "https://www.instagram.com/reel/CxYz123/",
                platform = Platform.INSTAGRAM,
                videoId = "CxYz123"
            )
        )

        val requests = PlatformBatchPlanner.buildQueuedRequests(
            links = links,
            profile = DownloadProfile.QUALITY_720P
        )

        assertEquals(2, requests.size)
        assertEquals("yt-dQw4w9WgXcQ", requests[0].fileName)
        assertEquals(Platform.YOUTUBE, requests[0].platform)
        assertEquals(DownloadProfile.QUALITY_720P, requests[0].downloadProfile)
        assertTrue(requests[0].downloadSelector.contains("720"))

        assertEquals("ig-CxYz123", requests[1].fileName)
        assertEquals(Platform.INSTAGRAM, requests[1].platform)
        assertEquals(DownloadProfile.QUALITY_720P, requests[1].downloadProfile)
    }

    @Test
    fun `restoreSnapshot rebuilds saved profile pending links and ids`() {
        val savedLinks = listOf(
            PendingBatchLink(
                url = "https://www.facebook.com/watch/?v=1234567890",
                platform = Platform.FACEBOOK,
                videoId = "1234567890"
            )
        )

        val restored = PlatformBatchPlanner.restoreSnapshot(
            pendingLinks = savedLinks,
            activeBatchIds = listOf(11L, 22L, 33L),
            selectedProfileName = DownloadProfile.AUDIO_ONLY.name,
            inputText = "hazır metin",
            fallbackProfile = DownloadProfile.MAX
        )

        assertEquals(savedLinks, restored.pendingLinks)
        assertEquals(listOf(11L, 22L, 33L), restored.activeBatchIds)
        assertEquals(DownloadProfile.AUDIO_ONLY, restored.selectedProfile)
        assertEquals("hazır metin", restored.inputText)
    }
}
