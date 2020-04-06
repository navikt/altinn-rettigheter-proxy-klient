package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient.Companion.ISSUER_SELVBETJENING
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import no.nav.security.oidc.context.TokenContext
import no.nav.security.oidc.test.support.JwtTokenGenerator
import org.junit.*
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail


class AltinnrettigheterProxyKlientIntegrationTest {

    val restTemplate: RestTemplate = RestTemplate()
    private val restTemplateBuilder = object: RestTemplateBuilder() {
        override fun build(): RestTemplate {
            return restTemplate
        }
    }

    private val klient: AltinnrettigheterProxyKlient = AltinnrettigheterProxyKlient(
            AltinnrettigheterProxyKlientConfig(
                    ProxyConfig(
                            "klient-applikasjon", "http://localhost:${PORT}/proxy"
                    ),
                    AltinnConfig(
                    "http://localhost:${PORT}/altinn",
                    "test",
                    "test"
                    )
            ),
            restTemplateBuilder
    )

    @Before
    fun setUp() {
        wireMockServer.resetAll()
    }

    @Test
    fun `hentOrganisasjoner() kaller AltinnrettigheterProxy med riktige parametre og returnerer en liste av Altinn reportees`() {

        `altinn-rettigheter-proxy mock skal returnere 200 OK og en liste med to AltinnReportee`()

        val organisasjoner = klient.hentOrganisasjoner(
                tokenContext,
                Subject(FNR_INNLOGGET_BRUKER),
                ServiceCode("3403"),
                ServiceEdition("1")
        )

        `sjekk at altinn-rettigheter-proxy-klient kaller proxy`()
        assertTrue { organisasjoner.size == 2 }
    }

    @Test
    fun `hentOrganisasjoner() kaster en  AltinnException dersom Altinn svarer med feil`() {

        `altinn-rettigheter-proxy mock skal returnere 400 med 'melding' og 'kilde' i response body`(
                "ALTINN",
                "400: The ServiceCode=9999 and ServiceEditionCode=1 are either invalid or non-existing")

        try {
            klient.hentOrganisasjoner(
                    tokenContext,
                    Subject(FNR_INNLOGGET_BRUKER),
                    ServiceCode("9999"),
                    ServiceEdition("1")
            )
            fail("Skulle har fått en exception")
        } catch (e: Exception) {
            assertEquals("400: The ServiceCode=9999 and ServiceEditionCode=1 " +
                    "are either invalid or non-existing", e.message)

        }

        `sjekk at altinn-rettigheter-proxy mottar riktig request og at ingen direkte kall til Altinn er sent`()
    }

    @Test
    fun `hentOrganisasjoner() fallback funksjon gjør et kall direkte til Altinn etter proxy svarer med intern feil`() {

        `altinn-rettigheter-proxy mock skal returnere 500 med 'melding' og 'kilde' i response body`()
        `altinn mock skal returnere 200 OK og en liste med to AltinnReportee`()

        val organisasjoner = klient.hentOrganisasjoner(
                tokenContext,
                Subject(FNR_INNLOGGET_BRUKER),
                ServiceCode("3403"),
                ServiceEdition("1")
        )

        `sjekk at klient sendte kall til proxy og altinn`()
        assertTrue { organisasjoner.size == 2 }
    }


    /*  Hjelpemetoder til testing */

    private fun `altinn-rettigheter-proxy mock skal returnere 400 med 'melding' og 'kilde' i response body`(
            kilde: String,
            melding: String
    ) {
        wireMockServer.stubFor(get(urlPathEqualTo("/proxy/organisasjoner"))
                .withHeader("Accept", equalTo("application/json"))
                .withQueryParams(mapOf(
                        "serviceCode" to equalTo("9999"),
                        "serviceEdition" to equalTo("1")
                ))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.BAD_REQUEST.value())
                        .withHeader("Content-Type", "application/json")
                        .withBody("{" +
                                "\"origin\": \"${kilde}\"," +
                                "\"message\": \"${melding}\"}"
                        )
                )
        )
    }

    private fun `altinn-rettigheter-proxy mock skal returnere 200 OK og en liste med to AltinnReportee`() {
        wireMockServer.stubFor(get(urlPathEqualTo("/proxy/organisasjoner"))
                .withHeader("Accept", equalTo("application/json"))
                .withQueryParams(mapOf(
                        "serviceCode" to equalTo("3403"),
                        "serviceEdition" to equalTo("1")
                ))
                .willReturn(`200 response med en liste av to reportees`())
        )
    }

    private fun `altinn mock skal returnere 200 OK og en liste med to AltinnReportee`() {
        wireMockServer.stubFor(get(urlPathEqualTo("/altinn/ekstern/altinn/api/serviceowner/reportees"))
                .withHeader("Accept", equalTo("application/json"))
                .withQueryParams(mapOf(
                        "serviceCode" to equalTo("3403"),
                        "serviceEdition" to equalTo("1")
                ))
                .willReturn(`200 response med en liste av to reportees`())
        )
    }

    private fun `200 response med en liste av to reportees`(): ResponseDefinitionBuilder? {
        return aResponse()
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

    private fun `altinn-rettigheter-proxy mock skal returnere 500 med 'melding' og 'kilde' i response body`() {
        wireMockServer.stubFor(get(urlEqualTo("/proxy/organisasjoner?serviceCode=3403&serviceEdition=1"))
                .withHeader("Accept", equalTo("application/json"))
                .withQueryParams(mapOf(
                        "serviceCode" to equalTo("3403"),
                        "serviceEdition" to equalTo("1")
                ))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .withHeader("Content-Type", "application/json")
                        .withBody("{" +
                                "\"origin\": \"ALTINN_RETTIGHETER_PROXY\"," +
                                "\"message\": \"500: Internal server error"
                        )
                )
        )
    }

    private fun `sjekk at altinn-rettigheter-proxy-klient kaller proxy`() {
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/proxy/organisasjoner"))
                .withHeader("Accept", containing("application/json"))
                .withQueryParam("serviceCode", equalTo("3403"))
                .withQueryParam("serviceEdition", equalTo("1"))
        )
    }

    private fun `sjekk at altinn-rettigheter-proxy mottar riktig request og at ingen direkte kall til Altinn er sent`() {
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/proxy/organisasjoner"))
                .withHeader("Accept", containing("application/json"))
                .withQueryParam("serviceCode", equalTo("9999"))
                .withQueryParam("serviceEdition", equalTo("1"))
        )

        val alleRequestTilAltinn = wireMockServer.findAll(getRequestedFor(urlMatching("/altinn/.*")))
        assertTrue(alleRequestTilAltinn.isEmpty())
    }

    private fun `sjekk at klient sendte kall til proxy og altinn`() {
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/proxy/organisasjoner"))
                .withHeader("Accept", containing("application/json"))
                .withQueryParam("serviceCode", equalTo("3403"))
                .withQueryParam("serviceEdition", equalTo("1")))

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/altinn/ekstern/altinn/api/serviceowner/reportees"))
                .withHeader("Accept", containing("application/json"))
                .withQueryParam("ForceEIAuthentication", equalTo(""))
                .withQueryParam("subject", equalTo(FNR_INNLOGGET_BRUKER))
                .withQueryParam("serviceCode", equalTo("3403"))
                .withQueryParam("serviceEdition", equalTo("1")))
    }


    companion object {
        const val PORT:Int = 1331
        const val FNR_INNLOGGET_BRUKER = "15008462396"

        private lateinit var wireMockServer: WireMockServer
        val tokenContext = TokenContext(ISSUER_SELVBETJENING, JwtTokenGenerator.signedJWTAsString(FNR_INNLOGGET_BRUKER))

        @BeforeClass
        @JvmStatic
        fun initClass() {
            wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig()
                    .port(PORT)
                    .notifier(ConsoleNotifier(true)))
            wireMockServer.start()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            wireMockServer.stop()
        }
    }
}
