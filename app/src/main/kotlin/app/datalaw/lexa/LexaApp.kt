package app.datalaw.lexa

import android.app.Application
import app.datalaw.lexa.data.Dictionary
import app.datalaw.lexa.data.FileStore
import app.datalaw.lexa.data.Pack
import app.datalaw.lexa.platform.Speaker
import app.datalaw.lexa.store.AppStore
import app.datalaw.lexa.data.Area
import app.datalaw.lexa.data.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Корень приложения: общие сервисы (без DI-фреймворка — приложение небольшое). */
class LexaApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var files: FileStore
        private set
    lateinit var dictionary: Dictionary
        private set
    lateinit var pack: Pack
        private set
    lateinit var store: AppStore
        private set
    lateinit var speaker: Speaker
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        files = FileStore(this)
        dictionary = Dictionary(files)
        pack = Pack(files)
        store = AppStore(this)
        speaker = Speaker(this)
        // кэш «на лету» не раздуваем: оставляем последние файлы
        appScope.launch { runCatching { files.trim(Area.CACHE, Config.CACHE_LIMIT) } }
    }

    companion object {
        lateinit var instance: LexaApp
            private set
    }
}
