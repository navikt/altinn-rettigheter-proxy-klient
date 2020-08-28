package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import com.github.kittinunf.fuel.core.Headers.Companion.ACCEPT
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.isClientError
import com.github.kittinunf.fuel.core.isServerError
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.ProxyError
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.ProxyErrorMedResponseBody
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientFallbackException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.*
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.CorrelationIdUtils
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.ResourceUtils
import org.slf4j.LoggerFactory

class AltinnrettigheterProxyKlient(
        private val config: AltinnrettigheterProxyKlientConfig
) {

    private var klientVersjon: String = ResourceUtils.getKlientVersjon()

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
    ): List<AltinnReportee> {
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

        return organisasjoner
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

        val parametreTilProxy = mutableMapOf<String, String>()

        if (serviceCode != null) parametreTilProxy["serviceCode"] = serviceCode.value
        if (serviceEdition != null) parametreTilProxy["serviceEdition"] = serviceEdition.value
        if (filter != null) parametreTilProxy["filter"] = filter

        parametreTilProxy["top"] = top.toString()
        parametreTilProxy["skip"] = skip.toString()

        val (_, response, result) = with(
                getAltinnrettigheterProxyURL(config.proxy.url, PROXY_ENDEPUNKT_API_ORGANISASJONER)
                        .httpGet(parametreTilProxy.toList())
        ) {
            authentication().bearer(selvbetjeningToken.value)
            headers[PROXY_KLIENT_VERSJON_HEADER_NAME] = klientVersjon
            headers[CORRELATION_ID_HEADER_NAME] = CorrelationIdUtils.getCorrelationId()
            headers[CONSUMER_ID_HEADER_NAME] = config.proxy.consumerId
            headers[ACCEPT] = "application/json"

            responseObject<List<AltinnReportee>>()
        }
        when (result) {
            is Result.Failure -> {
                val proxyErrorMedResponseBody = ProxyErrorMedResponseBody.parse(
                        response.body().toStream(),
                        response.statusCode
                )


                logger.info("Mottok en feil fra kilde '${proxyErrorMedResponseBody.kilde}' " +
                        "med status '${proxyErrorMedResponseBody.httpStatus}' " +
                        "og melding '${proxyErrorMedResponseBody.melding}'")

                if ((response.isClientError && response.statusCode != 404) || proxyErrorMedResponseBody.kilde == ProxyError.Kilde.ALTINN) {
                    throw AltinnException(proxyErrorMedResponseBody)
                } else {
                    throw AltinnrettigheterProxyException(proxyErrorMedResponseBody)
                }


            }
            is Result.Success -> return result.get()
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
        val parametreTilAltinn = mutableMapOf<String, String>()

        parametreTilAltinn["ForceEIAuthentication"] = ""

        if (serviceCode != null) parametreTilAltinn["serviceCode"] = serviceCode.value
        if (serviceEdition != null) parametreTilAltinn["serviceEdition"] = serviceEdition.value
        if (filter != null) parametreTilAltinn["\$filter"] = filter

        parametreTilAltinn["\$top"] = top.toString()
        parametreTilAltinn["\$skip"] = skip.toString()
        parametreTilAltinn["subject"] = subject.value

        val (_, response, result) = with(
                getAltinnURL(config.altinn.url).httpGet(parametreTilAltinn.toList())
        ) {
            headers[CORRELATION_ID_HEADER_NAME] = CorrelationIdUtils.getCorrelationId()
            headers["X-NAV-APIKEY"] = config.altinn.altinnApiGwApiKey
            headers["APIKEY"] = config.altinn.altinnApiKey
            headers[ACCEPT] = "application/json"

            responseObject<List<AltinnReportee>>()
        }
        when (result) {
            is Result.Failure -> {
                var melding = "Fallback kall mot Altinn feiler. "

                melding += if (!response.isClientError && !response.isServerError) {
                    "Med melding '${result.getException().message}' "
                } else {
                    "Med HTTP kode '${response.statusCode}' " +
                            "og melding '${response.responseMessage}'"
                }

                logger.warn(melding)
                throw AltinnrettigheterProxyKlientFallbackException(melding, result.getException())
            }
            is Result.Success -> {
                logger.info("Fallback kall til Altinn gjennomført")
                return result.get()
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

