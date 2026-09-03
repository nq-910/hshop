package me.erista.hshop.thor.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.util.LruCache
import me.erista.hshop.model.GameTdbMetadata
import me.erista.hshop.scraper.ArtworkResolver
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class GameTdbRepository(private val context: Context) {

    private val cache = LruCache<String, GameTdbMetadata>(256)
    private var db: SQLiteDatabase? = null

    init {
        initDatabase()
    }

    @Synchronized
    private fun initDatabase() {
        if (db != null && db?.isOpen == true) return
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.parentFile?.exists()!!) {
                dbFile.parentFile?.mkdirs()
            }

            // Check if we need to copy or overwrite from assets
            var needsCopy = !dbFile.exists() || dbFile.length() == 0L
            if (!needsCopy) {
                // If asset size differs from destination file, overwrite
                val assetSize = getAssetSize(DB_NAME)
                if (assetSize > 0 && assetSize != dbFile.length()) {
                    needsCopy = true
                }
            }

            if (needsCopy) {
                copyDatabaseFromAssets(dbFile)
            }

            if (dbFile.exists() && dbFile.length() > 0) {
                db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
                Log.i(TAG, "GameTDB database opened successfully from ${dbFile.path}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GameTDB database", e)
        }
    }

    private fun getAssetSize(assetName: String): Long {
        return try {
            context.assets.openFd(assetName).use { it.length }
        } catch (_: Exception) {
            // openFd might fail for compressed assets, fallback to reading stream size
            try {
                context.assets.open(assetName).use { it.available().toLong() }
            } catch (_: Exception) {
                -1L
            }
        }
    }

    private fun copyDatabaseFromAssets(destFile: File) {
        try {
            context.assets.open(DB_NAME).use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var length: Int
                    while (input.read(buffer).also { length = it } > 0) {
                        output.write(buffer, 0, length)
                    }
                    output.flush()
                }
            }
            Log.i(TAG, "GameTDB database extracted to ${destFile.path} (${destFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying GameTDB asset", e)
        }
    }

    fun findMetadataByProductCode(productCode: String, fallbackTitle: String? = null): GameTdbMetadata? {
        Log.i(TAG, "findMetadataByProductCode: productCode='$productCode', fallbackTitle='$fallbackTitle'")
        val gameId = ArtworkResolver.extractGameId(productCode)
        Log.i(TAG, "extracted gameId='$gameId'")
        if (gameId != null) {
            val meta = findMetadataByGameId(gameId)
            Log.i(TAG, "lookup by gameId '$gameId': ${if (meta != null) "FOUND (${meta.title}, syn len=${meta.synopsis.length})" else "NOT FOUND"}")
            if (meta != null) return meta
        }
        if (!fallbackTitle.isNullOrBlank()) {
            val meta = findMetadataByTitle(fallbackTitle)
            Log.i(TAG, "lookup by title '$fallbackTitle': ${if (meta != null) "FOUND (${meta.title})" else "NOT FOUND"}")
            return meta
        }
        return null
    }

    fun findMetadataByGameId(gameId: String): GameTdbMetadata? {
        val cleanId = gameId.trim().uppercase()
        if (cleanId.length != 4) {
            Log.w(TAG, "findMetadataByGameId: invalid length for '$gameId'")
            return null
        }

        synchronized(cache) {
            val cached = cache.get(cleanId)
            if (cached != null) return cached
        }

        val database = db ?: run {
            initDatabase()
            db ?: run {
                Log.e(TAG, "findMetadataByGameId: database is null!")
                return null
            }
        }

        return try {
            database.rawQuery(
                "SELECT id, name, title, synopsis, developer, publisher, release_date, genre, rating_type, rating_val, rating_desc, players, wifi_features, languages, region FROM games WHERE id = ? LIMIT 1",
                arrayOf(cleanId)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val meta = GameTdbMetadata(
                        gameId = cursor.getString(0) ?: cleanId,
                        name = cursor.getString(1) ?: "",
                        title = cursor.getString(2) ?: "",
                        synopsis = cursor.getString(3) ?: "",
                        developer = cursor.getString(4) ?: "",
                        publisher = cursor.getString(5) ?: "",
                        releaseDate = cursor.getString(6) ?: "",
                        genre = cursor.getString(7) ?: "",
                        ratingType = cursor.getString(8) ?: "",
                        ratingValue = cursor.getString(9) ?: "",
                        ratingDescriptors = cursor.getString(10) ?: "",
                        players = cursor.getString(11) ?: "",
                        wifiFeatures = cursor.getString(12) ?: "",
                        languages = cursor.getString(13) ?: "",
                        region = cursor.getString(14) ?: ""
                    )
                    synchronized(cache) {
                        cache.put(cleanId, meta)
                    }
                    meta
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying GameTDB for $cleanId", e)
            null
        }
    }

    fun findMetadataByTitle(title: String): GameTdbMetadata? {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return null

        synchronized(cache) {
            val cached = cache.get("title:$cleanTitle")
            if (cached != null) return cached
        }

        val database = db ?: run {
            initDatabase()
            db ?: return null
        }

        return try {
            var result: GameTdbMetadata? = database.rawQuery(
                "SELECT id, name, title, synopsis, developer, publisher, release_date, genre, rating_type, rating_val, rating_desc, players, wifi_features, languages, region FROM games WHERE title = ? LIMIT 1",
                arrayOf(cleanTitle)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    GameTdbMetadata(
                        gameId = cursor.getString(0) ?: "",
                        name = cursor.getString(1) ?: "",
                        title = cursor.getString(2) ?: "",
                        synopsis = cursor.getString(3) ?: "",
                        developer = cursor.getString(4) ?: "",
                        publisher = cursor.getString(5) ?: "",
                        releaseDate = cursor.getString(6) ?: "",
                        genre = cursor.getString(7) ?: "",
                        ratingType = cursor.getString(8) ?: "",
                        ratingValue = cursor.getString(9) ?: "",
                        ratingDescriptors = cursor.getString(10) ?: "",
                        players = cursor.getString(11) ?: "",
                        wifiFeatures = cursor.getString(12) ?: "",
                        languages = cursor.getString(13) ?: "",
                        region = cursor.getString(14) ?: ""
                    )
                } else null
            }

            if (result == null) {
                result = database.rawQuery(
                    "SELECT id, name, title, synopsis, developer, publisher, release_date, genre, rating_type, rating_val, rating_desc, players, wifi_features, languages, region FROM games WHERE name LIKE ? LIMIT 1",
                    arrayOf("$cleanTitle%")
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        GameTdbMetadata(
                            gameId = cursor.getString(0) ?: "",
                            name = cursor.getString(1) ?: "",
                            title = cursor.getString(2) ?: "",
                            synopsis = cursor.getString(3) ?: "",
                            developer = cursor.getString(4) ?: "",
                            publisher = cursor.getString(5) ?: "",
                            releaseDate = cursor.getString(6) ?: "",
                            genre = cursor.getString(7) ?: "",
                            ratingType = cursor.getString(8) ?: "",
                            ratingValue = cursor.getString(9) ?: "",
                            ratingDescriptors = cursor.getString(10) ?: "",
                            players = cursor.getString(11) ?: "",
                            wifiFeatures = cursor.getString(12) ?: "",
                            languages = cursor.getString(13) ?: "",
                            region = cursor.getString(14) ?: ""
                        )
                    } else null
                }
            }

            if (result != null) {
                synchronized(cache) {
                    cache.put("title:$cleanTitle", result)
                    if (result.gameId.isNotEmpty()) {
                        cache.put(result.gameId, result)
                    }
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error querying GameTDB for title $cleanTitle", e)
            null
        }
    }

    companion object {
        private const val TAG = "GameTdbRepository"
        private const val DB_NAME = "gametdb.db"
    }
}
