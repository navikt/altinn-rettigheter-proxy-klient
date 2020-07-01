package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import com.github.kittinunf.fuel.core.Headers.Companion.ACCEPT
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.isClientError
import com.github.kittinunf.fuel.core.isServerError
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.*
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.*
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.*
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.CorrelationIdUtils
import org.slf4j.LoggerFactory

class AltinnrettigheterProxyKlient(
        private val config: AltinnrettigheterProxyKlientConfig
) {

    fun hentOrganisasjoner(
            selvbetjeningToken: SelvbetjeningToken,
            subject: Subject,
            serviceCode: ServiceCode,
            serviceEdition: ServiceEdition
    ): List<AltinnReportee> {
        return hentOrganisasjonerViaProxy(
                selvbetjeningToken,
                subject,
                mapOf(
                "serviceCode" to serviceCode.value,
                "serviceEdition" to serviceEdition.value
                ),
                PROXY_ENDEPUNKT_API_ORGANISASJONER
        )
    }

    fun hentOrganisasjoner(
            selvbetjeningToken: SelvbetjeningToken,
            subject: Subject,
            queryParametre: Map<String, String>
            ): List<AltinnReportee> {

        return hentOrganisasjonerViaProxy(
                selvbetjeningToken,
                subject,
                queryParametre,
                PROXY_ENDEPUNKT_GENERISK)
    }

    private fun hentOrganisasjonerViaProxy(
            selvbetjeningToken: SelvbetjeningToken,
            subject: Subject,
            queryParametre: Map<String, String>,
            endepunkt: String
    ): List<AltinnReportee> {

        return try {
            hentOrganisasjonerViaAltinnrettigheterProxy(selvbetjeningToken, queryParametre, endepunkt)
        } catch (proxyException: AltinnrettigheterProxyException) {
            logger.warn("Fikk en feil i altinn-rettigheter-proxy med melding '${proxyException.message}'. " +
                    "Gjør et nytt forsøk ved å kalle Altinn direkte.")
            hentOrganisasjonerIAltinn(subject, queryParametre)
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
            queryParametre: Map<String, String>,
            endepunktProxy: String
    ): List<AltinnReportee> {

        val parametreTilProxy = queryParametre.toMutableMap()

        if ((!parametreTilProxy.containsKey("ForceEIAuthentication")) && PROXY_ENDEPUNKT_GENERISK == endepunktProxy)
            parametreTilProxy["ForceEIAuthentication"] = ""

        val (_, response, result) = with(
                getAltinnrettigheterProxyURL(config.proxy.url, endepunktProxy).httpGet(parametreTilProxy.toList())
        ) {
            authentication().bearer(selvbetjeningToken.value)
            headers[CORRELATION_ID_HEADER_NAME] = CorrelationIdUtils.getCorrelationId()
            headers[CONSUMER_ID_HEADER_NAME] = config.proxy.consumerId
            headers[ACCEPT] = "application/json"

            responseObject<List<AltinnReportee>>()
        }
        when (result) {
            is Result.Failure -> {
                if (!response.isClientError && !response.isServerError) {
                    throw AltinnrettigheterProxyException(
                            ProxyErrorUtenResponse(
                                    "Feil ved bruk av Altinnrettigheter proxy pga " +
                                            "'${result.getException().message}'",
                                    ProxyError.Kilde.ALTINN_RETTIGHETER_PROXY)
                    )
                }

                val proxyErrorMedResponseBody = ProxyErrorMedResponseBody.parse(
                        response.body().toStream(),
                        response.statusCode
                )
                logger.info("Mottok en feil fra kilde '${proxyErrorMedResponseBody.kilde}' " +
                        "med status '${proxyErrorMedResponseBody.httpStatus}' " +
                        "og melding '${proxyErrorMedResponseBody.melding}'")

                if (proxyErrorMedResponseBody.kilde == ProxyError.Kilde.ALTINN) {
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
            queryParametre: Map<String, String>
    ): List<AltinnReportee> {
        val parametreTilAltinn: MutableMap<String, String> = queryParametre.toMutableMap();

        if (!parametreTilAltinn.containsKey("ForceEIAuthentication"))
            parametreTilAltinn["ForceEIAuthentication"] = ""

        if (parametreTilAltinn.containsKey("\$filter")) {
            parametreTilAltinn["\$filter"] = (parametreTilAltinn["\$filter"] ?: "").replace("+", " ")
        }

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
        const val CORRELATION_ID_HEADER_NAME = "X-Correlation-ID"
        const val CONSUMER_ID_HEADER_NAME = "X-Consumer-ID"

        const val PROXY_ENDEPUNKT_API_ORGANISASJONER = "/organisasjoner"
        const val PROXY_ENDEPUNKT_GENERISK = "/ekstern/altinn/api/serviceowner/reportees"

        fun getAltinnrettigheterProxyURL(basePath: String, endepunkt: String) =
                basePath.removeSuffix("/") + endepunkt

        fun getAltinnURL(basePath: String) =
                basePath.removeSuffix("/") + "/ekstern/altinn/api/serviceowner/reportees"
    }
}

