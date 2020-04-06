package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error

import java.lang.RuntimeException

class AltinnrettigheterProxyKlientParameterSjekkException(melding: String)
    : RuntimeException(melding)