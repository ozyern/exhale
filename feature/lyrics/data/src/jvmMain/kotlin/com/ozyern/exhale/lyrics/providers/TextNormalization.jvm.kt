package com.ozyern.exhale.lyrics.providers

import java.text.Normalizer

internal actual fun String.decomposeCanonical(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
