package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error

import java.lang.RuntimeException

class AltinnrettigheterProxyKlientUhåndtertException(melding: String, exception: Exception)
    : RuntimeException(melding, exception)