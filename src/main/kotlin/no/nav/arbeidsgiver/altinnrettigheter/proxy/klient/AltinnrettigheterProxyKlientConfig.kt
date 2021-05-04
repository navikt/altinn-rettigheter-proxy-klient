package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

data class AltinnrettigheterProxyKlientConfig(
        val proxy: ProxyConfig,
        val altinn: AltinnConfig
)

/** Configuration for fallback functionaltiy. */
data class AltinnConfig(
        val url: String,
        val altinnApiKey: String,
        val altinnApiGwApiKey: String
)

/** Configuration for altinn-rettigheter-proxy. */
data class ProxyConfig(
        val consumerId: String,
        val url: String
)