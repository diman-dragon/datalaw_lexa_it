package app.datalaw.lexa.data


/** Скачанный на устройство словарь («пакет»). Признак готовности — manifest.json в области PACK. */
data class PackInfo(val installed: Boolean, val version: String? = null, val files: Int = 0, val bytes: Long = 0)

class Pack(
    private val files: FileStore,
    private val dataUrl: String = Config.DATA_URL,
) {
    private companion object {
        const val DONE = "manifest.json"
        const val STARTED = ".version"
    }

    suspend fun info(): PackInfo {
        val raw = files.get(Area.PACK, DONE) ?: return PackInfo(false)
        val m = Manifest.parse(raw) ?: return PackInfo(false) // повреждён — считаем, что не установлен
        return PackInfo(true, m.version, m.files.size, m.total)
    }

    /** Актуальный манифест на сервере (без кэша). */
    suspend fun fetchManifest(): Manifest {
        val text = Remote.fetch(dataUrl, DONE, bust = true) ?: throw DataUnavailableException("manifest.json not found")
        return Manifest.parse(text) ?: throw DataUnavailableException("bad manifest")
    }

    suspend fun install(onProgress: (Progress) -> Unit): Manifest {
        val manifest = fetchManifest()

        // Новая версия словаря — начинаем с чистого листа; та же версия — докачиваем недостающее
        if (files.get(Area.PACK, STARTED) != manifest.version) {
            files.clear(Area.PACK)
            files.put(Area.PACK, STARTED, manifest.version)
        } else {
            files.delete(Area.PACK, DONE)
        }

        runDownload(
            manifest,
            object : DownloadIO {
                override suspend fun fetchText(path: String): String =
                    Remote.fetch(dataUrl, path) ?: throw DataUnavailableException("missing $path")

                override suspend fun has(path: String) = files.has(Area.PACK, path)
                override suspend fun put(path: String, text: String) = files.put(Area.PACK, path, text)
            },
            onProgress,
        )

        files.put(Area.PACK, DONE, rawManifest(manifest))
        return manifest
    }

    suspend fun remove() = files.clear(Area.PACK)

    suspend fun cacheCount(): Int = files.count(Area.CACHE)

    suspend fun clearCache() = files.clear(Area.CACHE)

    private fun rawManifest(m: Manifest): String {
        val sb = StringBuilder()
        sb.append("{\"version\":").append(quote(m.version)).append(",\"total\":").append(m.total).append(",\"files\":[")
        m.files.forEachIndexed { i, (p, s) ->
            if (i > 0) sb.append(',')
            sb.append('[').append(quote(p)).append(',').append(s).append(']')
        }
        return sb.append("]}").toString()
    }

    private fun quote(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

