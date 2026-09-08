package com.mvbar.android.ui
import android.app.Instrumentation
import android.os.Bundle
import androidx.room.Room
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.local.entity.toEntity
import com.mvbar.android.data.model.Track
import com.mvbar.android.data.repository.MusicRepository
import kotlinx.coroutines.runBlocking

/** Exercises real Room cache queries without changing user data, network, or playback. */
internal fun Instrumentation.verifyOfflineUnknownAlbums(): Bundle = runBlocking {
 val database=Room.inMemoryDatabaseBuilder(targetContext,MvbarDatabase::class.java).build()
 try {
  val tracks=listOf(null,"","   ").mapIndexed { i,album -> Track(id=i+1,title="Song $i",artist="Night Lovell",album=album) } + Track(id=4,artist="Other",album=null)
  database.trackDao().insertAll(tracks.map { it.toEntity() })
  val repo=MusicRepository(database)
  val name="Unknown Album — Night Lovell"
  check(repo.getCachedAlbumCount()==2)
  check(repo.getCachedAlbums(50,0,"U")!!.any { it.displayName==name&&it.trackCount==3 })
  check(repo.getCachedAlbums(50,0,"N")!!.isEmpty())
  check(repo.getCachedAlbumTracks(name)!!.map { it.id }.toSet()==setOf(1,2,3))
  check(repo.getCachedAlbumTracks("Unknown Album — Other")!!.single().id==4)
  check(repo.getCachedArtists(50,0)!!.first { it.name=="Night Lovell" }.albumCount==1)
  check(database.trackDao().getById(1)!!.album==null)
  Bundle().apply { putString("result","Offline Room: groups, counts, letter filters, track lookup, artist isolation and unchanged tags passed") }
 } finally { database.close() }
}
