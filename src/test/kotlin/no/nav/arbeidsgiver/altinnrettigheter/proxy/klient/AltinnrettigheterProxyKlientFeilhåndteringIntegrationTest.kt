package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.FNR_INNLOGGET_BRUKER
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.INVALID_SERVICE_CODE
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.SERVICE_EDITION
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.SYKEFRAVÆR_SERVICE_CODE
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn mottar riktig request`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn returnerer 200 OK og en liste med to AltinnReportee`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn returnerer 400 Bad Request`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy mottar riktig request med flere parametre`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy mottar riktig request`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy returnerer 200 OK og en liste med AltinnReportees`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy returnerer 500 uhåndtert feil`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy returnerer 502 Bad Gateway`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy returnerer en feil av type 'httpStatus' med 'melding' og 'cause' i response body`
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail


class AltinnrettigheterProxyKlientFeilhåndteringIntegrationTest {

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
    fun `hentOrganisasjoner() skal gå over til fallback hvis kall nr 2 feiler`() {
        wireMockServer.stubFor(`altinn-rettigheter-proxy returnerer 200 OK og en liste med AltinnReportees`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION,
                500)
        )
        wireMockServer.stubFor(
                `altinn-rettigheter-proxy returnerer en feil av type 'httpStatus' med 'melding' og 'cause' i response body`(
                    SYKEFRAVÆR_SERVICE_CODE,
                    SERVICE_EDITION,
                    "500",
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "500: Internal server error",
                    "ukjent feil"
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
                ServiceEdition(SERVICE_EDITION),
                true
        )

        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION, "0"))
        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION, "500"))
        wireMockServer.verify(`altinn mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION, FNR_INNLOGGET_BRUKER))

        assertTrue { organisasjoner.size == 502 }
    }

    @Test
    fun `hentOrganisasjoner() fallback funksjon gjør et kall direkte til Altinn dersom proxy er nede`() {
        // TODO: lag en bedre mock enn denne (svar et ordentlig 404, ikke WireMock sin default)
        val klientMedProxyUrlSomAldriSvarer = AltinnrettigheterProxyKlient(
                AltinnrettigheterProxyKlientConfig(
                        ProxyConfig(
                                "klient-applikasjon", "http://localhost:${PORT}/proxy-url-som-gir-404"
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
                ServiceEdition(SERVICE_EDITION),
                true
        )

        wireMockServer.verify(`altinn mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION, FNR_INNLOGGET_BRUKER))
        assertTrue { organisasjoner.size == 2 }
    }

    @Test
    fun `hentOrganisasjoner() fallback funksjon gjør et kall direkte til Altinn etter proxy svarer med intern feil`() {
        wireMockServer.stubFor(
                `altinn-rettigheter-proxy returnerer en feil av type 'httpStatus' med 'melding' og 'cause' i response body`(
                    SYKEFRAVÆR_SERVICE_CODE,
                    SERVICE_EDITION,
                    "0",
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "500: Internal server error",
                    "ukjent feil"
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
                ServiceEdition(SERVICE_EDITION),
                true
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
                ServiceEdition(SERVICE_EDITION),
                true
        )

        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION))
        wireMockServer.verify(`altinn mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION, FNR_INNLOGGET_BRUKER))
        assertTrue { organisasjoner.size == 2 }
    }

    @Test
    fun `hentOrganisasjoner() kaster en AltinnException dersom Altinn svarer med feil ved fallback kall`() {
        wireMockServer.stubFor(
                `altinn-rettigheter-proxy returnerer en feil av type 'httpStatus' med 'melding' og 'cause' i response body`(
                    INVALID_SERVICE_CODE,
                    SERVICE_EDITION,
                    "0",
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Internal Server Error",
                    "ukjent feil"
                )
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
                    ServiceEdition(SERVICE_EDITION),
                    true
            )
            fail("Skulle har fått en exception")
        } catch (e: Exception) {
            assertEquals(
                    "Fallback kall mot Altinn feiler med HTTP feil 400 'Bad Request'",
                    e.message
            )
        }

        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request`(INVALID_SERVICE_CODE, SERVICE_EDITION))
        wireMockServer.verify(`altinn mottar riktig request`(INVALID_SERVICE_CODE, SERVICE_EDITION, FNR_INNLOGGET_BRUKER))
    }

    @Test
    fun `hentOrganisasjoner() kaster exception når ingen tjeneste svarer`() {
        val klientMedProxyOgAltinnSomAldriSvarer = AltinnrettigheterProxyKlient(
                AltinnrettigheterProxyKlientConfig(
                        ProxyConfig(
                                "klient-applikasjon",
                                "http://localhost:13456/proxy-url-som-aldri-svarer"
                        ),
                        AltinnConfig(
                                "http://localhost:13456/altinn-url-som-aldri-svarer",
                                "testApiKey",
                                "test"
                        )
                )
        )

        try {
            klientMedProxyOgAltinnSomAldriSvarer.hentOrganisasjoner(
                    selvbetjeningToken,
                    Subject(FNR_INNLOGGET_BRUKER),
                    ServiceCode(INVALID_SERVICE_CODE),
                    ServiceEdition(SERVICE_EDITION),
                    true
            )
            fail("Skulle har fått en exception")
        } catch (e: Exception) {
            assertContains(
                e.message ?: "Ingen melding",
                "Fallback kall mot Altinn feiler .* 'Connection refused".toRegex()
            )
        }
    }

    @Test
    fun `hentOrganisasjoner() fallback funksjon skal ikke enkode pluss tegn i filter parameter ved direkte kall til Altinn`() {

        wireMockServer.stubFor(`altinn-rettigheter-proxy returnerer 502 Bad Gateway`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION)
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
                ServiceCode(SYKEFRAVÆR_SERVICE_CODE),
                ServiceEdition(SERVICE_EDITION),
                true
        )

        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request med flere parametre`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION,
                "Type ne 'Person' and Status eq 'Active'",
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
        private lateinit var wireMockServer: WireMockServer
        val selvbetjeningToken = SelvbetjeningToken("dette_er_ikke_en_ekte_idToken")

        @BeforeClass
        @JvmStatic
        fun initClass() {
            wireMockServer = WireMockServer(WireMockConfiguration.wireMockConfig()
                    .port(PORT)
                    .notifier(ConsoleNotifier(false)))
            wireMockServer.start()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            wireMockServer.stop()
        }
    }
}
