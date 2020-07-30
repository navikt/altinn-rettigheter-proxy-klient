package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient.Companion.CONSUMER_ID_HEADER_NAME
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient.Companion.CORRELATION_ID_HEADER_NAME
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient.Companion.PROXY_ENDEPUNKT_API_ORGANISASJONER
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient.Companion.QUERY_PARAM_FILTER_AKTIVE_BEDRIFTER
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.ProxyError
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.SelvbetjeningToken
import org.apache.http.HttpStatus

class AltinnrettigheterProxyKlientIntegrationTestUtils {

    companion object {
        const val NON_EMPTY_STRING_REGEX = "^(?!\\s*\$).+"
        const val PORT: Int = 1331
        const val FNR_INNLOGGET_BRUKER = "15008462396"
        const val SYKEFRAVÆR_SERVICE_CODE = "3403"
        const val INVALID_SERVICE_CODE = "9999"
        const val SERVICE_EDITION = "1"

        fun `altinn-rettigheter-proxy returnerer 200 OK og en liste med AltinnReportees`(
                serviceCode: String,
                serviceEdition: String,
                antallReportees: Int = 2,
                skip: String = "0"
        ): MappingBuilder {

            return get(urlPathEqualTo("/proxy$PROXY_ENDEPUNKT_API_ORGANISASJONER"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader("Authorization", matching(NON_EMPTY_STRING_REGEX))
                    .withHeader(CORRELATION_ID_HEADER_NAME, matching(NON_EMPTY_STRING_REGEX))
                    .withHeader(CONSUMER_ID_HEADER_NAME, matching(NON_EMPTY_STRING_REGEX))

                    .withQueryParams(mapOf(
                            "serviceCode" to equalTo(serviceCode),
                            "serviceEdition" to equalTo(serviceEdition),
                            "top" to equalTo("500"),
                            "skip" to equalTo(skip),
                            "filter" to equalTo(QUERY_PARAM_FILTER_AKTIVE_BEDRIFTER)
                    ))
                    .willReturn(`200 response med en liste av reportees`(antallReportees))
        }

        fun `altinn-rettigheter-proxy returnerer en feil av type 'httpStatus' med 'kilde' og 'melding' i response body`(
                serviceCode: String,
                serviceEdition: String,
                skip: String = "0",
                httpStatusKode: Int,
                kilde: ProxyError.Kilde,
                melding: String
        ): MappingBuilder {
            return get(urlPathEqualTo("/proxy$PROXY_ENDEPUNKT_API_ORGANISASJONER"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withQueryParams(mapOf(
                            "serviceCode" to equalTo(serviceCode),
                            "serviceEdition" to equalTo(serviceEdition),
                            "top" to equalTo("500"),
                            "skip" to equalTo(skip),
                            "filter" to equalTo(QUERY_PARAM_FILTER_AKTIVE_BEDRIFTER)
                    ))
                    .willReturn(aResponse()
                            .withStatus(httpStatusKode)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{" +
                                    "\"origin\": \"${kilde.verdi}\"," +
                                    "\"message\": \"${melding}\"}"
                            )
                    )
        }

        fun `altinn-rettigheter-proxy returnerer 500 uhåndtert feil`(
                serviceCode: String,
                serviceEdition: String
        ): MappingBuilder {
            return get(urlPathEqualTo("/proxy$PROXY_ENDEPUNKT_API_ORGANISASJONER"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withQueryParams(mapOf(
                            "serviceCode" to equalTo(serviceCode),
                            "serviceEdition" to equalTo(serviceEdition),
                            "top" to equalTo("500"),
                            "skip" to equalTo("0"),
                            "filter" to equalTo(QUERY_PARAM_FILTER_AKTIVE_BEDRIFTER)
                    ))
                    .willReturn(aResponse()
                            .withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{" +
                                    "\"status\": \"500\"," +
                                    "\"message\": \"Internal Server Error\"}"
                            )
                    )
        }

        fun `altinn returnerer 200 OK og en liste med to AltinnReportee`(serviceCode: String, serviceEdition: String): MappingBuilder {
            return get(urlPathEqualTo("/altinn/ekstern/altinn/api/serviceowner/reportees"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withQueryParams(mapOf(
                            "serviceCode" to equalTo(serviceCode),
                            "serviceEdition" to equalTo(serviceEdition)
                    ))
                    .willReturn(`200 response med en liste av reportees`())
        }

        fun `200 response med en liste av reportees`(antallReportees: Int = 2): ResponseDefinitionBuilder? {
            var reportees = emptyList<String>()
            if (antallReportees != 0) {
                reportees = mutableListOf(
                        "    {" +
                                "        \"Name\": \"BALLSTAD OG HAMARØY\"," +
                                "        \"Type\": \"Business\"," +
                                "        \"ParentOrganizationNumber\": \"811076112\"," +
                                "        \"OrganizationNumber\": \"811076732\"," +
                                "        \"OrganizationForm\": \"BEDR\"," +
                                "        \"Status\": \"Active\"" +
                                "    }",
                        "    {" +
                                "        \"Name\": \"BALLSTAD OG HORTEN\"," +
                                "        \"Type\": \"Enterprise\"," +
                                "        \"ParentOrganizationNumber\": null," +
                                "        \"OrganizationNumber\": \"811076112\"," +
                                "        \"OrganizationForm\": \"AS\"," +
                                "        \"Status\": \"Active\"" +
                                "    }"
                )
                reportees.addAll(generateAltinnReporteeJson(antallReportees - 2))
            }

            return aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(reportees.joinToString(prefix = "[", postfix = "]", separator = ","))
        }

        private fun generateAltinnReporteeJson(antall: Int): List<String> {
            return List(antall) { index ->
                getAltinnReporteeJson(AltinnReportee(
                        "name_$index",
                        "Enterprise",
                        "0",
                        "$index",
                        "AS",
                        "Active"
                ))
            }
        }

        private fun getAltinnReporteeJson(reportee: AltinnReportee): String {
            return "    {" +
                    "        \"Name\": \"${reportee.name}\"," +
                    "        \"Type\": \"${reportee.type}\"," +
                    "        \"ParentOrganizationNumber\": \"${reportee.parentOrganizationNumber}\"," +
                    "        \"OrganizationNumber\": \"${reportee.organizationNumber}\"," +
                    "        \"OrganizationForm\": \"${reportee.organizationForm}\"," +
                    "        \"Status\": \"${reportee.status}\"" +
                    "    }"
        }

        fun `altinn returnerer 400 Bad Request`(
                melding: String,
                serviceCode:
                String, serviceEdition: String
        ): MappingBuilder {
            return get(urlPathEqualTo("/altinn/ekstern/altinn/api/serviceowner/reportees"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withQueryParams(mapOf(
                            "serviceCode" to equalTo(serviceCode),
                            "serviceEdition" to equalTo(serviceEdition)
                    ))
                    .willReturn(aResponse()
                            .withStatus(400)
                            .withHeader("Content-Type", "application/json")
                            .withBody("\"message\": \"${melding}\"")
                    )
        }


        fun `altinn-rettigheter-proxy mottar riktig request`(
                serviceCode: String,
                serviceEdition: String,
                skip: String = "0"
        ): RequestPatternBuilder {
            return getRequestedFor(urlPathEqualTo("/proxy$PROXY_ENDEPUNKT_API_ORGANISASJONER"))
                    .withHeader("Accept", containing("application/json"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader("Authorization", matching(NON_EMPTY_STRING_REGEX))
                    .withHeader(CORRELATION_ID_HEADER_NAME, matching(NON_EMPTY_STRING_REGEX))
                    .withHeader(CONSUMER_ID_HEADER_NAME, matching(NON_EMPTY_STRING_REGEX))
                    .withQueryParam("serviceCode", equalTo(serviceCode))
                    .withQueryParam("serviceEdition", equalTo(serviceEdition))
                    .withQueryParam("top", equalTo("500"))
                    .withQueryParam("skip", equalTo(skip))
                    .withQueryParam("filter", equalTo(QUERY_PARAM_FILTER_AKTIVE_BEDRIFTER))
        }

        fun `altinn-rettigheter-proxy mottar riktig request med flere parametre`(
                serviceCode: String,
                serviceEdition: String,
                filter: String,
                top: Int,
                skip: Int
        ): RequestPatternBuilder {
            return getRequestedFor(urlPathEqualTo("/proxy$PROXY_ENDEPUNKT_API_ORGANISASJONER"))
                    .withHeader("Accept", containing("application/json"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withHeader("Authorization", matching(NON_EMPTY_STRING_REGEX))
                    .withHeader(CORRELATION_ID_HEADER_NAME, matching(NON_EMPTY_STRING_REGEX))
                    .withHeader(CONSUMER_ID_HEADER_NAME, matching(NON_EMPTY_STRING_REGEX))
                    .withQueryParam("serviceCode", equalTo(serviceCode))
                    .withQueryParam("serviceEdition", equalTo(serviceEdition))
                    .withQueryParam("filter", equalTo(filter))
                    .withQueryParam("top", equalTo(top.toString()))
                    .withQueryParam("skip", equalTo(skip.toString()))
        }

        fun `altinn mottar riktig request`(
                serviceCode: String,
                serviceEdition: String,
                subject: String
        ): RequestPatternBuilder {
            return getRequestedFor(urlPathEqualTo("/altinn/ekstern/altinn/api/serviceowner/reportees"))
                    .withHeader("Accept", containing("application/json"))
                    .withHeader(CORRELATION_ID_HEADER_NAME, matching(NON_EMPTY_STRING_REGEX))
                    .withoutHeader("Authorization")
                    .withQueryParam("ForceEIAuthentication", equalTo(""))
                    .withQueryParam("subject", equalTo(subject))
                    .withQueryParam("serviceCode", equalTo(serviceCode))
                    .withQueryParam("serviceEdition", equalTo(serviceEdition))
        }

        fun `altinn-rettigheter-proxy returnerer 502 Bad Gateway`(
                serviceCode: String,
                serviceEdition: String
        ): MappingBuilder {
            return get(urlPathEqualTo("/proxy$PROXY_ENDEPUNKT_API_ORGANISASJONER"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withQueryParams(mapOf(
                            "serviceCode" to equalTo(serviceCode),
                            "serviceEdition" to equalTo(serviceEdition),
                            "top" to equalTo("500"),
                            "skip" to equalTo("0"),
                            "filter" to equalTo(QUERY_PARAM_FILTER_AKTIVE_BEDRIFTER)
                    ))
                    .willReturn(aResponse()
                            .withStatus(HttpStatus.SC_BAD_GATEWAY)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{" +
                                    "\"status\": \"502\"," +
                                    "\"message\": \"Bad Gateway\"}"
                            )
                    )
        }


        fun `altinn returnerer 200 OK og en liste med to AltinnReportee`(
                serviceCode: String, serviceEdition: String,
                filterRegex: String,
                top: Int,
                skip: Int
        ): MappingBuilder {
            return get(urlPathEqualTo("/altinn/ekstern/altinn/api/serviceowner/reportees"))
                    .withHeader("Accept", equalTo("application/json"))
                    .withQueryParams(mapOf(
                            "serviceCode" to equalTo(serviceCode),
                            "serviceEdition" to equalTo(serviceEdition),
                            "%24filter" to matching(filterRegex),
                            "%24top" to equalTo(top.toString()),
                            "%24skip" to equalTo(skip.toString())
                    ))
                    .willReturn(`200 response med en liste av reportees`())
        }

        fun `altinn mottar riktig request`(
                serviceCode: String,
                serviceEdition: String,
                subject: String,
                filter: String,
                top: Int,
                skip: Int
        ): RequestPatternBuilder {
            return getRequestedFor(urlPathEqualTo("/altinn/ekstern/altinn/api/serviceowner/reportees"))
                    .withHeader("Accept", containing("application/json"))
                    .withHeader(CORRELATION_ID_HEADER_NAME, matching(NON_EMPTY_STRING_REGEX))
                    .withoutHeader("Authorization")
                    .withQueryParam("ForceEIAuthentication", equalTo(""))
                    .withQueryParam("subject", equalTo(subject))
                    .withQueryParam("serviceCode", equalTo(serviceCode))
                    .withQueryParam("serviceEdition", equalTo(serviceEdition))
                    .withQueryParam("serviceEdition", equalTo(serviceEdition))
                    .withQueryParam("%24filter", equalTo(filter))
                    .withQueryParam("%24top", equalTo(top.toString()))
                    .withQueryParam("%24skip", equalTo(skip.toString()))
        }

    }
}
