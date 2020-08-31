package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils.ResourceUtils.Companion.INGEN_VERSJON_TILGJENGELIG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceUtilsTest {


    @Test
    fun getKlientVersjon_returnerer_nåværende_klientVersjon() {
        val klientVersjon = ResourceUtils.getKlientVersjon()

        assertTrue(
                "Klient versjonsnummer '${klientVersjon}' skal starte med et tall",
                klientVersjon.matches(Regex("^[0-9].*"))
        )
    }

    @Test
    fun getKlientVersjon_returnerer_default_verdi_dersom_ingen_versjon_er_funnet() {
        val klientVersjon = ResourceUtils.getKlientVersjon(FILEN_FINNES_IKKE)

        assertEquals(INGEN_VERSJON_TILGJENGELIG, klientVersjon)
    }

    @Test
    fun getAllResources_returnerer_en_tom_liste_dersom_ressurs_filen_ikke_finnes() {
        val resourcesNårRessursFilenIkkeErAngitt: List<Resource> =
                ResourceUtils.getAllResources("")

        val resourcesNårRessursFilenIkkeFinnes: List<Resource> =
                ResourceUtils.getAllResources(FILEN_FINNES_IKKE)

        assertTrue(resourcesNårRessursFilenIkkeErAngitt.isEmpty())
        assertTrue(resourcesNårRessursFilenIkkeFinnes.isEmpty())
    }


    companion object {
        private const val FILEN_FINNES_IKKE = "FILEN_FINNES_IKKE"
    }
}
