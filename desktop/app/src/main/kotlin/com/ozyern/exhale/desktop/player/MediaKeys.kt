/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import kotlin.concurrent.thread

/**
 * The keyboard's play, pause, next and previous keys, wherever you are.
 *
 * Compose only sees a key while the window has focus, which is why the media keys did nothing with
 * Exhale in the background: Windows hands them to whichever app owns the current media session, and
 * that was the browser. Registering them as system hotkeys claims them for Exhale instead.
 *
 * Each key is claimed by name rather than by watching the keyboard, so nothing else anyone types is
 * ever seen here. If another player already holds a key, Windows refuses that one and it keeps
 * working as it did — see [active].
 */
object MediaKeys {
    private const val WM_HOTKEY = 0x0312
    private const val WM_QUIT = 0x0012

    private const val VK_MEDIA_NEXT_TRACK = 0xB0
    private const val VK_MEDIA_PREV_TRACK = 0xB1
    private const val VK_MEDIA_STOP = 0xB2
    private const val VK_MEDIA_PLAY_PAUSE = 0xB3

    /** Hotkey ids, ours to choose; they come back in the message that says a key was pressed. */
    private val keys = listOf(1 to VK_MEDIA_PLAY_PAUSE, 2 to VK_MEDIA_NEXT_TRACK, 3 to VK_MEDIA_PREV_TRACK, 4 to VK_MEDIA_STOP)

    @Volatile private var threadId: Int = 0

    @Volatile private var running = false

    /** Which keys Windows actually granted, for Settings to be honest about. */
    @Volatile var claimed: Int = 0
        private set

    val active: Boolean get() = running && claimed > 0

    @Synchronized
    fun start(player: DesktopPlayer) {
        if (running || !isWindows) return
        running = true
        thread(name = "exhale-media-keys", isDaemon = true) {
            val user32 = runCatching { user32() }.getOrNull() ?: run { running = false; return@thread }
            val kernel32 = runCatching { kernel32() }.getOrNull() ?: run { running = false; return@thread }
            threadId = kernel32.GetCurrentThreadId()
            val granted = keys.filter { (id, vk) -> user32.RegisterHotKey(null, id, 0, vk) }
            claimed = granted.size
            // Whether Windows gave us the keys at all is the first thing to know when they do nothing.
            println("Exhale media keys: claimed ${granted.size} of ${keys.size} (${granted.joinToString { "0x%02X".format(it.second) }})")
            try {
                val message = Msg()
                // GetMessage blocks this thread until a claimed key is pressed, or until stop() posts a quit.
                while (running && user32.GetMessageW(message, null, 0, 0) > 0) {
                    if (message.message != WM_HOTKEY) continue
                    println("Exhale media keys: hotkey ${Pointer.nativeValue(message.wParam ?: Pointer.NULL).toInt()}")
                    when (Pointer.nativeValue(message.wParam ?: Pointer.NULL).toInt()) {
                        1 -> player.togglePlayPause()
                        2 -> player.next()
                        3 -> player.previous()
                        4 -> player.togglePlayPause()
                    }
                }
            } finally {
                keys.forEach { (id, _) -> runCatching { user32.UnregisterHotKey(null, id) } }
                claimed = 0
                running = false
            }
        }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        val id = threadId
        if (id != 0) runCatching { user32().PostThreadMessageW(id, WM_QUIT, Pointer.NULL, Pointer.NULL) }
    }

    fun apply(player: DesktopPlayer, enabled: Boolean) {
        if (enabled) start(player) else stop()
    }

    private val isWindows: Boolean get() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private fun user32(): User32Lib = Native.load("user32", User32Lib::class.java)

    private fun kernel32(): Kernel32Lib = Native.load("kernel32", Kernel32Lib::class.java)

    @Suppress("FunctionName")
    private interface User32Lib : Library {
        fun RegisterHotKey(hWnd: Pointer?, id: Int, fsModifiers: Int, vk: Int): Boolean
        fun UnregisterHotKey(hWnd: Pointer?, id: Int): Boolean
        fun GetMessageW(lpMsg: Msg, hWnd: Pointer?, filterMin: Int, filterMax: Int): Int
        fun PostThreadMessageW(threadId: Int, message: Int, wParam: Pointer?, lParam: Pointer?): Boolean
    }

    @Suppress("FunctionName")
    private interface Kernel32Lib : Library {
        fun GetCurrentThreadId(): Int
    }

    /** Win32's MSG, as the message loop fills it in. */
    @Structure.FieldOrder("hwnd", "message", "wParam", "lParam", "time", "x", "y")
    class Msg : Structure() {
        @JvmField var hwnd: Pointer? = null

        @JvmField var message: Int = 0

        @JvmField var wParam: Pointer? = null

        @JvmField var lParam: Pointer? = null

        @JvmField var time: Int = 0

        @JvmField var x: Int = 0

        @JvmField var y: Int = 0
    }
}
