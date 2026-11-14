package com.ozyern.artwork

class ArtworkSourceRegistry<T> {
    data class Source<T>(val owner: Any, val id: String, val url: String?, val image: T?, val title: String?)

    private val sources = LinkedHashMap<Any, Source<T>>()
    private val titles = HashMap<Any, String>()
    private val pendingImages = HashMap<Any, Pair<String?, T>>()
    private var touchedOwner: Any? = null
    var selected: Source<T>? = null
        private set

    fun register(owner: Any, id: String, url: String?) {
        val previous = sources[owner]
        val pending = pendingImages.remove(owner)?.takeIf { it.first == url }?.second
        val image = pending ?: previous?.image.takeIf { previous?.id == id && previous.url == url }
        sources[owner] = Source(owner, id, url, image, titles[owner])
    }

    fun setTitle(owner: Any, title: String) {
        titles[owner] = title
        sources[owner]?.let { sources[owner] = it.copy(title = title) }
    }

    fun updateImage(owner: Any, url: String?, image: T) {
        val source = sources[owner]
        if (source == null) {
            pendingImages[owner] = url to image
            return
        }
        if (source.url != url) return
        sources[owner] = source.copy(image = image)
    }

    fun touch(owner: Any) {
        touchedOwner = owner
    }

    fun select(id: String, returning: Boolean = false, preferredOwner: Any? = null): Source<T>? {
        if (returning && selected?.id == id) {
            val activeSelection = selected?.let { sources[it.owner] }
            if (activeSelection != null) {
                selected = activeSelection
                return activeSelection
            }
        }
        val touched = sources[touchedOwner]?.takeIf { it.id == id }
        val preferred = preferredOwner?.let(sources::get)?.takeIf { it.id == id }
        selected = touched ?: preferred ?: sources.values.lastOrNull { it.id == id }
        touchedOwner = null
        return selected
    }

    fun isRegistered(owner: Any?): Boolean = owner != null && sources.containsKey(owner)

    fun source(id: String): Source<T>? = selected?.takeIf { it.id == id }
        ?: sources.values.lastOrNull { it.id == id }

    fun unregister(owner: Any) {
        sources.remove(owner)
        titles.remove(owner)
        pendingImages.remove(owner)
        if (touchedOwner == owner) touchedOwner = null
    }
}
