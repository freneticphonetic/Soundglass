package com.example.audio

/** Each worker may close only its own resource. Closing twice is harmless. */
class OwnedResource<T>(val value: T, private val closeAction: (T) -> Unit) {
    @Volatile var isClosed = false
        private set
    @Synchronized fun close() {
        if (isClosed) return
        isClosed = true
        closeAction(value)
    }
}
