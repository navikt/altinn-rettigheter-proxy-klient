package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.client

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientFallbackException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.service.FallbackService.Companion.CORRELATION_ID_HEADER_NAME
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.getCorrelationId
import org.slf4j.LoggerFactory

class FallbackClient(
    private val config: AltinnrettigheterProxyKlientConfig,
    private val httpClient: HttpClient,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)!!

    internal fun hentOrganisasjoner(
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
        fun getAltinnURL(basePath: String) =
            basePath.removeSuffix("/") + "/ekstern/altinn/api/serviceowner/reportees"
    }
}