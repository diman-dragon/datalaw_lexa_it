package app.datalaw.lexa.core

/** Шардирование файлов словаря. FNV-1a обязан совпадать с fnv1a() в scripts/build_data.py. */
object Hash {
    const val SHARDS_E = 512
    const val SHARDS_R = 128
    const val SHARDS_S = 256

    /** FNV-1a, 32 бита, по UTF-8. Результат — беззнаковое значение в Long. */
    fun fnv1a(text: String): Long {
        var h = 0x811C9DC5L
        for (b in text.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xFF)
            h = (h * 0x01000193L) and 0xFFFFFFFFL
        }
        return h
    }

    fun shardOf(key: String, count: Int): Int = (fnv1a(key) % count).toInt()

    fun shardName(index: Int): String = index.toString().padStart(3, '0')

    /** Путь шарда внутри пакета словаря: e/016.json, r/…, s/… */
    fun shardPath(folder: String, key: String): String {
        val count = when (folder) {
            "e" -> SHARDS_E
            "r" -> SHARDS_R
            "s" -> SHARDS_S
            else -> throw IllegalArgumentException("unknown shard folder: $folder")
        }
        return "$folder/${shardName(shardOf(key, count))}.json"
    }
}
