package com.mvbar.android.data.repository

import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.model.FriendRequestBody
import com.mvbar.android.data.model.ShareTrackBody

class SocialRepository {
    private val api get() = ApiClient.api

    suspend fun getSummary() = api.getSocialSummary()

    suspend fun searchUsers(query: String) = api.searchSocialUsers(query.trim())

    suspend fun sendFriendRequest(userId: String) =
        api.sendFriendRequest(FriendRequestBody(userId))

    suspend fun acceptFriendRequest(relationshipId: Int) =
        api.acceptFriendRequest(relationshipId)

    suspend fun removeFriendRequest(relationshipId: Int) =
        api.removeFriendRequest(relationshipId)

    suspend fun removeFriend(userId: String) = api.removeFriend(userId)

    suspend fun getShares(limit: Int = 50, offset: Int = 0) =
        api.getTrackShares(limit, offset)

    suspend fun markShareRead(shareId: Int) = api.markTrackShareRead(shareId)

    suspend fun markAllSharesRead() = api.markAllTrackSharesRead()

    suspend fun deleteShare(shareId: Int) = api.deleteTrackShare(shareId)

    suspend fun getShareTargets(trackId: Int) = api.getShareTargets(trackId)

    suspend fun shareTrack(trackId: Int, recipientIds: List<String>, message: String?) =
        api.shareTrack(
            ShareTrackBody(
                trackId = trackId,
                recipientIds = recipientIds,
                message = message?.trim()?.takeIf { it.isNotEmpty() }
            )
        )
}
