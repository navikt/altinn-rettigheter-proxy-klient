package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error

import java.lang.RuntimeException

class AltinnException(proxyResponseIError: ProxyResponseIError)
    : RuntimeException(proxyResponseIError.melding)