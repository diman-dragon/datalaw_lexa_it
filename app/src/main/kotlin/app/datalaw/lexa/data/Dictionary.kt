package app.datalaw.lexa.data

import app.datalaw.lexa.core.Entry
import app.datalaw.lexa.core.EntryParse
import app.datalaw.lexa.core.Hash
import app.datalaw.lexa.core.Meta
import app.datalaw.lexa.core.SentencePair
import app.datalaw.lexa.core.WordIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** То, что нужно потребителям от словаря (и что подменяется в тестах). */
interface DictionarySource {
    suspend fun getEntry(key: String): Entry?
    suspend fun getReverse(key: String): List<String>
    suspend fun getSentences(key: String): List<SentencePair>
    suspend fun itIndex(): WordIndex
}

/**
 * Доступ к словарю: скачанный пакет → кэш → сервер.
 * Недоступность данных (нет сети и нет пакета) — DataUnavailableException; «слова нет» — null/пустой результат.
 */
class Dictionary(
    private val files: FileStore,
    private val dataUrl: String = Config.DATA_URL,
) : DictionarySource {
    /** Читает файл словаря: пакет → кэш → сеть (с записью в кэш). null — такого файла нет. */
    suspend fun readFile(path: String): String? {
        files.get(Area.PACK, path)?.let { return it }
        files.get(Area.CACHE, path)?.let { return it }
        val text = Remote.fetch(dataUrl, path) ?: return null
        runCatching { files.put(Area.CACHE, path, text) }
        return text
    }

    private suspend fun json(path: String): JsonObject? {
        val text = readFile(path) ?: return null
        return withContext(Dispatchers.Default) {
            runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
        }
    }

    /* Шарды держим в памяти (последние 64 файла). */
    private val shardCache = object : LinkedHashMap<String, JsonObject>(80, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JsonObject>?): Boolean = size > SHARD_LIMIT
    }
    private val shardLock = Mutex()

    private suspend fun shard(folder: String, key: String): JsonObject {
        val path = Hash.shardPath(folder, key)
        shardLock.withLock { shardCache[path] }?.let { return it }
        val loaded = json(path) ?: JsonObject(emptyMap())
        shardLock.withLock { shardCache[path] = loaded }
        return loaded
    }

    suspend fun loadMeta(): Meta? = json("meta.json")?.let(EntryParse::meta)

    /** Статья по ключу (нормализованное итальянское слово). */
    override suspend fun getEntry(key: String): Entry? =
        (shard("e", key)[key] as? JsonObject)?.let(EntryParse::entry)

    /** Итальянские слова для русского термина (ключ — normalizeRu). */
    override suspend fun getReverse(key: String): List<String> = EntryParse.reverse(shard("r", key)[key])

    /** Ошибку не глотаем: пустой результат был бы принят за «примеров нет». */
    override suspend fun getSentences(key: String): List<SentencePair> = EntryParse.sentences(shard("s", key)[key])

    private val indexLock = Mutex()
    private var itIx: WordIndex? = null
    private var ruIx: WordIndex? = null

    private suspend fun index(file: String): WordIndex {
        val text = readFile(file) ?: return WordIndex.EMPTY
        return withContext(Dispatchers.Default) { WordIndex.parse(text) }
    }

    override suspend fun itIndex(): WordIndex = indexLock.withLock { itIx ?: index("words.txt").also { itIx = it } }

    suspend fun ruIndex(): WordIndex = indexLock.withLock { ruIx ?: index("rwords.txt").also { ruIx = it } }

    /** После установки/удаления пакета индексы и шарды перечитываются. */
    suspend fun reset() {
        indexLock.withLock {
            itIx = null
            ruIx = null
        }
        shardLock.withLock { shardCache.clear() }
    }

    private companion object {
        const val SHARD_LIMIT = 64
    }
}
