package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn mottar riktig request`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn returnerer 200 OK og en liste med to AltinnReportee`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn returnerer 400 Bad Request`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy med pagination og filter parametre returnerer 200 OK og en liste med to AltinnReportee`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy mottar riktig request med flere parametre`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy mottar riktig request`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy returnerer 200 OK og en liste med to AltinnReportee`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy returnerer 500 uhåndtert feil`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy returnerer 502 Bad Gateway`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy returnerer en feil av type 'httpStatus' med 'kilde' og 'melding' i response body`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.ProxyError
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.SelvbetjeningToken
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import org.apache.http.HttpStatus
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail


class AltinnrettigheterProxyKlientIntegrationTest {

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
            )
    )

    @Before
    fun setUp() {
        wireMockServer.resetAll()
    }

    @Test
    fun `hentOrganisasjoner() kaller AltinnrettigheterProxy med riktige parametre og returnerer en liste av Altinn reportees`() {
        wireMockServer.stubFor(`altinn-rettigheter-proxy returnerer 200 OK og en liste med to AltinnReportee`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION)
        )

        val organisasjoner = klient.hentOrganisasjoner(
                selvbetjeningToken,
                Subject(FNR_INNLOGGET_BRUKER),
                ServiceCode(SYKEFRAVÆR_SERVICE_CODE),
                ServiceEdition(SERVICE_EDITION)
        )

        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION))
        assertTrue { organisasjoner.size == 2 }
    }

    @Test
    fun `hentOrganisasjoner() med andre parametre kaller AltinnrettigheterProxy med riktige parametre og returnerer en liste av Altinn reportees`() {
        wireMockServer.stubFor(`altinn-rettigheter-proxy med pagination og filter parametre returnerer 200 OK og en liste med to AltinnReportee`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION,
                500,
                0,
                "Type+ne+'Person'+and+Status+eq+'Active'"
        )
        )

        val organisasjoner = klient.hentOrganisasjoner(
                selvbetjeningToken,
                Subject(FNR_INNLOGGET_BRUKER),
                mapOf(
                        "serviceCode" to "3403",
                        "serviceEdition" to "1",
                        "\$filter" to "Type+ne+'Person'+and+Status+eq+'Active'",
                        "\$top" to "500",
                        "\$skip" to "0"
                )
        )

        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request med flere parametre`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION,
                "Type+ne+'Person'+and+Status+eq+'Active'",
                500,
                0
        ))
        assertTrue { organisasjoner.size == 2 }
    }

    @Test
    fun `hentOrganisasjoner() kaster en  AltinnException dersom Altinn svarer med feil til proxy`() {
        wireMockServer.stubFor(
                `altinn-rettigheter-proxy returnerer en feil av type 'httpStatus' med 'kilde' og 'melding' i response body`(
                        INVALID_SERVICE_CODE,
                        SERVICE_EDITION,
                        HttpStatus.SC_BAD_GATEWAY,
                        ProxyError.Kilde.ALTINN,
                        "400: The ServiceCode=9999 and ServiceEditionCode=1 are either invalid or non-existing"
                )
        )

        try {
            klient.hentOrganisasjoner(
                    selvbetjeningToken,
                    Subject(FNR_INNLOGGET_BRUKER),
                    ServiceCode(INVALID_SERVICE_CODE),
                    ServiceEdition(SERVICE_EDITION)
            )
            fail("Skulle har fått en exception")
        } catch (e: Exception) {
            assertEquals("400: The ServiceCode=9999 and ServiceEditionCode=1 " +
                    "are either invalid or non-existing", e.message)
        }

        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request`(INVALID_SERVICE_CODE, SERVICE_EDITION))
        val alleRequestTilAltinn = wireMockServer.findAll(getRequestedFor(urlMatching("/altinn/.*")))
        assertTrue(alleRequestTilAltinn.isEmpty())
    }

    @Test
    fun `hentOrganisasjoner() fallback funksjon gjør et kall direkte til Altinn dersom proxy er nede`() {
        var klientMedProxyUrlSomAldriSvarer: AltinnrettigheterProxyKlient = AltinnrettigheterProxyKlient(
                AltinnrettigheterProxyKlientConfig(
                        ProxyConfig(
                                "klient-applikasjon", "http://localhost:${PORT}/proxy-url-som-aldri-svarer"
                        ),
                        AltinnConfig(
                                "http://localhost:${PORT}/altinn/",
                                "test",
                                "test"
                        )
                )
        )
        wireMockServer.stubFor(`altinn returnerer 200 OK og en liste med to AltinnReportee`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION)
        )

        val organisasjoner = klientMedProxyUrlSomAldriSvarer.hentOrganisasjoner(
                selvbetjeningToken,
                Subject(FNR_INNLOGGET_BRUKER),
                ServiceCode(SYKEFRAVÆR_SERVICE_CODE),
                ServiceEdition(SERVICE_EDITION)
        )

        wireMockServer.verify(`altinn mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION, FNR_INNLOGGET_BRUKER))
        assertTrue { organisasjoner.size == 2 }
    }

    @Test
    fun `hentOrganisasjoner() fallback funksjon gjør et kall direkte til Altinn etter proxy svarer med intern feil`() {
        wireMockServer.stubFor(
                `altinn-rettigheter-proxy returnerer en feil av type 'httpStatus' med 'kilde' og 'melding' i response body`(
                        SYKEFRAVÆR_SERVICE_CODE,
                        SERVICE_EDITION,
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        ProxyError.Kilde.ALTINN_RETTIGHETER_PROXY,
                        "500: Internal server error"
                )
        )
        wireMockServer.stubFor(`altinn returnerer 200 OK og en liste med to AltinnReportee`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION)
        )

        val organisasjoner = klient.hentOrganisasjoner(
                selvbetjeningToken,
                Subject(FNR_INNLOGGET_BRUKER),
                ServiceCode(SYKEFRAVÆR_SERVICE_CODE),
                ServiceEdition(SERVICE_EDITION)
        )

        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION))
        wireMockServer.verify(`altinn mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION, FNR_INNLOGGET_BRUKER))
        assertTrue { organisasjoner.size == 2 }
    }

    @Test
    fun `hentOrganisasjoner() fallback funksjon gjør et kall direkte til Altinn etter proxy svarer med uhåndtert feil`() {

        wireMockServer.stubFor(`altinn-rettigheter-proxy returnerer 500 uhåndtert feil`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION)
        )
        wireMockServer.stubFor(`altinn returnerer 200 OK og en liste med to AltinnReportee`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION)
        )

        val organisasjoner = klient.hentOrganisasjoner(
                selvbetjeningToken,
                Subject(FNR_INNLOGGET_BRUKER),
                ServiceCode(SYKEFRAVÆR_SERVICE_CODE),
                ServiceEdition(SERVICE_EDITION)
        )

        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION))
        wireMockServer.verify(`altinn mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION, FNR_INNLOGGET_BRUKER))
        assertTrue { organisasjoner.size == 2 }
    }

    @Test
    fun `hentOrganisasjoner() kaster en AltinnException dersom Altinn svarer med feil ved fallback kall`() {
        wireMockServer.stubFor(
                `altinn-rettigheter-proxy returnerer en feil av type 'httpStatus' med 'kilde' og 'melding' i response body`(
                        INVALID_SERVICE_CODE,
                        SERVICE_EDITION,
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        ProxyError.Kilde.ALTINN_RETTIGHETER_PROXY,
                        "Internal Server Error")
        )
        wireMockServer.stubFor(
                `altinn returnerer 400 Bad Request`(
                        "400: The ServiceCode=9999 and ServiceEditionCode=1 are either invalid or non-existing",
                        INVALID_SERVICE_CODE,
                        SERVICE_EDITION)
        )

        try {
            klient.hentOrganisasjoner(
                    selvbetjeningToken,
                    Subject(FNR_INNLOGGET_BRUKER),
                    ServiceCode(INVALID_SERVICE_CODE),
                    ServiceEdition(SERVICE_EDITION)
            )
            fail("Skulle har fått en exception")
        } catch (e: Exception) {
            assertEquals(
                    "Fallback kall mot Altinn feiler. Med HTTP kode '400' og melding 'Bad Request'",
                    e.message
            )
        }

        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request`(INVALID_SERVICE_CODE, SERVICE_EDITION))
        wireMockServer.verify(`altinn mottar riktig request`(INVALID_SERVICE_CODE, SERVICE_EDITION, FNR_INNLOGGET_BRUKER))
    }

    @Test
    fun `hentOrganisasjoner() kaster exception når ingen tjeneste svarer`() {
        var klientMedProxyOgAltinnSomAldriSvarer = AltinnrettigheterProxyKlient(
                AltinnrettigheterProxyKlientConfig(
                        ProxyConfig(
                                "klient-applikasjon",
                                "http://localhost:13456/proxy-url-som-aldri-svarer"
                        ),
                        AltinnConfig(
                                "http://localhost:13456/altinn-url-som-aldri-svarer",
                                "test",
                                "test"
                        )
                )
        )

        try {
            klientMedProxyOgAltinnSomAldriSvarer.hentOrganisasjoner(
                    selvbetjeningToken,
                    Subject(FNR_INNLOGGET_BRUKER),
                    ServiceCode(INVALID_SERVICE_CODE),
                    ServiceEdition(SERVICE_EDITION)
            )
            fail("Skulle har fått en exception")
        } catch (e: Exception) {
            assertTrue(
                    (e.message ?: "Ingen melding").startsWith(
                            "Fallback kall mot Altinn feiler. Med melding 'Connection refused"
                    )
            )
        }
    }

    @Test
    fun `hentOrganisasjoner() fallback funksjon skal ikke enkode pluss tegn i filter parameter ved direkte kall til Altinn`() {

        wireMockServer.stubFor(`altinn-rettigheter-proxy returnerer 502 Bad Gateway`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION,
                "^Type.ne.'Person'.and.Status.eq.'Active'\$",
                500,
                0)
        )
        wireMockServer.stubFor(`altinn returnerer 200 OK og en liste med to AltinnReportee`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION,
                "^Type.ne.'Person'.and.Status.eq.'Active'\$",
                500,
                0)
        )

        val organisasjoner = klient.hentOrganisasjoner(
                selvbetjeningToken,
                Subject(FNR_INNLOGGET_BRUKER),
                mapOf(
                        "serviceCode" to SYKEFRAVÆR_SERVICE_CODE,
                        "serviceEdition" to SERVICE_EDITION,
                        "\$filter" to "Type+ne+'Person'+and+Status+eq+'Active'",
                        "\$top" to "500",
                        "\$skip" to "0"
                )
        )

        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request med flere parametre`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION,
                "Type+ne+'Person'+and+Status+eq+'Active'",
                500,
                0)
        )
        wireMockServer.verify(`altinn mottar riktig request`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION,
                FNR_INNLOGGET_BRUKER,
                "Type ne 'Person' and Status eq 'Active'",
                500,
                0)
        )
        assertTrue { organisasjoner.size == 2 }
    }


    companion object {
        const val PORT: Int = 1331
        const val FNR_INNLOGGET_BRUKER = "15008462396"
        const val SYKEFRAVÆR_SERVICE_CODE = "3403"
        const val INVALID_SERVICE_CODE = "9999"
        const val SERVICE_EDITION = "1"

        private lateinit var wireMockServer: WireMockServer
        val selvbetjeningToken = SelvbetjeningToken("dette_er_ikke_en_ekte_idToken")

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
