package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error

import java.lang.RuntimeException

class AltinnrettigheterProxyKlientException(melding: String, exception: Exception)
    : RuntimeException(melding, exception)