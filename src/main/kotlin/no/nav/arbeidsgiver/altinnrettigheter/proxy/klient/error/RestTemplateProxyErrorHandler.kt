package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.ProxyResponseIError.Kilde.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.DefaultResponseErrorHandler
import java.io.IOException


class RestTemplateProxyErrorHandler : DefaultResponseErrorHandler() {

    /*
     Her håndteres 4xx eller 5xx response
     */

    @Throws(IOException::class)
    override fun handleError(response: ClientHttpResponse) {

        val proxyResponseIError = ProxyResponseIError.parse(response.body, response.statusCode)

        logger.warn("Mottok en feil fra kilde '${proxyResponseIError.kilde}' " +
                "med status '${proxyResponseIError.httpStatus}' " +
                "og melding '${proxyResponseIError.melding}'")

        if (proxyResponseIError.kilde == ALTINN) {
            throw AltinnException(proxyResponseIError)
        } else {
            throw AltinnrettigheterProxyException(proxyResponseIError)
        }
    }


    @Throws(IOException::class)
    override fun hasError(response: ClientHttpResponse): Boolean {
        // TODO ???
        val rawStatusCode = response.rawStatusCode
        val statusCode = HttpStatus.resolve(rawStatusCode)
        return if (statusCode != null) this.hasError(statusCode) else this.hasError(rawStatusCode)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RestTemplateProxyErrorHandler::class.java)
    }

}

/*
Disse skal trigge et fallback (med en WARN logg i klient biblioteket):
 - Proxy returnerer en intern feil / 5xx error (med origin i Proxy)
 - Proxy returnerer 401 Unauthorized, 403 Forbidden eller 404 Not Found (i.e. proxy URL er Not Found)

Disse skal IKKE trigge noe fallback kall (og da blir sendt direkte til klient applikasjonen):
 - Proxy returnerer en feil 4xx Client Error (med origin i Altinn)
 - Proxy returnerer en feil 5xx Server Error (med origin i Altinn) — obs: vi skal implementere en retry i selve altinn-rettigheter-proxy så det blir minst et forsøk til i tilfelle Altinn/API-GW returnerer 500 Internal Server Error, 502 Bad Gateway, 503 Service Unavailable eller 504 Gateway Timeout

 */