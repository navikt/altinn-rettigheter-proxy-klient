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
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.CorrelationIdUtils
import no.nav.security.oidc.context.TokenContext
import org.slf4j.LoggerFactory

class AltinnrettigheterProxyKlient(
        private val config: AltinnrettigheterProxyKlientConfig
) {

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
            logger.warn("Fikk en feil i altinn-rettigheter-proxy med melding '${proxyException.message}'. " +
                    "Gjør et nytt forsøk ved å kalle Altinn direkte.")
            hentOrganisasjonerIAltinn(subject, serviceCode, serviceEdition)
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
            tokenContext: TokenContext,
            serviceCode: ServiceCode,
            serviceEdition: ServiceEdition
    ): List<AltinnReportee> {
        val parameters = listOf(
                "serviceCode" to serviceCode.value,
                "serviceEdition" to serviceEdition.value
        )

        val (_, response, result) = with(
                getAltinnrettigheterProxyURL(config.proxy.url).httpGet(parameters)
        ) {
            authentication().bearer(tokenContext.idToken)
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
            serviceCode: ServiceCode,
            serviceEdition: ServiceEdition
    ): List<AltinnReportee> {
        val parameters = listOf(
                "ForceEIAuthentication" to "",
                "subject" to subject.value,
                "serviceCode" to serviceCode.value,
                "serviceEdition" to serviceEdition.value
        )

        val (_, response, result) = with(
                getAltinnURL(config.altinn.url).httpGet(parameters)
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
        const val ISSUER_SELVBETJENING = "selvbetjening"
        const val CORRELATION_ID_HEADER_NAME = "X-Correlation-ID"
        const val CONSUMER_ID_HEADER_NAME = "X-Consumer-ID"

        fun getAltinnrettigheterProxyURL(basePath: String) = basePath.removeSuffix("/") + "/organisasjoner"
        fun getAltinnURL(basePath: String) = basePath.removeSuffix("/") + "/ekstern/altinn/api/serviceowner/reportees"
    }
}

