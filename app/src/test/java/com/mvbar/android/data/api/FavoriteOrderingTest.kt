package com.mvbar.android.data.api

import com.mvbar.android.data.model.FavoritesResponse
import com.mvbar.android.data.model.Track
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class FavoriteOrderingTest {
    @Test fun loadsAllPagesInServerOrder() = runBlocking {
        val ids = (1..405).reversed().toList()
        val offsets = mutableListOf<Int>()
        val api = Proxy.newProxyInstance(MvbarApi::class.java.classLoader, arrayOf(MvbarApi::class.java)) { _, method, args ->
            check(method.name == "getFavorites")
            val limit = args[0] as Int
            val offset = args[1] as Int
            offsets.add(offset)
            FavoritesResponse(ok = true, tracks = ids.drop(offset).take(limit).map { Track(id = it) })
        } as MvbarApi
        assertEquals(ids, api.getAllFavorites().tracks.map { it.id })
        assertEquals(listOf(0, 200, 400), offsets)
    }

    @Test fun movingToEndSendsAnExplicitNullAnchor() {
        assertEquals("{\"trackId\":42,\"beforeTrackId\":null}", Json.encodeToString(FavoriteMoveRequest(42, null)))
    }
}
