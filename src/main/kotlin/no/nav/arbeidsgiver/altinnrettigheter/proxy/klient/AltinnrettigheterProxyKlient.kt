package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.*
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.CorrelationIdUtils
import no.nav.security.oidc.context.TokenContext
import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

class AltinnrettigheterProxyKlient(
        private val config: AltinnrettigheterProxyKlientConfig,
        restTemplateBuilder: RestTemplateBuilder
) {

    private val CORRELATION_ID_HEADER_NAME = "X-Correlation-ID"
    private val CONSUMER_ID_HEADER_NAME = "X-Consumer-ID"

    private val restTemplateBuilder = restTemplateBuilder

    fun hentOrganisasjoner(
            tokenContext: TokenContext,
            subject: Subject,
            serviceCode: ServiceCode,
            serviceEdition: ServiceEdition
    ): List<AltinnReportee> {

        if (tokenContext.issuer != ISSUER_SELVBETJENING) {
            AltinnrettigheterProxyKlientParameterSjekkException("Feil med token")
        }

        return try {
            hentOrganisasjonerViaAltinnrettigheterProxy(tokenContext, serviceCode, serviceEdition)
        } catch (proxyException: AltinnrettigheterProxyException) {
            logger.warn("Noe galt i proxy")
            hentOrganisasjonerIAltinn(subject, serviceCode, serviceEdition)
        } catch (altinnException: AltinnException) {
            logger.warn("Fikk exception i Altinn med følgende meldingen '${altinnException.message}'. " +
                    "Exceptions fra Altinn håndteres av klient applikasjon")
            throw altinnException
        } catch (exception: Exception) {
            throw AltinnrettigheterProxyKlientUhåndtertException("Uhåndtert exception ved kall til proxy", exception)
        }
    }


    private fun hentOrganisasjonerViaAltinnrettigheterProxy(
            tokenContext: TokenContext,
            serviceCode: ServiceCode,
            serviceEdition: ServiceEdition
    ): List<AltinnReportee> {
        val failsafeRestTemplate: RestTemplate =
                restTemplateBuilder.errorHandler(
                        RestTemplateProxyErrorHandler()
                )
                .build()

        val respons = failsafeRestTemplate.exchange(
                getAltinnRettigheterProxyURI(serviceCode, serviceEdition),
                HttpMethod.GET,
                getAuthHeadersForInnloggetBruker(tokenContext),
                object : ParameterizedTypeReference<List<AltinnReportee>>() {}
        )

        if (respons.statusCode != HttpStatus.OK) {
            val message = "Kall mot Altinn feiler med HTTP kode '${respons.statusCode}'"
            throw RuntimeException(message)
        }

        return respons.body!!
    }

    private fun hentOrganisasjonerIAltinn(
            subject: Subject,
            serviceCode: ServiceCode,
            serviceEdition: ServiceEdition
    ): List<AltinnReportee> {

        val restTemplate: RestTemplate = restTemplateBuilder.build()

        return try {
            val respons = restTemplate.exchange(
                    getAltinnURI(subject, serviceCode, serviceEdition),
                    HttpMethod.GET,
                    getHeaderEntityForAltinn(),
                    object : ParameterizedTypeReference<List<AltinnReportee>>() {}
            )

            if (respons.statusCode != HttpStatus.OK) {
                val message = "Kall mot Altinn feiler med HTTP kode '${respons.statusCode}'"
                throw RuntimeException(message)
            }
            respons.body!!
        } catch (exception: Exception) {
            throw AltinnrettigheterProxyKlientFallbackException(
                    "Feil ved fallback kall til Altinn",
                    exception
            )
        } // TODO catch HttpStatusCodeException, RestClientException (?) eller bare la Exceptions propageres
    }

    private fun getAltinnRettigheterProxyURI(serviceCode: ServiceCode, serviceEdition: ServiceEdition): URI {
        val uriBuilder = UriComponentsBuilder
                .fromUriString(config.proxy.url)
                .pathSegment("organisasjoner")
                .queryParam("ForceEIAuthentication")
                .queryParam("serviceCode", serviceCode.value)
                .queryParam("serviceEdition", serviceEdition.value)

        return uriBuilder.build().toUri()
    }

    private fun getAltinnURI(subject: Subject, serviceCode: ServiceCode, serviceEdition: ServiceEdition): URI {
        val uriBuilder = UriComponentsBuilder
                .fromUriString(config.altinn.url)
                .pathSegment(
                        "ekstern",
                        "altinn",
                        "api",
                        "serviceowner",
                        "reportees"
                )
                .queryParam("ForceEIAuthentication")
                .queryParam("serviceCode", serviceCode.value)
                .queryParam("serviceEdition", serviceEdition.value)
                .queryParam("subject", subject.value)
        return uriBuilder.build().toUri()
    }

    private fun getAuthHeadersForInnloggetBruker(tokenContext: TokenContext): HttpEntity<HttpHeaders> {
        val headers = HttpHeaders()
        headers.setBearerAuth(tokenContext.idToken)
        headers[CORRELATION_ID_HEADER_NAME] = CorrelationIdUtils.getCorrelationId()
        headers[CONSUMER_ID_HEADER_NAME] = config.proxy.consumerId
        headers[HttpHeaders.ACCEPT] = MediaType.APPLICATION_JSON.toString()
        return HttpEntity(headers)
    }

    private fun getHeaderEntityForAltinn(): HttpEntity<HttpHeaders> {
        val headers = HttpHeaders()
        headers["X-NAV-APIKEY"] = config.altinn.altinnApiGwApiKey
        headers["APIKEY"] = config.altinn.altinnApiKey
        headers[HttpHeaders.ACCEPT] = "${MediaType.APPLICATION_JSON.type}/${MediaType.APPLICATION_JSON.subtype}"
        return HttpEntity(headers)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RestTemplateProxyErrorHandler::class.java)
        const val ISSUER_SELVBETJENING = "selvbetjening"
    }
}

