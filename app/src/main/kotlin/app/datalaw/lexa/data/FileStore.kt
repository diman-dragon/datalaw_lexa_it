package app.datalaw.lexa.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Файловое хранилище словаря с двумя областями:
 *   PACK  — скачанный пользователем словарь (кнопка «Скачать для офлайна»); не вытесняется;
 *   CACHE — кэш того, что читалось «на лету» онлайн; можно очищать и подрезать.
 */
enum class Area { PACK, CACHE }

class FileStore(context: Context) {
    private val packRoot = File(context.filesDir, "dict/pack")
    private val cacheRoot = File(context.cacheDir, "dict")

    private fun root(area: Area): File = if (area == Area.PACK) packRoot else cacheRoot

    /** Пути приходят из манифеста с сервера — не даём выйти за пределы области. */
    private fun file(area: Area, path: String): File {
        require(path.isNotEmpty() && !path.startsWith("/") && !path.split('/').contains("..")) { "bad path: $path" }
        return File(root(area), path)
    }

    suspend fun get(area: Area, path: String): String? = withContext(Dispatchers.IO) {
        val f = file(area, path)
        if (f.isFile) runCatching { f.readText() }.getOrNull() else null
    }

    suspend fun has(area: Area, path: String): Boolean = withContext(Dispatchers.IO) { file(area, path).isFile }

    /** Запись через временный файл: оборванная загрузка не оставляет «половину» файла. */
    suspend fun put(area: Area, path: String, text: String) = withContext(Dispatchers.IO) {
        val f = file(area, path)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".part")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) {
            f.delete()
            if (!tmp.renameTo(f)) {
                tmp.delete()
                error("cannot write $path")
            }
        }
    }

    suspend fun delete(area: Area, path: String) {
        withContext(Dispatchers.IO) { file(area, path).delete() }
    }

    private fun files(area: Area): List<File> =
        root(area).walkTopDown().filter { it.isFile && !it.name.endsWith(".part") }.toList()

    suspend fun count(area: Area): Int = withContext(Dispatchers.IO) { files(area).size }

    suspend fun clear(area: Area) {
        withContext(Dispatchers.IO) { root(area).deleteRecursively() }
    }

    /** Оставляет в области не более max самых свежих файлов. */
    suspend fun trim(area: Area, max: Int) {
        withContext(Dispatchers.IO) {
            val all = files(area).sortedBy { it.lastModified() }
            for (f in all.take(maxOf(0, all.size - max))) f.delete()
        }
    }
}
