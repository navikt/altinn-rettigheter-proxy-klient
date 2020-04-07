package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.ProxyError
import java.lang.RuntimeException

class AltinnrettigheterProxyException(proxyError: ProxyError)
    : RuntimeException(proxyError.melding)