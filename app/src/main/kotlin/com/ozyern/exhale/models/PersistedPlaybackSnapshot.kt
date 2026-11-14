/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.models

import java.io.Serializable

data class PersistedPlaybackSnapshot(
    val queue: PersistQueue?,
    val automix: PersistQueue?,
    val playerState: PersistPlayerState?,
) : Serializable
