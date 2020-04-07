package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error

abstract class ProxyError() {

    abstract val melding: String
    abstract val kilde: Kilde

    enum class Kilde(val verdi: String) {
        ALTINN("ALTINN"),
        ALTINN_RETTIGHETER_PROXY("ALTINN_RETTIGHETER_PROXY"),
        ALTINN_RETTIGHETER_PROXY_KLIENT("ALTINN_RETTIGHETER_PROXY_KLIENT")
    }
}

