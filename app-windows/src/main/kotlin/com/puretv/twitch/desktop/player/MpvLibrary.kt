package com.puretv.twitch.desktop.player

import com.sun.jna.*

/**
 * Minimal JNA binding for libmpv (the libmpv-2 client API). We bind only the
 * handful of `mpv_*` entry points MpvPlayer needs. Properties are read back as
 * strings via [mpv_get_property_string] on each PROPERTY_CHANGE event, which
 * avoids decoding the `mpv_event_property` union — simpler and robust enough
 * for the values we observe (time-pos, duration, pause, etc.).
 */
interface MpvLibrary : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    // Returns a HEAP-allocated C string (or null). The caller MUST copy it via
    // Pointer.getString(0) and then release it with mpv_free() — JNA does not
    // free it automatically, so binding it as String here would leak on every
    // PROPERTY_CHANGE. Hence Pointer?, paired with mpv_free below.
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int   // args MUST be null-terminated
    fun mpv_observe_property(ctx: Pointer, reply_userdata: Long, name: String, format: Int): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer  // returns mpv_event*
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_error_string(error: Int): String
}

// mpv_event { int event_id; int error; uint64_t reply_userdata; void* data; }
@Structure.FieldOrder("event_id", "error", "reply_userdata", "data")
class MpvEvent : Structure() {
    @JvmField var event_id: Int = 0
    @JvmField var error: Int = 0
    @JvmField var reply_userdata: Long = 0
    @JvmField var data: Pointer? = null
}

object MpvConst {
    const val MPV_EVENT_NONE = 0
    const val MPV_EVENT_SHUTDOWN = 1
    const val MPV_EVENT_FILE_LOADED = 8
    const val MPV_EVENT_END_FILE = 7
    const val MPV_EVENT_PROPERTY_CHANGE = 22
    const val MPV_FORMAT_NONE = 0
    const val MPV_FORMAT_STRING = 1
    const val MPV_FORMAT_FLAG = 3
    const val MPV_FORMAT_INT64 = 4
    const val MPV_FORMAT_DOUBLE = 5
}
