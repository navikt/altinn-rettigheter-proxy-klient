package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.ProxyError
import java.lang.RuntimeException

@Suppress("CanBeParameter")
class AltinnException(val proxyError: ProxyError)
    : RuntimeException(proxyError.melding)