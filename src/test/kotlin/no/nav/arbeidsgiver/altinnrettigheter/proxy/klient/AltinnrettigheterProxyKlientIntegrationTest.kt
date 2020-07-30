package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.FNR_INNLOGGET_BRUKER
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.SERVICE_EDITION
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.SYKEFRAVÆR_SERVICE_CODE
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy mottar riktig request`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientIntegrationTestUtils.Companion.`altinn-rettigheter-proxy returnerer 200 OK og en liste med AltinnReportees`
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.SelvbetjeningToken
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertTrue


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
        wireMockServer.stubFor(`altinn-rettigheter-proxy returnerer 200 OK og en liste med AltinnReportees`(
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
    fun `hentOrganisasjoner() kaller AltinnrettigheterProxy flere ganger hvis bruker har tilgang til flere enn 499 virksomheter`() {
        wireMockServer.stubFor(`altinn-rettigheter-proxy returnerer 200 OK og en liste med AltinnReportees`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION,
                500)
        )
        wireMockServer.stubFor(`altinn-rettigheter-proxy returnerer 200 OK og en liste med AltinnReportees`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION,
                0,
                "500")
        )

        val organisasjoner = klient.hentOrganisasjoner(
                selvbetjeningToken,
                Subject(FNR_INNLOGGET_BRUKER),
                ServiceCode(SYKEFRAVÆR_SERVICE_CODE),
                ServiceEdition(SERVICE_EDITION)
        )

        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION, "0"))
        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION, "500"))
        assertTrue { organisasjoner.size == 500 }
    }

    @Test
    fun `hentOrganisasjoner() skal hente alle virksomhetene hvis bruker har tilgang til flere enn 500 virksomheter`() {
        wireMockServer.stubFor(`altinn-rettigheter-proxy returnerer 200 OK og en liste med AltinnReportees`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION,
                500)
        )
        wireMockServer.stubFor(`altinn-rettigheter-proxy returnerer 200 OK og en liste med AltinnReportees`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION,
                500,
                "500")
        )
        wireMockServer.stubFor(`altinn-rettigheter-proxy returnerer 200 OK og en liste med AltinnReportees`(
                SYKEFRAVÆR_SERVICE_CODE,
                SERVICE_EDITION,
                321,
                "1000")
        )

        val organisasjoner = klient.hentOrganisasjoner(
                selvbetjeningToken,
                Subject(FNR_INNLOGGET_BRUKER),
                ServiceCode(SYKEFRAVÆR_SERVICE_CODE),
                ServiceEdition(SERVICE_EDITION)
        )

        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION, "0"))
        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION, "500"))
        wireMockServer.verify(`altinn-rettigheter-proxy mottar riktig request`(SYKEFRAVÆR_SERVICE_CODE, SERVICE_EDITION, "1000"))
        assertTrue { organisasjoner.size == 1321 }
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
