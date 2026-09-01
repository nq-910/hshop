package io.github.cia3ds.jni

interface NativeSeedFetcherCallback {
    fun onFetch(titleId: String): ByteArray?
}
