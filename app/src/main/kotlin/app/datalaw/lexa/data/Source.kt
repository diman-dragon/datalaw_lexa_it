package app.datalaw.lexa.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Нет сети или сервер не ответил — отличается от «такого файла нет». */
class DataUnavailableException(message: String = "Dictionary data unavailable") : IOException(message)

object Remote {
    /** Скачивает файл словаря. null — файла нет (404); исключение — нет связи. */
    suspend fun fetch(baseUrl: String, path: String, bust: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            val url = baseUrl + path + if (bust) "?t=${System.currentTimeMillis()}" else ""
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                }
                val code = conn.responseCode
                if (code == 404) return@withContext null
                if (code !in 200..299) throw DataUnavailableException("HTTP $code")
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                // страница-заглушка вместо файла — считаем, что файла нет
                if (text.startsWith("<")) null else text
            } catch (e: CancellationException) {
                throw e
            } catch (e: DataUnavailableException) {
                throw e
            } catch (e: IOException) {
                throw DataUnavailableException()
            } finally {
                conn?.disconnect()
            }
        }
}
