/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference

/**
 * Exhale as a media player, as far as Windows is concerned.
 *
 * Windows keeps a list of what's playing — the tile in the quick settings panel, the overlay the
 * volume keys show — and it decides where the keyboard's play, pause, next and previous keys go by
 * that same list. An app that never joins it is invisible to both, which is why the media keys did
 * nothing: they were going to whatever else had a session.
 *
 * Joining means talking to the Windows Runtime, and from a JVM that means COM by hand: every call
 * here is a jump through a vtable at a fixed offset, and an offset that's wrong doesn't throw — it
 * takes the whole process down. So each step is checked, nothing continues after a failure, and a
 * start that doesn't finish leaves a mark in settings that stops the next one from trying (see
 * [disabledAfterCrash]) rather than leaving Exhale unable to open.
 */
object WindowsMediaSession {
    // Windows.Media.SystemMediaTransportControls, and the interop that hands one to a desktop window.
    private const val CLASS_NAME = "Windows.Media.SystemMediaTransportControls"
    private const val IID_INTEROP = "DDB0472D-C911-4A1F-86D9-DC3D71A95F5A"
    private const val IID_SMTC = "99FA3FF4-1742-42A6-902E-087D41F965EC"
    private const val IID_SMTC_LOWER = "99fa3ff4-1742-42a6-902e-087d41f965ec"
    private const val IID_BUTTON_ARGS = "b7f47116-a56f-4dc8-9e11-92031f4a87c2"
    private const val IID_IUNKNOWN = "00000000-0000-0000-c000-000000000046"
    private const val IID_AGILE_OBJECT = "94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90"
    private const val E_NOINTERFACE = -2147467262 // 0x80004002

    // Vtable slots, counting the three of IUnknown and the three of IInspectable before the interface's own.
    private const val INTEROP_GET_FOR_WINDOW = 6
    private const val SMTC_PUT_PLAYBACK_STATUS = 7
    private const val SMTC_GET_DISPLAY_UPDATER = 8
    private const val SMTC_PUT_IS_ENABLED = 11
    private const val SMTC_PUT_IS_PLAY_ENABLED = 13
    private const val SMTC_PUT_IS_PAUSE_ENABLED = 17
    private const val SMTC_PUT_IS_PREVIOUS_ENABLED = 25
    private const val SMTC_PUT_IS_NEXT_ENABLED = 27
    private const val SMTC_ADD_BUTTON_PRESSED = 32
    private const val UPDATER_PUT_TYPE = 7
    private const val UPDATER_GET_MUSIC_PROPERTIES = 12
    private const val UPDATER_UPDATE = 17
    private const val MUSIC_PUT_TITLE = 7
    private const val MUSIC_PUT_ARTIST = 11
    private const val ARGS_GET_BUTTON = 6

    private const val MEDIA_TYPE_MUSIC = 1
    private const val STATUS_PLAYING = 3
    private const val STATUS_PAUSED = 4
    private const val STATUS_STOPPED = 2

    private const val BUTTON_PLAY = 0
    private const val BUTTON_PAUSE = 1
    private const val BUTTON_STOP = 2
    private const val BUTTON_NEXT = 6
    private const val BUTTON_PREVIOUS = 7

    /** Set while a start is in progress; still set at the next start means that one didn't survive it. */
    private const val ATTEMPT_KEY = "windows_media_session_attempt"

    @Volatile private var smtc: Pointer? = null

    @Volatile private var updater: Pointer? = null

    @Volatile private var music: Pointer? = null

    /** The event handler is referenced only from native memory, so it's held here or it's collected. */
    @Suppress("unused")
    @Volatile private var handlerRefs: List<Any>? = null

    @Volatile private var handlerObject: Memory? = null

    val active: Boolean get() = smtc != null

    /** True when a previous start didn't come back, so this one leaves Windows' media list alone. */
    val disabledAfterCrash: Boolean get() = DesktopPrefs.raw(ATTEMPT_KEY) == "1"

    /**
     * Joins Windows' media list for [windowId], reporting presses to [onButton]. Returns false if any
     * step of it failed, in which case nothing was left behind.
     */
    @Synchronized
    fun start(windowId: Long, onButton: (MediaButton) -> Unit): Boolean {
        if (smtc != null) return true
        if (!isWindows) return false
        if (disabledAfterCrash) {
            println("Exhale media session: skipped — the last attempt didn't finish")
            return false
        }
        DesktopPrefs.putAll(mapOf(ATTEMPT_KEY to "1"))
        val ok = runCatching { connect(windowId, onButton) }.getOrElse {
            println("Exhale media session: ${it.message}")
            false
        }
        // Whether it worked or failed cleanly, the process is still here: let the next start try again.
        DesktopPrefs.putAll(mapOf(ATTEMPT_KEY to null))
        if (!ok) release()
        return ok
    }

    private fun connect(windowId: Long, onButton: (MediaButton) -> Unit): Boolean {
        val combase = Combase.INSTANCE
        // Apartment-threaded, and an already-initialised apartment is fine (S_FALSE / RPC_E_CHANGED_MODE).
        combase.RoInitialize(1)

        val name = hstring(combase, CLASS_NAME) ?: return false
        try {
            val factory = PointerByReference()
            val hr = combase.RoGetActivationFactory(name.value, guid(IID_INTEROP), factory)
            if (hr < 0 || factory.value == null) {
                println("Exhale media session: no interop factory (0x${hr.toUInt().toString(16)})")
                return false
            }
            val controls = PointerByReference()
            val forWindow = call(factory.value, INTEROP_GET_FOR_WINDOW, Pointer.createConstant(windowId), guid(IID_SMTC), controls)
            if (forWindow < 0 || controls.value == null) {
                println("Exhale media session: GetForWindow failed (0x${forWindow.toUInt().toString(16)})")
                return false
            }
            smtc = controls.value

            // What Exhale can be asked to do, and the buttons the panel shows.
            check("IsEnabled", call(smtc, SMTC_PUT_IS_ENABLED, 1))
            check("IsPlayEnabled", call(smtc, SMTC_PUT_IS_PLAY_ENABLED, 1))
            check("IsPauseEnabled", call(smtc, SMTC_PUT_IS_PAUSE_ENABLED, 1))
            check("IsPreviousEnabled", call(smtc, SMTC_PUT_IS_PREVIOUS_ENABLED, 1))
            check("IsNextEnabled", call(smtc, SMTC_PUT_IS_NEXT_ENABLED, 1))

            val updaterRef = PointerByReference()
            check("DisplayUpdater", call(smtc, SMTC_GET_DISPLAY_UPDATER, updaterRef))
            updater = updaterRef.value ?: return false
            check("Type", call(updater, UPDATER_PUT_TYPE, MEDIA_TYPE_MUSIC))
            val musicRef = PointerByReference()
            check("MusicProperties", call(updater, UPDATER_GET_MUSIC_PROPERTIES, musicRef))
            music = musicRef.value ?: return false

            subscribe(onButton)
            println("Exhale media session: joined Windows' media list")
            return true
        } finally {
            combase.WindowsDeleteString(name.value)
        }
    }

    /** The ButtonPressed handler: a COM object of four functions, built in memory by hand. */
    private fun subscribe(onButton: (MediaButton) -> Unit) {
        // The interfaces this handler really is. Claiming to be everything — including the marshalling
        // ones — is what made WinRT refuse it: COM asks what it can do with an object before using it.
        val handlerIid = typedEventHandlerIid()
        val accepted = setOf(IID_IUNKNOWN.uppercase(), IID_AGILE_OBJECT.uppercase(), handlerIid.uppercase())
        val queryInterface = object : QueryInterfaceCallback {
            override fun invoke(self: Pointer, riid: Pointer, ppv: Pointer): Int {
                val asked = guidText(riid)
                if (asked.uppercase() in accepted) {
                    ppv.setPointer(0, self)
                    return 0
                }
                // Anything else genuinely isn't this object; say so rather than handing back nonsense.
                ppv.setPointer(0, Pointer.NULL)
                println("Exhale media session: declined interface $asked")
                return E_NOINTERFACE
            }
        }
        val addRef = object : RefCallback {
            override fun invoke(self: Pointer): Int = 2
        }
        val release = object : RefCallback {
            override fun invoke(self: Pointer): Int = 1
        }
        val invoke = object : InvokeCallback {
            override fun invoke(self: Pointer, sender: Pointer, args: Pointer): Int {
                val button = Memory(4).use { out ->
                    if (call(args, ARGS_GET_BUTTON, out) < 0) return@use -1
                    out.getInt(0)
                }
                val pressed = when (button) {
                    BUTTON_PLAY -> MediaButton.Play
                    BUTTON_PAUSE -> MediaButton.Pause
                    BUTTON_STOP -> MediaButton.Pause
                    BUTTON_NEXT -> MediaButton.Next
                    BUTTON_PREVIOUS -> MediaButton.Previous
                    else -> return 0
                }
                runCatching { onButton(pressed) }
                return 0
            }
        }

        val vtable = Memory(4L * Native.POINTER_SIZE)
        vtable.setPointer(0L, CallbackReference.getFunctionPointer(queryInterface))
        vtable.setPointer(Native.POINTER_SIZE.toLong(), CallbackReference.getFunctionPointer(addRef))
        vtable.setPointer(2L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(release))
        vtable.setPointer(3L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(invoke))
        val instance = Memory(Native.POINTER_SIZE.toLong())
        instance.setPointer(0L, vtable)

        val token = Memory(8)
        val hr = call(smtc, SMTC_ADD_BUTTON_PRESSED, instance, token)
        if (hr < 0) {
            println("Exhale media session: couldn't listen for buttons (0x${hr.toUInt().toString(16)})")
        }
        // Native memory holds the only references to these; Java must keep them alive itself.
        handlerObject = instance
        handlerRefs = listOf(queryInterface, addRef, release, invoke, vtable, token)
    }

    /** What Windows shows in the panel, and what the volume overlay names. */
    @Synchronized
    fun nowPlaying(title: String, artist: String) {
        val updaterPtr = updater ?: return
        val musicPtr = music ?: return
        val combase = Combase.INSTANCE
        val titleString = hstring(combase, title) ?: return
        val artistString = hstring(combase, artist)
        try {
            call(musicPtr, MUSIC_PUT_TITLE, titleString.value)
            artistString?.let { call(musicPtr, MUSIC_PUT_ARTIST, it.value) }
            call(updaterPtr, UPDATER_UPDATE)
        } finally {
            combase.WindowsDeleteString(titleString.value)
            artistString?.let { combase.WindowsDeleteString(it.value) }
        }
    }

    @Synchronized
    fun setPlaying(playing: Boolean, stopped: Boolean = false) {
        val controls = smtc ?: return
        val status = when {
            stopped -> STATUS_STOPPED
            playing -> STATUS_PLAYING
            else -> STATUS_PAUSED
        }
        call(controls, SMTC_PUT_PLAYBACK_STATUS, status)
    }

    @Synchronized
    fun release() {
        smtc = null
        updater = null
        music = null
        handlerObject = null
        handlerRefs = null
    }

    private fun check(what: String, hr: Int) {
        if (hr < 0) println("Exhale media session: $what failed (0x${hr.toUInt().toString(16)})")
    }

    /** One vtable jump: the object's first field is its vtable, and [slot] is the function in it. */
    private fun call(target: Pointer?, slot: Int, vararg args: Any?): Int {
        val self = target ?: return -1
        val vtable = self.getPointer(0)
        val function = Function.getFunction(vtable.getPointer(slot.toLong() * Native.POINTER_SIZE))
        return function.invokeInt(arrayOf<Any?>(self, *args))
    }

    private class HString(val value: Pointer)

    private fun hstring(combase: Combase, text: String): HString? {
        val out = PointerByReference()
        val hr = combase.WindowsCreateString(WString(text), text.length, out)
        return if (hr >= 0 && out.value != null) HString(out.value) else null
    }

    /** The sixteen bytes at [pointer], written back out the way a GUID is normally printed. */
    private fun guidText(pointer: Pointer): String {
        val bytes = pointer.getByteArray(0, 16)
        fun hex(vararg indices: Int) = indices.joinToString("") { "%02x".format(bytes[it]) }
        return "${hex(3, 2, 1, 0)}-${hex(5, 4)}-${hex(7, 6)}-${hex(8, 9)}-${hex(10, 11, 12, 13, 14, 15)}"
    }

    /**
     * ITypedEventHandler<SystemMediaTransportControls, ButtonPressedEventArgs> has no fixed id: WinRT
     * derives one for each pair of types by hashing a signature string, so it's worked out the same way
     * here rather than written down.
     */
    private fun typedEventHandlerIid(): String {
        val signature = "pinterface({9de1c534-6ae1-11e0-84e1-18a905bcc53f};" +
            "rc(Windows.Media.SystemMediaTransportControls;{$IID_SMTC_LOWER});" +
            "rc(Windows.Media.SystemMediaTransportControlsButtonPressedEventArgs;{$IID_BUTTON_ARGS}))"
        val namespace = byteArrayOf(
            0x11, 0xf4.toByte(), 0x7a, 0xd5.toByte(), 0x7b, 0x73, 0x42, 0xc0.toByte(),
            0xab.toByte(), 0xae.toByte(), 0x87.toByte(), 0x8b.toByte(), 0x1e, 0x16, 0xad.toByte(), 0xee.toByte(),
        )
        val digest = java.security.MessageDigest.getInstance("SHA-1").apply {
            update(namespace)
            update(signature.toByteArray(Charsets.UTF_8))
        }.digest()
        val bytes = digest.copyOf(16)
        // Version 5 (name-based, SHA-1) and the standard variant bits, as RFC 4122 lays them out.
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x50).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        fun hex(range: IntRange) = range.joinToString("") { "%02x".format(bytes[it]) }
        return "${hex(0..3)}-${hex(4..5)}-${hex(6..7)}-${hex(8..9)}-${hex(10..15)}"
    }

    /** A GUID as its sixteen bytes: first three fields little-endian, the last eight as written. */
    private fun guid(text: String): Memory {
        val hex = text.replace("-", "")
        val memory = Memory(16)
        memory.setInt(0, hex.substring(0, 8).toLong(16).toInt())
        memory.setShort(4, hex.substring(8, 12).toInt(16).toShort())
        memory.setShort(6, hex.substring(12, 16).toInt(16).toShort())
        for (i in 0 until 8) {
            memory.setByte((8 + i).toLong(), hex.substring(16 + i * 2, 18 + i * 2).toInt(16).toByte())
        }
        return memory
    }

    private val isWindows: Boolean get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    enum class MediaButton { Play, Pause, Next, Previous }

    @Suppress("FunctionName")
    private interface Combase : Library {
        fun RoInitialize(type: Int): Int
        fun RoGetActivationFactory(className: Pointer, iid: Memory, factory: PointerByReference): Int
        fun WindowsCreateString(source: WString, length: Int, out: PointerByReference): Int
        fun WindowsDeleteString(string: Pointer): Int

        companion object {
            val INSTANCE: Combase by lazy { Native.load("combase", Combase::class.java) }
        }
    }

    private interface QueryInterfaceCallback : Callback {
        fun invoke(self: Pointer, riid: Pointer, ppv: Pointer): Int
    }

    private interface RefCallback : Callback {
        fun invoke(self: Pointer): Int
    }

    private interface InvokeCallback : Callback {
        fun invoke(self: Pointer, sender: Pointer, args: Pointer): Int
    }
}
