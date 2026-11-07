package app.datalaw.lexa.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/** manifest.json: версия сборки, суммарный размер, список [путь, размер]. */
data class Manifest(val version: String, val total: Long, val files: List<Pair<String, Long>>) {
    companion object {
        fun parse(text: String): Manifest? = runCatching {
            val o = Json.parseToJsonElement(text) as JsonObject
            val version = (o["version"] as? JsonPrimitive)?.contentOrNull ?: return null
            val total = (o["total"] as? JsonPrimitive)?.longOrNull ?: return null
            val files = (o["files"] as? JsonArray)?.map { f ->
                val a = f as JsonArray
                val path = (a[0] as JsonPrimitive).content
                val size = (a[1] as JsonPrimitive).longOrNull ?: return null
                path to size
            } ?: return null
            Manifest(version, total, files)
        }.getOrNull()
    }
}

data class Progress(val files: Int, val totalFiles: Int, val bytes: Long, val totalBytes: Long)

interface DownloadIO {
    suspend fun fetchText(path: String): String
    suspend fun has(path: String): Boolean
    suspend fun put(path: String, text: String)
}

/**
 * Загрузка словаря «в офлайн»: по манифесту скачивает все файлы. Устойчиво к обрывам:
 * уже скачанные файлы пропускаются, ошибки повторяются. Возвращает число реально загруженных файлов.
 */
suspend fun runDownload(
    manifest: Manifest,
    io: DownloadIO,
    onProgress: (Progress) -> Unit,
    concurrency: Int = 6,
    retries: Int = 2,
    retryDelayMs: Long = 500,
): Int {
    val lock = Any()
    var doneFiles = 0
    var doneBytes = 0L
    val downloaded = AtomicInteger(0)
    val next = AtomicInteger(0)
    val total = manifest.files.size

    fun snapshot() = Progress(doneFiles, total, doneBytes, manifest.total)
    onProgress(snapshot())

    suspend fun one(path: String, size: Long) {
        if (!io.has(path)) {
            var last: Throwable? = null
            for (attempt in 0..retries) {
                coroutineContext.ensureActive()
                try {
                    io.put(path, io.fetchText(path))
                    downloaded.incrementAndGet()
                    last = null
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    last = e
                    if (attempt < retries) delay(retryDelayMs * (attempt + 1))
                }
            }
            if (last != null) throw last
        }
        val p = synchronized(lock) {
            doneFiles += 1
            doneBytes += size
            snapshot()
        }
        onProgress(p)
    }

    // сбой любого воркера отменяет остальных и пробрасывается наружу (структурная конкурентность)
    coroutineScope {
        repeat(maxOf(1, concurrency)) {
            launch(Dispatchers.IO) {
                while (true) {
                    val item = manifest.files.getOrNull(next.getAndIncrement()) ?: break
                    one(item.first, item.second)
                }
            }
        }
    }
    return downloaded.get()
}
