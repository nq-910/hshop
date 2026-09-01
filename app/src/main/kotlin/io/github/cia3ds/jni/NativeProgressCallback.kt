package io.github.cia3ds.jni

interface NativeProgressCallback {
    fun onProgress(progress: Int, message: String)
}
