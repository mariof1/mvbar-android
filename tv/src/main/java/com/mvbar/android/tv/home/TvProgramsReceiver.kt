package com.mvbar.android.tv.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mvbar.android.tv.data.TvRepository
import com.mvbar.android.tv.data.TvSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class TvProgramsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "android.media.tv.action.INITIALIZE_PROGRAMS") return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val store = TvSessionStore(context)
                val session = store.load() ?: return@launch
                val repo = TvRepository(session, store.clientId)
                val recommendations = async { runCatching { repo.recommendations() }.getOrDefault(emptyList()) }
                val recent = async { runCatching { repo.recentlyAdded() }.getOrDefault(emptyList()) }
                val episodes = async { runCatching { repo.newEpisodes() }.getOrDefault(emptyList()) }
                TvHomePublisher(context).publish(recommendations.await(), recent.await(), episodes.await())
            } finally {
                pending.finish()
            }
        }
    }
}
