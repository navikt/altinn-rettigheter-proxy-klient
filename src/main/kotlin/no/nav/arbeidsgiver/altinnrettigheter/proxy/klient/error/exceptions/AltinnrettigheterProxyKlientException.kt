package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions

import java.lang.RuntimeException

class AltinnrettigheterProxyKlientException(melding: String, exception: Exception)
    : RuntimeException(melding, exception)