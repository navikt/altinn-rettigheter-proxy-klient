package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import no.nav.security.oidc.context.OIDCRequestContextHolder
import org.springframework.boot.web.client.RestTemplateBuilder

data class AltinnrettigheterProxyKlientContext(
        val oidcRequestContextHolder: OIDCRequestContextHolder,
        val restTemplateBuilder: RestTemplateBuilder
)