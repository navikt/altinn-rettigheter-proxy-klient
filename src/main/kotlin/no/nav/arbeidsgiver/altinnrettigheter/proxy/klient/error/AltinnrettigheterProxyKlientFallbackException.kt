package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error

import java.lang.RuntimeException

class AltinnrettigheterProxyKlientFallbackException(melding: String, exception: Exception)
    : RuntimeException(melding, exception)