package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

data class AltinnrettigheterProxyKlientConfig
@JvmOverloads
constructor (
        val proxy: ProxyConfig,
        val altinn: AltinnConfig? = null,
)

data class AltinnConfig(
        val url: String,
        val altinnApiKey: String,
        val altinnApiGwApiKey: String
)

data class ProxyConfig(
        val consumerId: String?,
        val url: String
)