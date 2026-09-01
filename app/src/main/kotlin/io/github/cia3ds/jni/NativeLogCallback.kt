package io.github.cia3ds.jni

interface NativeLogCallback {
    fun onLine(line: String)
}
