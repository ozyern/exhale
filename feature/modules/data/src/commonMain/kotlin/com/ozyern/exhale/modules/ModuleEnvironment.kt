package com.ozyern.exhale.modules

/** Client-owned services. One instance is shared by settings, search, downloads and playback. */
class ModuleEnvironment(store: ModuleStore) {
    private val http = moduleHttpClient()
    val repository: ModuleRepository

    init {
        lateinit var client: ModuleClient
        val scriptTransport = ModuleTransport { url, method, headers, body ->
            client.request(url, method, headers, body)
        }
        client = ModuleClient(KtorModuleTransport(http), QuickJsModuleRuntime(scriptTransport), "Exhale/2.0")
        repository = ModuleRepository(client, store)
    }

    fun close() { http.close() }
}
