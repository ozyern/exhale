package com.ozyern.storage.data

import androidx.media3.datasource.DataSource
import com.ozyern.storage.domain.*
import com.ozyern.storage.playback.NetworkDataSourceFactory
import kotlinx.coroutines.Dispatchers

class NetworkMusicRepository(
    private val store: NetworkServerStore,
    private val webDav: WebDavFileSource = WebDavFileSource(),
    private val smb: SmbFileSource = SmbFileSource(),
) : NetworkLibraryRepository by RemoteNetworkLibraryRepository(store, webDav, smb, Dispatchers.IO) {
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun dataSourceFactory(downstream: DataSource.Factory): DataSource.Factory = NetworkDataSourceFactory(downstream, store, webDav, smb)

}
