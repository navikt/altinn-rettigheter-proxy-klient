package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.ProxyError
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.SelvbetjeningToken
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.service.FallbackService.Companion.CORRELATION_ID_HEADER_NAME
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.ResourceUtils
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.getCorrelationId
import org.slf4j.LoggerFactory

class ProxyClient(
    private val config: AltinnrettigheterProxyKlientConfig,
    private val httpClient: HttpClient,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)!!
    private val klientVersjon: String = ResourceUtils.getKlientVersjon()

    /*
     - internt: info nødvendig for å avgjøre om vi skal bruker fallback?
     - ekstern: altså, hva trenger vi å sende videre til kalleren av biblioteket?
     */
    internal fun hentOrganisasjoner(
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

        val url = getAltinnrettigheterProxyURL(
            config.proxy.url,
            PROXY_ENDEPUNKT_API_ORGANISASJONER
        ) + "?" + parametreTilProxy.formUrlEncode()

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
                /* Valid HTTP exchange, but HTTP status outside 2xx. */
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

    companion object {
        fun getAltinnrettigheterProxyURL(basePath: String, endepunkt: String) =
            basePath.removeSuffix("/") + endepunkt
        const val PROXY_KLIENT_VERSJON_HEADER_NAME = "X-Proxyklient-Versjon"
        const val CONSUMER_ID_HEADER_NAME = "X-Consumer-ID"
        const val PROXY_ENDEPUNKT_API_ORGANISASJONER = "/v2/organisasjoner"
    }
}