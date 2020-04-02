package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error

import java.lang.RuntimeException

class AltinnrettigheterProxyException(proxyResponseIError: ProxyResponseIError)
    : RuntimeException(proxyResponseIError.melding)