package com.mvbar.android.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `social summary matches server payload`() {
        val summary = json.decodeFromString<SocialSummary>(
            """
            {
              "ok": true,
              "friends": [{
                "relationshipId": 9,
                "user": {"id": "friend-id", "email": "friend@example.com", "avatarPath": "avatar.jpg"},
                "createdAt": "2026-08-29T12:00:00Z",
                "respondedAt": "2026-08-29T12:01:00Z"
              }],
              "incoming": [],
              "outgoing": [],
              "unreadShares": 2
            }
            """.trimIndent()
        )

        assertTrue(summary.ok)
        assertEquals(9, summary.friends.single().relationshipId)
        assertEquals("friend@example.com", summary.friends.single().user.email)
        assertEquals(2, summary.unreadShares)
    }

    @Test
    fun `track share accepts integer duration and creates playable track`() {
        val response = json.decodeFromString<TrackSharesResponse>(
            """
            {
              "ok": true,
              "shares": [{
                "id": 42,
                "track": {
                  "id": 7,
                  "title": "Take It Easy",
                  "artist": "Eagles",
                  "album": "Eagles",
                  "durationMs": 211000,
                  "artPath": null,
                  "artHash": null
                },
                "sender": {"id": "sender-id", "email": "sender@example.com", "avatarPath": null},
                "message": "This is a good one",
                "createdAt": "2026-08-29T12:00:00Z",
                "readAt": null
              }],
              "total": 1,
              "unread": 1,
              "limit": 50,
              "offset": 0
            }
            """.trimIndent()
        )

        val share = response.shares.single()
        assertEquals(211000.0, share.track.durationMs ?: 0.0, 0.0)
        assertEquals("Take It Easy", share.track.toTrack().displayTitle)
        assertNull(share.readAt)
        assertFalse(share.sender.email.isBlank())
    }

    @Test
    fun `shared playlist includes owner and share timestamp`() {
        val response = json.decodeFromString<PlaylistsResponse>(
            """
            {
              "ok": true,
              "playlists": [{
                "id": 12,
                "name": "Plane mixes",
                "created_at": "2026-08-29T10:00:00Z",
                "shared_at": "2026-08-30T09:30:00Z",
                "item_count": 4,
                "owner": {"id": "owner-id", "email": "owner@example.com", "avatarPath": null},
                "is_owner": false,
                "is_collaborative": true,
                "collaborator_count": 1
              }]
            }
            """.trimIndent()
        )

        val playlist = response.playlists.single()
        assertFalse(playlist.isOwner)
        assertEquals("owner@example.com", playlist.owner?.email)
        assertEquals("2026-08-30T09:30:00Z", playlist.sharedAt)
    }
}
