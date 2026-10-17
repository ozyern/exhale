package com.ozyern.exhale.cupertino

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb

fun ImageBitmap.scale(width: Int, height: Int): ImageBitmap {
    return Bitmap.createScaledBitmap(this.asAndroidBitmap(), 5, 5, false)
        .copy(Bitmap.Config.ARGB_8888, false)
        .asImageBitmap()
}

fun ImageBitmap.crop(x: Int, y: Int, width: Int, height: Int): ImageBitmap {
    val endX = (x + width).coerceAtMost(this.width)
    val endY = (y + height).coerceAtMost(this.height)
    val cropWidth = (endX - x).coerceAtLeast(0)
    val cropHeight = (endY - y).coerceAtLeast(0)

    require(x >= 0 && y >= 0) {
        "x($x), y($y) should not be less than 0)"
    }

    val cropped = Bitmap.createBitmap(
        this.asAndroidBitmap(),
        x, y, cropWidth, cropHeight
    )

    return cropped.asImageBitmap()
}

fun ImageBitmap.averageLuminance(
    cropX: Int = 0,
    cropY: Int = 0,
    cropWidth: Int = width,
    cropHeight: Int = height,
    sampleWidth: Int = 5,
    sampleHeight: Int = sampleWidth,
    defaultColor: Color
): Double = innerAverageLuminance(
    cropX, cropY,
    cropWidth.coerceIn(0..width),
    cropHeight.coerceIn(0..height),
    sampleWidth,
    sampleHeight,
    defaultColor
)

private fun ImageBitmap.innerAverageLuminance(
    cropX: Int,
    cropY: Int,
    cropWidth: Int,
    cropHeight: Int,
    sampleWidth: Int = 5,
    sampleHeight: Int = sampleWidth,
    defaultColor: Color
): Double {
    val buffer = IntArray(sampleWidth * sampleHeight)
    val cropped = crop(cropX, cropY, cropWidth, cropHeight)
    val thumbnail = cropped.scale(sampleWidth, sampleHeight)
    thumbnail.readPixels(buffer)

    return buffer.sumOf { it.toLuminance(defaultColor) } / buffer.size
}

private fun Int.toLuminance(defaultColor: Color): Double {
    val color = this.takeIf { it != Color.Transparent.toArgb() } ?: defaultColor.toArgb()

    val r = ((color shr 16) and 0xFF) / 255f
    val g = ((color shr 8) and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

fun Int.toHexString(includeAlpha: Boolean = true): String {
    return if (includeAlpha) {
        "#${this.toUInt().toString(16).padStart(8, '0').uppercase()}"
    } else {
        "#${(this and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()}"
    }
}