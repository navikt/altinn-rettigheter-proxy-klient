package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import com.github.tomakehurst.wiremock.WireMockServer
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
import org.springframework.web.client.RestTemplate
import kotlin.test.assertTrue


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
    }


    @Test
    fun klient_hentOrganisasjoner_sender_kall_til_AltinnrettigheterProxy_med_riktige_parametre_mottar_organisasjoner() {

        wireMockServer.stubFor(get(urlEqualTo("/proxy/organisasjoner?ForceEIAuthentication&serviceCode=3403&serviceEdition=1"))
                .withHeader("Accept", equalTo("application/json"))
                .withQueryParams(mapOf(
                        "serviceCode" to equalTo("3403"),
                        "serviceEdition" to equalTo("1")
                ))
                .willReturn(aResponse()
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
                )
        )

        val tokenContext = TokenContext(ISSUER_SELVBETJENING, JwtTokenGenerator.signedJWTAsString(FNR_INNLOGGET_BRUKER))
        val organisasjoner = klient.hentOrganisasjoner(
                tokenContext,
                Subject(FNR_INNLOGGET_BRUKER),
                ServiceCode("3403"),
                ServiceEdition("1")
        )

        wireMockServer.verify(getRequestedFor(urlEqualTo("/proxy/organisasjoner?ForceEIAuthentication&serviceCode=3403&serviceEdition=1"))
                .withHeader("Accept", containing("application/json"))
                .withQueryParam("serviceCode", equalTo("3403"))
                .withQueryParam("serviceEdition", equalTo("1"))
        )
        assertTrue { organisasjoner.size ==  2}
    }

    companion object {
        const val PORT:Int = 1331
        const val FNR_INNLOGGET_BRUKER = "15008462396"
        private lateinit var wireMockServer: WireMockServer

        @BeforeClass
        @JvmStatic
        fun initClass() {
            wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig().port(PORT).notifier(ConsoleNotifier(true)))
            wireMockServer.start()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            wireMockServer.stop()
        }
    }
}
