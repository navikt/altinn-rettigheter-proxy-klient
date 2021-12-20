package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
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
        private val config: AltinnrettigheterProxyKlientConfig
) {

    private var klientVersjon: String = ResourceUtils.getKlientVersjon()

    private var httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
    }

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
            selvbetjeningToken: SelvbetjeningToken,
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
            selvbetjeningToken: SelvbetjeningToken,
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
            selvbetjeningToken: SelvbetjeningToken,
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
                logger.error("Altinn returnerer flere organisasjoner (${nyeOrganisasjoner.size}) " +
                        "enn det vi spurte om (${DEFAULT_PAGE_SIZE}). " +
                        "Dette medfører at brukeren ikke får tilgang til alle bedriftene sine")
            }

            if (nyeOrganisasjoner.size != DEFAULT_PAGE_SIZE) {
                detFinnesFlereOrganisasjoner = false
            }

            organisasjoner.addAll(nyeOrganisasjoner)
        }

        return@withCorrelationId organisasjoner
    }

    private fun hentOrganisasjonerMedFallbackFunksjonalitet(
            selvbetjeningToken: SelvbetjeningToken,
            subject: Subject,
            serviceCode: ServiceCode?,
            serviceEdition: ServiceEdition?,
            top: Number,
            skip: Number,
            filter: String?
    ): List<AltinnReportee> {
        return try {
            hentOrganisasjonerViaAltinnrettigheterProxy(selvbetjeningToken, serviceCode, serviceEdition, top, skip, filter)
        } catch (proxyException: AltinnrettigheterProxyException) {
            logger.warn("Fikk en feil i altinn-rettigheter-proxy med melding '${proxyException.message}'. " +
                    "Gjør et nytt forsøk ved å kalle Altinn direkte.")
            hentOrganisasjonerIAltinn(subject, serviceCode, serviceEdition, top, skip, filter)
        } catch (altinnException: AltinnException) {
            logger.warn("Fikk exception i Altinn med følgende melding '${altinnException.message}'. " +
                    "Exception fra Altinn håndteres av klient applikasjon")
            throw altinnException
        } catch (exception: Exception) {
            logger.warn("Fikk exception med følgende melding '${exception.message}'. " +
                    "Denne skal håndteres av klient applikasjon")
            throw AltinnrettigheterProxyKlientException("Exception ved kall til proxy", exception)
        }
    }

    private fun hentOrganisasjonerViaAltinnrettigheterProxy(
            selvbetjeningToken: SelvbetjeningToken,
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


        val url = getAltinnrettigheterProxyURL(config.proxy.url, PROXY_ENDEPUNKT_API_ORGANISASJONER) +
                "?" + parametreTilProxy.formUrlEncode()

        return runBlocking {
            try {
                httpClient.get(url) {
                    headers {
                        append("Authorization", "Bearer ${selvbetjeningToken.value}")
                        append(PROXY_KLIENT_VERSJON_HEADER_NAME, klientVersjon)
                        append(CORRELATION_ID_HEADER_NAME, getCorrelationId())
                        append(CONSUMER_ID_HEADER_NAME, config.proxy.consumerId)
                    }
                }
            } catch (e: ResponseException) {
                logger.info("mottok exception med content-type: ${e.response.contentType()}")
                val proxyError = when (e.response.contentType()) {
                    ContentType.Application.Json -> {
                        ProxyError.parse(
                            e.response.content.toInputStream(),
                            e.response.status.value
                        )
                    }
                    else -> {
                        ProxyError(
                            httpStatus = e.response.status.value,
                            melding = e.response.readText(),
                            cause = "ukjent feil"
                        )
                    }
                }

                logger.info("""
                    Mottok en feil med status '${proxyError.httpStatus}' 
                    og melding '${proxyError.melding}'
                    og årsak '${proxyError.cause}'
                    """.trimIndent()
                )

                if (e is ClientRequestException && e.response.status.value != 404) {
                    throw AltinnException(proxyError)
                } else {
                    throw AltinnrettigheterProxyException(proxyError)
                }
            } catch (e: Exception) {
                logger.info("feil i kall mot altinn-proxy", e)
                throw AltinnrettigheterProxyException(
                    ProxyError(
                        0, e.message ?: "ingen message", ""
                    )
                )
            }
        }
    }

    private fun hentOrganisasjonerIAltinn(
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

        val url = getAltinnURL(config.altinn.url) + "?" + parametreTilAltinn.formUrlEncode()

        return runBlocking {
            try {
                httpClient.get(url) {
                    headers {
                        append(CORRELATION_ID_HEADER_NAME, getCorrelationId())
                        append("X-NAV-APIKEY", config.altinn.altinnApiGwApiKey)
                        append("APIKEY", config.altinn.altinnApiKey)
                    }
                }
            } catch (e: ResponseException) {
                val melding = "Fallback kall mot Altinn feiler med HTTP feil " +
                        "${e.response.status.value} '${e.response.status.description}'"
                logger.warn(melding)
                throw AltinnrettigheterProxyKlientFallbackException(melding, e)
            } catch (e: Exception) {
                val melding = "Fallback kall mot Altinn feiler med exception: '${e.message}' "
                logger.warn(melding, e)
                throw AltinnrettigheterProxyKlientFallbackException(melding, e)
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
}

