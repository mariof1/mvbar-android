package com.mvbar.android.data.local.dao

import androidx.room.*
import com.mvbar.android.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowseDao {
    // Artists
    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE ASC")
    fun allArtistsFlow(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE ASC LIMIT :limit OFFSET :offset")
    suspend fun getArtists(limit: Int, offset: Int): List<ArtistEntity>

    @Query("SELECT COUNT(*) FROM artists")
    suspend fun artistCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtists(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists")
    suspend fun deleteAllArtists()

    @Transaction
    suspend fun replaceAllArtists(artists: List<ArtistEntity>) {
        deleteAllArtists()
        insertArtists(artists)
    }

    // Albums
    @Query("SELECT * FROM albums ORDER BY displayName COLLATE NOCASE ASC")
    fun allAlbumsFlow(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums ORDER BY displayName COLLATE NOCASE ASC LIMIT :limit OFFSET :offset")
    suspend fun getAlbums(limit: Int, offset: Int): List<AlbumEntity>

    @Query("SELECT COUNT(*) FROM albums")
    suspend fun albumCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums")
    suspend fun deleteAllAlbums()

    @Transaction
    suspend fun replaceAllAlbums(albums: List<AlbumEntity>) {
        deleteAllAlbums()
        insertAlbums(albums)
    }

    // Genres
    @Query("SELECT * FROM genres ORDER BY name COLLATE NOCASE ASC")
    fun allGenresFlow(): Flow<List<GenreEntity>>

    @Query("SELECT * FROM genres ORDER BY name COLLATE NOCASE ASC LIMIT :limit OFFSET :offset")
    suspend fun getGenres(limit: Int, offset: Int): List<GenreEntity>

    @Query("SELECT COUNT(*) FROM genres")
    suspend fun genreCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenres(genres: List<GenreEntity>)

    @Query("DELETE FROM genres")
    suspend fun deleteAllGenres()

    @Transaction
    suspend fun replaceAllGenres(genres: List<GenreEntity>) {
        deleteAllGenres()
        insertGenres(genres)
    }

    // Countries
    @Query("SELECT * FROM countries ORDER BY name COLLATE NOCASE ASC")
    fun allCountriesFlow(): Flow<List<CountryEntity>>

    @Query("SELECT * FROM countries ORDER BY name COLLATE NOCASE ASC LIMIT :limit OFFSET :offset")
    suspend fun getCountries(limit: Int, offset: Int): List<CountryEntity>

    @Query("SELECT COUNT(*) FROM countries")
    suspend fun countryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCountries(countries: List<CountryEntity>)

    @Query("DELETE FROM countries")
    suspend fun deleteAllCountries()

    @Transaction
    suspend fun replaceAllCountries(countries: List<CountryEntity>) {
        deleteAllCountries()
        insertCountries(countries)
    }

    // Languages
    @Query("SELECT * FROM languages ORDER BY name COLLATE NOCASE ASC")
    fun allLanguagesFlow(): Flow<List<LanguageEntity>>

    @Query("SELECT * FROM languages ORDER BY name COLLATE NOCASE ASC LIMIT :limit OFFSET :offset")
    suspend fun getLanguages(limit: Int, offset: Int): List<LanguageEntity>

    @Query("SELECT COUNT(*) FROM languages")
    suspend fun languageCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLanguages(languages: List<LanguageEntity>)

    @Query("DELETE FROM languages")
    suspend fun deleteAllLanguages()

    @Transaction
    suspend fun replaceAllLanguages(languages: List<LanguageEntity>) {
        deleteAllLanguages()
        insertLanguages(languages)
    }
}
