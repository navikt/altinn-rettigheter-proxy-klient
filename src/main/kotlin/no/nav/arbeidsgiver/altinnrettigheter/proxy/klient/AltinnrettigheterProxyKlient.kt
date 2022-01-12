package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.client.FallbackClient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.ProxyError
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientFallbackException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.*
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.service.FallbackService
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.ResourceUtils
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.getCorrelationId
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.withCorrelationId
import org.slf4j.LoggerFactory

class AltinnrettigheterProxyKlient(
    config: AltinnrettigheterProxyKlientConfig
) {
    private val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
    }

    private val fallbackService = FallbackService(
        proxyClient = ProxyClient(config, httpClient),
        fallbackClient = FallbackClient(config, httpClient),
    )


    /**
     * Hent alle organisasjoner i Altinn en bruker har rettigheter i.
     *  @param selvbetjeningToken - Selvbetjening token til innlogget bruker
     *  @param subject - Fødselsnummer til innlogget bruker (fallback funksjon)
     *  @param filtrerPåAktiveOrganisasjoner - Aktiver filtering på både Status og Type
     *
     *  @return en liste av alle organisasjoner
     *   - med Status: 'Active' og Type: 'Enterprise' | 'Business', når filtrerPåAktiveOrganisasjoner er 'true'
     *   - med Status: 'Active' | 'Inactive' og Type: 'Enterprise' | 'Business' | 'Person', når filtrerPåAktiveOrganisasjoner er 'false'
     *
     *  Ønsker å skille mellom:
     *  - selvbetjenings-token utløpt
     *  - har ikke aktiv altinn-profil
     *  - DomeneFeil/Programmeringsfeil (404, ukjent service code, etc)
     *  - Driftsfeil
     *
     *  Hvordan nå:
     *  @throws AltinnException
     *  @throws AltinnrettigheterProxyKlientException
     *  @throws AltinnrettigheterProxyKlientFallbackException
     */
    fun hentOrganisasjoner(
            selvbetjeningToken: SelvbetjeningToken,
            subject: Subject,
            filtrerPåAktiveOrganisasjoner: Boolean
    ): List<AltinnReportee> {
        return fallbackService.hentOrganisasjoner(
                selvbetjeningToken,
                subject,
                null,
                null,
                filtrerPåAktiveOrganisasjoner
        )
    }

    /**
     * Hent alle organisasjoner i Altinn en bruker har enkel rettighet i
     *  @param selvbetjeningToken - Selvbetjening token til innlogget bruker
     *  @param subject - Fødselsnummer til innlogget bruker (fallback funksjon)
     *  @param serviceCode - Kode for rettigheter brukeren har for en organisasjon (henger sammen med ServiceEdition)
     *  @param serviceEdition
     *  @param filtrerPåAktiveOrganisasjoner - Aktiver filtering på både Status og Type
     *
     *  @return en liste av alle organisasjoner
     *   - med Status: 'Active' og Type: 'Enterprise' | 'Business', når filtrerPåAktiveOrganisasjoner er 'true'
     *   - med Status: 'Active' | 'Inactive' og Type: 'Enterprise' | 'Business' | 'Person', når filtrerPåAktiveOrganisasjoner er 'false'
     */
    fun hentOrganisasjoner(
            selvbetjeningToken: SelvbetjeningToken,
            subject: Subject,
            serviceCode: ServiceCode,
            serviceEdition: ServiceEdition,
            filtrerPåAktiveOrganisasjoner: Boolean
    ): List<AltinnReportee> {
        return fallbackService.hentOrganisasjoner(
                selvbetjeningToken,
                subject,
                serviceCode,
                serviceEdition,
                filtrerPåAktiveOrganisasjoner
        )
    }
}

