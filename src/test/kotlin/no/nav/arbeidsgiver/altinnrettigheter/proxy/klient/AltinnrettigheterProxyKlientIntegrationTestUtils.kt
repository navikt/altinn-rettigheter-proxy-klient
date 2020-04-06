package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.ProxyResponseIError
import org.apache.http.HttpStatus

class AltinnrettigheterProxyKlientIntegrationTestUtils {

    companion object {

        fun `altinn-rettigheter-proxy returnerer 200 OK og en liste med to AltinnReportee`(
                serviceCode: String,
                serviceEdition: String
        ): MappingBuilder {
            return WireMock.get(WireMock.urlPathEqualTo("/proxy/organisasjoner"))
                    .withHeader("Accept", WireMock.equalTo("application/json"))
                    .withQueryParams(mapOf(
                            "serviceCode" to WireMock.equalTo(serviceCode),
                            "serviceEdition" to WireMock.equalTo(serviceEdition)
                    ))
                    .willReturn(`200 response med en liste av to reportees`())
        }

        fun `altinn-rettigheter-proxy returnerer en feil av type 'httpStatus' med 'kilde' og 'melding' i response body`(
                serviceCode: String,
                serviceEdition: String,
                httpStatusKode: Int,
                kilde: ProxyResponseIError.Kilde,
                melding: String
        ): MappingBuilder {
            return WireMock.get(WireMock.urlPathEqualTo("/proxy/organisasjoner"))
                    .withHeader("Accept", WireMock.equalTo("application/json"))
                    .withQueryParams(mapOf(
                            "serviceCode" to WireMock.equalTo(serviceCode),
                            "serviceEdition" to WireMock.equalTo(serviceEdition)
                    ))
                    .willReturn(WireMock.aResponse()
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
            return WireMock.get(WireMock.urlPathEqualTo("/proxy/organisasjoner"))
                    .withHeader("Accept", WireMock.equalTo("application/json"))
                    .withQueryParams(mapOf(
                            "serviceCode" to WireMock.equalTo(serviceCode),
                            "serviceEdition" to WireMock.equalTo(serviceEdition)
                    ))
                    .willReturn(WireMock.aResponse()
                            .withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{" +
                                    "\"status\": \"500\"," +
                                    "\"message\": \"Internal Server Error\"}"
                            )
                    )
        }

        fun `altinn returnerer 200 OK og en liste med to AltinnReportee`(serviceCode: String, serviceEdition: String): MappingBuilder {
            return WireMock.get(WireMock.urlPathEqualTo("/altinn/ekstern/altinn/api/serviceowner/reportees"))
                    .withHeader("Accept", WireMock.equalTo("application/json"))
                    .withQueryParams(mapOf(
                            "serviceCode" to WireMock.equalTo(serviceCode),
                            "serviceEdition" to WireMock.equalTo(serviceEdition)
                    ))
                    .willReturn(`200 response med en liste av to reportees`())
        }

        fun `200 response med en liste av to reportees`(): ResponseDefinitionBuilder? {
            return WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[" +
                            "    {" +
                            "        \"Name\": \"BALLSTAD OG HAMARØY\"," +
                            "        \"Type\": \"Business\"," +
                            "        \"ParentOrganizationNumber\": \"811076112\"," +
                            "        \"OrganizationNumber\": \"811076732\"," +
                            "        \"OrganizationForm\": \"BEDR\"," +
                            "        \"Status\": \"Active\"" +
                            "    }," +
                            "    {" +
                            "        \"Name\": \"BALLSTAD OG HORTEN\"," +
                            "        \"Type\": \"Enterprise\"," +
                            "        \"ParentOrganizationNumber\": null," +
                            "        \"OrganizationNumber\": \"811076112\"," +
                            "        \"OrganizationForm\": \"AS\"," +
                            "        \"Status\": \"Active\"" +
                            "    }" +
                            "]")
        }

        fun `altinn returnerer 400 Bad Request`(
                melding: String,
                serviceCode:
                String, serviceEdition: String
        ): MappingBuilder {
            return WireMock.get(WireMock.urlPathEqualTo("/altinn/ekstern/altinn/api/serviceowner/reportees"))
                    .withHeader("Accept", WireMock.equalTo("application/json"))
                    .withQueryParams(mapOf(
                            "serviceCode" to WireMock.equalTo(serviceCode),
                            "serviceEdition" to WireMock.equalTo(serviceEdition)
                    ))
                    .willReturn(WireMock.aResponse()
                            .withStatus(400)
                            .withHeader("Content-Type", "application/json")
                            .withBody("\"message\": \"${melding}\"")
                    )
        }


        fun `altinn-rettigheter-proxy mottar riktig request`(
                serviceCode: String,
                serviceEdition: String
        ): RequestPatternBuilder {
            return WireMock.getRequestedFor(WireMock.urlPathEqualTo("/proxy/organisasjoner"))
                    .withHeader("Accept", WireMock.containing("application/json"))
                    .withQueryParam("serviceCode", WireMock.equalTo(serviceCode))
                    .withQueryParam("serviceEdition", WireMock.equalTo(serviceEdition))
        }

        fun `altinn mottar riktig request`(
                serviceCode: String,
                serviceEdition: String,
                subject: String
        ): RequestPatternBuilder {
            return WireMock.getRequestedFor(WireMock.urlPathEqualTo("/altinn/ekstern/altinn/api/serviceowner/reportees"))
                    .withHeader("Accept", WireMock.containing("application/json"))
                    .withQueryParam("ForceEIAuthentication", WireMock.equalTo(""))
                    .withQueryParam("subject", WireMock.equalTo(subject))
                    .withQueryParam("serviceCode", WireMock.equalTo(serviceCode))
                    .withQueryParam("serviceEdition", WireMock.equalTo(serviceEdition))
        }
    }
}