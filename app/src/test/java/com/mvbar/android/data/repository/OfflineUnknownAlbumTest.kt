package com.mvbar.android.data.repository
import com.mvbar.android.data.model.Track
import com.mvbar.android.data.model.browseAlbumName
import org.junit.Assert.*
import org.junit.Test

class OfflineUnknownAlbumTest {
 @Test fun unknownAlbumsGroupByArtistAndCountOnce() {
  val tracks = listOf(null, "", "  ").mapIndexed { i, album -> Track(id=i, title="Song $i", artist="Night Lovell", album=album) } +
   Track(id=4,artist="Other",album=null) + Track(id=5,artist="Night Lovell",album="Real Album")
  val repo=MusicRepository()
  val albums=repo.derivedAlbumsFromTracks(tracks)
  assertEquals(3,albums.size)
  assertEquals(3,albums.first { it.displayName=="Unknown Album — Night Lovell" }.trackCount)
  assertEquals(2,repo.derivedArtistsFromTracks(tracks).first { it.name=="Night Lovell" }.albumCount)
  assertNull(tracks.first().album)
 }
 @Test fun labelsMatchServerAndPreferAlbumArtist() {
  assertEquals("Unknown Album — Main",Track(artist="Guest",albumArtist=" Main ").browseAlbumName)
  assertEquals("Unknown Album — Guest",Track(artist="Guest",albumArtist=" ").browseAlbumName)
  assertEquals("Unknown Album — Unknown Artist",Track().browseAlbumName)
  assertEquals("Album",Track(album=" Album ").browseAlbumName)
 }
}
