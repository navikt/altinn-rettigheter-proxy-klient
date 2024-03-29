package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.ProxyError
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientFallbackException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.*
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.ResourceUtils
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.getCorrelationId
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.withCorrelationId
import org.slf4j.LoggerFactory

class AltinnrettigheterProxyKlient(
    private val config: AltinnrettigheterProxyKlientConfig,
) {

    private val httpClient: HttpClient = HttpClient(Apache) {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson()
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, request ->
                if (request.url.toString().startsWith(config.altinn?.url!!)) {
                    // feil fra altinn, trenger ikke parses
                    return@handleResponseExceptionWithRequest
                }

                val responseException = exception as? ResponseException ?: return@handleResponseExceptionWithRequest
                val response = responseException.response

                val proxyError = when (response.contentType()) {
                    ContentType.Application.Json -> ProxyError.parse(response.bodyAsText(), response.status)
                    else -> ProxyError(
                        httpStatus = response.status.value,
                        melding = responseException.message ?: response.status.toString(),
                        cause = "ukjent feil"
                    )
                }

                if (responseException is ClientRequestException && response.status.value != 404) {
                    throw AltinnException(proxyError)
                } else {
                    throw AltinnrettigheterProxyException(proxyError)
                }
            }
        }
    }
    private val klientVersjon: String = ResourceUtils.getKlientVersjon()


    /**
     * Hent alle organisasjoner i Altinn en bruker har rettigheter i.
     *  @param selvbetjeningToken - Selvbetjening token til innlogget bruker
     *  @param subject - Fødselsnummer til innlogget bruker (fallback funksjon)
     *  @param filtrerPåAktiveOrganisasjoner - Aktiver filtering på både Status og Type
     *
     *  @return en liste av alle organisasjoner
     *   - med Status: 'Active' og Type: 'Enterprise' | 'Business', når filtrerPåAktiveOrganisasjoner er 'true'
     *   - med Status: 'Active' | 'Inactive' og Type: 'Enterprise' | 'Business' | 'Person', når filtrerPåAktiveOrganisasjoner er 'false'
     */
    fun hentOrganisasjoner(
        selvbetjeningToken: Token,
        subject: Subject,
        filtrerPåAktiveOrganisasjoner: Boolean
    ): List<AltinnReportee> {
        return hentOrganisasjonerMedEllerUtenRettigheter(
            selvbetjeningToken,
            subject,
            null,
            null,
            filtrerPåAktiveOrganisasjoner
        )
    }

    /**
     * Hent alle organisasjoner i Altinn en bruker har enkel rettighet i
     *  @param selvbetjeningToken - Selvbetjening token til innlogget bruker
     *  @param subject - Fødselsnummer til innlogget bruker (fallback funksjon)
     *  @param serviceCode - Kode for rettigheter brukeren har for en organisasjon (henger sammen med ServiceEdition)
     *  @param serviceEdition
     *  @param filtrerPåAktiveOrganisasjoner - Aktiver filtering på både Status og Type
     *
     *  @return en liste av alle organisasjoner
     *   - med Status: 'Active' og Type: 'Enterprise' | 'Business', når filtrerPåAktiveOrganisasjoner er 'true'
     *   - med Status: 'Active' | 'Inactive' og Type: 'Enterprise' | 'Business' | 'Person', når filtrerPåAktiveOrganisasjoner er 'false'
     */
    fun hentOrganisasjoner(
        selvbetjeningToken: Token,
        subject: Subject,
        serviceCode: ServiceCode,
        serviceEdition: ServiceEdition,
        filtrerPåAktiveOrganisasjoner: Boolean
    ): List<AltinnReportee> {
        return hentOrganisasjonerMedEllerUtenRettigheter(
            selvbetjeningToken,
            subject,
            serviceCode,
            serviceEdition,
            filtrerPåAktiveOrganisasjoner
        )
    }


    private fun hentOrganisasjonerMedEllerUtenRettigheter(
        selvbetjeningToken: Token,
        subject: Subject,
        serviceCode: ServiceCode?,
        serviceEdition: ServiceEdition?,
        filtrerPåAktiveOrganisasjoner: Boolean
    ): List<AltinnReportee> = withCorrelationId {
        val organisasjoner: ArrayList<AltinnReportee> = ArrayList()
        var detFinnesFlereOrganisasjoner = true

        val filterValue = if (filtrerPåAktiveOrganisasjoner) QUERY_PARAM_FILTER_AKTIVE_BEDRIFTER else null

        while (detFinnesFlereOrganisasjoner) {
            val nyeOrganisasjoner = hentOrganisasjonerMedFallbackFunksjonalitet(
                selvbetjeningToken,
                subject,
                serviceCode,
                serviceEdition,
                DEFAULT_PAGE_SIZE,
                organisasjoner.size,
                filterValue
            )

            if (nyeOrganisasjoner.size > DEFAULT_PAGE_SIZE) {
                logger.error(
                    "Altinn returnerer flere organisasjoner ({}) enn det vi spurte om ({}). Dette medfører at brukeren ikke får tilgang til alle bedriftene sine",
                    nyeOrganisasjoner.size, DEFAULT_PAGE_SIZE
                )
            }

            if (nyeOrganisasjoner.size != DEFAULT_PAGE_SIZE) {
                detFinnesFlereOrganisasjoner = false
            }

            organisasjoner.addAll(nyeOrganisasjoner)
        }

        return@withCorrelationId organisasjoner
    }

    private fun hentOrganisasjonerMedFallbackFunksjonalitet(
        selvbetjeningToken: Token,
        subject: Subject,
        serviceCode: ServiceCode?,
        serviceEdition: ServiceEdition?,
        top: Number,
        skip: Number,
        filter: String?
    ): List<AltinnReportee> {
        return try {
            hentOrganisasjonerViaAltinnrettigheterProxy(
                selvbetjeningToken,
                serviceCode,
                serviceEdition,
                top,
                skip,
                filter
            )
        } catch (proxyException: AltinnrettigheterProxyException) {
            if (config.altinn != null) {
                logger.info(
                    "Fikk en feil i altinn-rettigheter-proxy med melding '{}'. Gjør et nytt forsøk ved å kalle Altinn direkte.",
                    proxyException.message
                )
                hentOrganisasjonerIAltinn(config.altinn, subject, serviceCode, serviceEdition, top, skip, filter)
            } else {
                throw AltinnrettigheterProxyKlientException("Feil i altinn-rettigheter-proxy", proxyException)
            }
        } catch (altinnException: AltinnException) {
            logger.info(
                "Fikk exception i Altinn med følgende melding '{}'. Exception fra Altinn håndteres av klient applikasjon",
                altinnException.message
            )
            throw altinnException
        } catch (exception: Exception) {
            logger.info(
                "Fikk exception med følgende melding '{}'. Denne skal håndteres av klient applikasjon",
                exception.message
            )
            throw AltinnrettigheterProxyKlientException("Exception ved kall til proxy", exception)
        }
    }

    private fun hentOrganisasjonerViaAltinnrettigheterProxy(
        selvbetjeningToken: Token,
        serviceCode: ServiceCode?,
        serviceEdition: ServiceEdition?,
        top: Number,
        skip: Number,
        filter: String?
    ): List<AltinnReportee> {

        val parametreTilProxy = mutableListOf<Pair<String, String>>().apply {
            add("top" to top.toString())
            add("skip" to skip.toString())
            if (serviceCode != null) add("serviceCode" to serviceCode.value)
            if (serviceEdition != null) add("serviceEdition" to serviceEdition.value)
            if (filter != null) add("filter" to filter)
        }


        val altinnProxyUrl = getAltinnrettigheterProxyURL(config.proxy.url, PROXY_ENDEPUNKT_API_ORGANISASJONER) +
                "?" + parametreTilProxy.formUrlEncode()

        return runBlocking {
            try {
                httpClient.get(altinnProxyUrl) {
                    headers {
                        append("Authorization", "Bearer ${selvbetjeningToken.value}")
                        append(PROXY_KLIENT_VERSJON_HEADER_NAME, klientVersjon)
                        append(CORRELATION_ID_HEADER_NAME, getCorrelationId())
                        if (config.proxy.consumerId != null) {
                            append(CONSUMER_ID_HEADER_NAME, config.proxy.consumerId)
                        }
                    }
                }.body()
            } catch (e: Exception) {
                throw AltinnrettigheterProxyException(
                    ProxyError(
                        0, e.message ?: "ingen message", ""
                    )
                )
            }
        }
    }

    private fun hentOrganisasjonerIAltinn(
        altinnConfig: AltinnConfig,
        subject: Subject,
        serviceCode: ServiceCode?,
        serviceEdition: ServiceEdition?,
        top: Number,
        skip: Number,
        filter: String?
    ): List<AltinnReportee> {
        val parametreTilAltinn = mutableListOf<Pair<String, String>>().apply {
            add("ForceEIAuthentication" to "")
            add("\$top" to top.toString())
            add("\$skip" to skip.toString())
            add("subject" to subject.value)
            if (serviceCode != null) add("serviceCode" to serviceCode.value)
            if (serviceEdition != null) add("serviceEdition" to serviceEdition.value)
            if (filter != null) add("\$filter" to filter)
        }

        val altinnUrl = getAltinnURL(altinnConfig.url) + "?" + parametreTilAltinn.formUrlEncode()

        return runBlocking {
            try {
                httpClient.get(altinnUrl) {
                    headers {
                        append(CORRELATION_ID_HEADER_NAME, getCorrelationId())
                        append("X-NAV-APIKEY", altinnConfig.altinnApiGwApiKey)
                        append("APIKEY", altinnConfig.altinnApiKey)
                    }
                }.body()
            } catch (e: ResponseException) {
                if (e.manglerAltinnProfil()) {
                    listOf()
                } else {
                    throw AltinnrettigheterProxyKlientFallbackException(
                        "Fallback kall mot Altinn feiler med HTTP feil ${e.response.status.value} '${e.response.status.description}'", e
                    )
                }
            } catch (e: Exception) {
                throw AltinnrettigheterProxyKlientFallbackException(
                    "Fallback kall mot Altinn feiler med exception: '${e.message}' ", e
                )
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)

        const val DEFAULT_PAGE_SIZE = 500
        const val QUERY_PARAM_FILTER_AKTIVE_BEDRIFTER = "Type ne 'Person' and Status eq 'Active'"

        const val PROXY_KLIENT_VERSJON_HEADER_NAME = "X-Proxyklient-Versjon"
        const val CORRELATION_ID_HEADER_NAME = "X-Correlation-ID"
        const val CONSUMER_ID_HEADER_NAME = "X-Consumer-ID"

        const val PROXY_ENDEPUNKT_API_ORGANISASJONER = "/v2/organisasjoner"

        fun getAltinnrettigheterProxyURL(basePath: String, endepunkt: String) =
            basePath.removeSuffix("/") + endepunkt

        fun getAltinnURL(basePath: String) =
            basePath.removeSuffix("/") + "/ekstern/altinn/api/serviceowner/reportees"
    }

    private fun ResponseException.manglerAltinnProfil() =
        response.status.value == 400 && response.status.description.contains("User profile")
}

