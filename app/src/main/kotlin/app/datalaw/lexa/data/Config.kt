package app.datalaw.lexa.data

import app.datalaw.lexa.BuildConfig

object Config {
    /** Откуда читается словарь, пока он не скачан на устройство. Переопределяется при сборке: -PDATA_URL=… */
    val DATA_URL: String = BuildConfig.DATA_URL.let { if (it.endsWith("/")) it else "$it/" }
    const val CACHE_LIMIT = 500
}
