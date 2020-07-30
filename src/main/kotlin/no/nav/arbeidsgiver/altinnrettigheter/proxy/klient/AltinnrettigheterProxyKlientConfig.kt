package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

data class AltinnrettigheterProxyKlientConfig(
        val proxy: ProxyConfig,
        val altinn: AltinnConfig,
        val pageSize: Int = 500
)

data class AltinnConfig(
        val url: String,
        val altinnApiKey: String,
        val altinnApiGwApiKey: String
)

data class ProxyConfig(
        val consumerId: String,
        val url: String
)