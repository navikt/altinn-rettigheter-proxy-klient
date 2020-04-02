package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.*
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnOrganisasjon
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Fnr
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.tilgangskontroll.TilgangskontrollUtils
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.CorrelationIdUtils
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

class AltinnrettigheterProxyKlient(
        private val config: AltinnrettigheterProxyKlientConfig,
        context: AltinnrettigheterProxyKlientContext
) {

    private val CORRELATION_ID_HEADER_NAME = "X-Correlation-ID"
    private val CONSUMER_ID_HEADER_NAME = "X-Consumer-ID"

    private val tilgangskontrollUtils = TilgangskontrollUtils(context.oidcRequestContextHolder)
    private val restTemplateBuilder = context.restTemplateBuilder

    fun hentOrganisasjoner(
            serviceCode: ServiceCode,
            serviceEdition: ServiceEdition
    ): List<AltinnOrganisasjon> {

        return try {
            hentOrganisasjonerViaAltinnrettigheterProxy(serviceCode, serviceEdition)
        } catch (proxyException: AltinnrettigheterProxyException) {
            val innloggetBruker = tilgangskontrollUtils.hentInnloggetBruker()
            hentOrganisasjonerIAltinn(innloggetBruker.fnr, serviceCode, serviceEdition)
        } catch (altinnException: AltinnException) {
            logger.warn("Fikk exception i Altinn med følgende meldingen '${altinnException.message}'. " +
                    "Exceptions fra Altinn håndteres av klient applikasjon")
            throw altinnException
        } catch (exception: Exception) {
            throw AltinnrettigheterProxyKlientException("Uhåndtert exception ved kall til proxy", exception)
        }
    }


    private fun hentOrganisasjonerViaAltinnrettigheterProxy(
            serviceCode: ServiceCode,
            serviceEdition: ServiceEdition
    ): List<AltinnOrganisasjon> {
        val failsafeRestTemplate: RestTemplate =
                restTemplateBuilder.errorHandler(
                        RestTemplateProxyErrorHandler()
                )
                .build()

        val respons = failsafeRestTemplate.exchange(
                getAltinnRettigheterProxyURI(serviceCode, serviceEdition),
                HttpMethod.GET,
                getAuthHeadersForInnloggetBruker(),
                object : ParameterizedTypeReference<List<AltinnOrganisasjon>>() {}
        )

        if (respons.statusCode != HttpStatus.OK) {
            val message = "Kall mot Altinn feiler med HTTP kode '${respons.statusCode}'"
            throw RuntimeException(message)
        }

        return respons.body!!
    }

    private fun hentOrganisasjonerIAltinn(
            fnr: Fnr,
            serviceCode: ServiceCode,
            serviceEdition: ServiceEdition
    ): List<AltinnOrganisasjon> {

        val restTemplate: RestTemplate = restTemplateBuilder.build()

        return try {
            val respons = restTemplate.exchange(
                    getAltinnURI(fnr, serviceCode, serviceEdition),
                    HttpMethod.GET,
                    getHeaderEntityForAltinn(),
                    object : ParameterizedTypeReference<List<AltinnOrganisasjon>>() {}
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
                .pathSegment()
                .pathSegment("organisasjoner")
                .queryParam("serviceCode", serviceCode.value)
                .queryParam("serviceEdition", serviceEdition.value)

        return uriBuilder.build().toUri()
    }

    private fun getAltinnURI(fnr: Fnr, serviceCode: ServiceCode, serviceEdition: ServiceEdition): URI {
        val uriBuilder = UriComponentsBuilder
                .fromUriString(config.altinn.url)
                .pathSegment()
                .pathSegment(
                        "ekstern",
                        "altinn",
                        "api",
                        "serviceowner",
                        "reportees"
                )
                .queryParam("serviceCode", serviceCode.value)
                .queryParam("serviceEdition", serviceEdition.value)
                .queryParam("subject", fnr.verdi)
        return uriBuilder.build().toUri()
    }

    private fun getAuthHeadersForInnloggetBruker(): HttpEntity<HttpHeaders> {
        val headers = HttpHeaders()
        headers.setBearerAuth(tilgangskontrollUtils.getSelvbetjeningToken())
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
    }
}

