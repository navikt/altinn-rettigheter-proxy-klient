package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.service

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.ProxyClient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.client.FallbackClient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error.exceptions.AltinnrettigheterProxyKlientException
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.*
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.withCorrelationId
import org.slf4j.LoggerFactory

class FallbackService(
    private val fallbackClient: FallbackClient,
    private val proxyClient: ProxyClient,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)!!

    fun hentOrganisasjoner(
        selvbetjeningToken: SelvbetjeningToken,
        subject: Subject,
        serviceCode: ServiceCode?,
        serviceEdition: ServiceEdition?,
        filtrerPåAktiveOrganisasjoner: Boolean
    ): List<AltinnReportee> = withCorrelationId {
        val organisasjoner: ArrayList<AltinnReportee> = ArrayList()
        var detFinnesFlereOrganisasjoner = true

        val filterValue = if (filtrerPåAktiveOrganisasjoner) QUERY_PARAM_FILTER_AKTIVE_BEDRIFTER else null

        while (detFinnesFlereOrganisasjoner) {
            val nyeOrganisasjoner = hentOrganisasjonerMedFallbackFunksjonalitet(
                selvbetjeningToken,
                subject,
                serviceCode,
                serviceEdition,
                DEFAULT_PAGE_SIZE,
                organisasjoner.size,
                filterValue
            )

            if (nyeOrganisasjoner.size > DEFAULT_PAGE_SIZE) {
                logger.error("Altinn returnerer flere organisasjoner (${nyeOrganisasjoner.size}) " +
                        "enn det vi spurte om (${DEFAULT_PAGE_SIZE}). " +
                        "Dette medfører at brukeren ikke får tilgang til alle bedriftene sine")
            }

            if (nyeOrganisasjoner.size != DEFAULT_PAGE_SIZE) {
                detFinnesFlereOrganisasjoner = false
            }

            organisasjoner.addAll(nyeOrganisasjoner)
        }

        return@withCorrelationId organisasjoner
    }

    private fun hentOrganisasjonerMedFallbackFunksjonalitet(
        selvbetjeningToken: SelvbetjeningToken,
        subject: Subject,
        serviceCode: ServiceCode?,
        serviceEdition: ServiceEdition?,
        top: Number,
        skip: Number,
        filter: String?
    ): List<AltinnReportee> {
        return try {
            proxyClient.hentOrganisasjoner(selvbetjeningToken, serviceCode, serviceEdition, top, skip, filter)
        } catch (proxyException: AltinnrettigheterProxyException) {
            logger.warn("Fikk en feil i altinn-rettigheter-proxy med melding '${proxyException.message}'. " +
                    "Gjør et nytt forsøk ved å kalle Altinn direkte.")
            fallbackClient.hentOrganisasjoner(subject, serviceCode, serviceEdition, top, skip, filter)
        } catch (altinnException: AltinnException) {
            logger.warn("Fikk exception i Altinn med følgende melding '${altinnException.message}'. " +
                    "Exception fra Altinn håndteres av klient applikasjon")
            throw altinnException
        } catch (exception: Exception) {
            logger.warn("Fikk exception med følgende melding '${exception.message}'. " +
                    "Denne skal håndteres av klient applikasjon")
            throw AltinnrettigheterProxyKlientException("Exception ved kall til proxy", exception)
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 500
        const val QUERY_PARAM_FILTER_AKTIVE_BEDRIFTER = "Type ne 'Person' and Status eq 'Active'"

        const val CORRELATION_ID_HEADER_NAME = "X-Correlation-ID"
    }
}