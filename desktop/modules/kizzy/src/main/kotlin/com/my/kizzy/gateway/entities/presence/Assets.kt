/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.my.kizzy.gateway.entities.presence

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Assets(
    @SerialName("large_image")
    val largeImage: String?,
    @SerialName("small_image")
    val smallImage: String?,
    @SerialName("large_text")
    val largeText: String? = null,
    @SerialName("small_text")
    val smallText: String? = null,
    /** Where the artwork goes when it's clicked. Discord ignores it on clients that don't support it. */
    @SerialName("large_url")
    val largeUrl: String? = null,
    /** The same for the badge in the artwork's corner. */
    @SerialName("small_url")
    val smallUrl: String? = null,
)