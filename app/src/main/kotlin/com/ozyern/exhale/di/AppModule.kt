/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.ozyern.exhale.constants.MaxSongCacheSizeKey
import com.ozyern.exhale.db.InternalDatabase
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import java.io.File
import java.util.NavigableSet
import java.util.TreeSet

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlayerCache

/** Animated covers (Apple motion artwork, canvases): kept so a looping clip is downloaded once. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CanvasVideoCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

private class LazyCache(
    private val create: () -> SimpleCache,
) : Cache {
    private val lock = Any()
    @Volatile private var cache: SimpleCache? = null

    private fun delegate(): SimpleCache =
        cache ?: synchronized(lock) { cache ?: create().also { cache = it } }

    override fun addListener(key: String, listener: Cache.Listener) =
        delegate().addListener(key, listener)

    override fun removeListener(key: String, listener: Cache.Listener) =
        delegate().removeListener(key, listener)

    override fun getCachedSpans(key: String): NavigableSet<CacheSpan> =
        delegate().getCachedSpans(key)

    override fun getKeys(): NavigableSet<String> =
        TreeSet(delegate().keys)

    override fun getCacheSpace(): Long =
        delegate().cacheSpace

    override fun getUid(): Long =
        delegate().uid

    override fun getCachedLength(key: String, position: Long, length: Long): Long =
        delegate().getCachedLength(key, position, length)

    override fun getCachedBytes(key: String, position: Long, length: Long): Long =
        delegate().getCachedBytes(key, position, length)

    override fun applyContentMetadataMutations(key: String, mutations: ContentMetadataMutations) =
        delegate().applyContentMetadataMutations(key, mutations)

    override fun getContentMetadata(key: String): ContentMetadata =
        delegate().getContentMetadata(key)

    override fun startReadWrite(key: String, position: Long, length: Long): CacheSpan =
        delegate().startReadWrite(key, position, length)

    override fun startReadWriteNonBlocking(key: String, position: Long, length: Long): CacheSpan? =
        delegate().startReadWriteNonBlocking(key, position, length)

    override fun startFile(key: String, position: Long, maxLength: Long): File =
        delegate().startFile(key, position, maxLength)

    override fun commitFile(file: File, length: Long) =
        delegate().commitFile(file, length)

    override fun releaseHoleSpan(holeSpan: CacheSpan) =
        delegate().releaseHoleSpan(holeSpan)

    override fun removeSpan(span: CacheSpan) =
        delegate().removeSpan(span)

    override fun removeResource(key: String) =
        delegate().removeResource(key)

    override fun isCached(key: String, position: Long, length: Long): Boolean =
        delegate().isCached(key, position, length)

    override fun release() =
        delegate().release()
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MusicDatabase = InternalDatabase.newInstance(context)

    @Singleton
    @Provides
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
    ): DatabaseProvider = StandaloneDatabaseProvider(context)

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache {
        val cacheSize = context.dataStore.get(MaxSongCacheSizeKey, 1024)
        val evictor = when (cacheSize) {
            -1 -> NoOpCacheEvictor()
            else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
        }
        return LazyCache {
            SimpleCache(
                context.filesDir.resolve("exoplayer"),
                evictor,
                databaseProvider,
            )
        }
    }

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache =
        LazyCache {
            SimpleCache(context.filesDir.resolve("download"), NoOpCacheEvictor(), databaseProvider)
        }

    /**
     * Animated covers loop forever, and with no cache every loop downloaded the clip again — at
     * Apple's top HEVC rendition, from every page showing one. That was gigabytes a day of the
     * same few seconds of video. In the cache directory, so the system may clear it when short.
     */
    @Singleton
    @Provides
    @CanvasVideoCache
    fun provideCanvasVideoCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): Cache =
        LazyCache {
            SimpleCache(
                context.cacheDir.resolve("canvas_video"),
                LeastRecentlyUsedCacheEvictor(CANVAS_VIDEO_CACHE_BYTES),
                databaseProvider,
            )
        }
}

/** Plenty for a few hundred clips at the capped rendition. */
private const val CANVAS_VIDEO_CACHE_BYTES = 256L * 1024 * 1024

/** Hands Compose code the animated-cover cache, which it cannot have injected. */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface CanvasVideoCacheEntryPoint {
    @CanvasVideoCache
    fun canvasVideoCache(): Cache
}
