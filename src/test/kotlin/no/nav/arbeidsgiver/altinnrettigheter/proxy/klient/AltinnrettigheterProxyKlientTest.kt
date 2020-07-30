package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient

import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient.Companion.PROXY_ENDEPUNKT_API_ORGANISASJONER
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient.Companion.getAltinnrettigheterProxyURL
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient.Companion.getAltinnURL
import org.junit.Assert.*
import org.junit.Test

class AltinnrettigheterProxyKlientTest {

    @Test
    fun `getAltinnrettigheterProxyURL() fjerner trailing slash på base path`() {
        assertEquals("http://altinn.proxy$PROXY_ENDEPUNKT_API_ORGANISASJONER",
                getAltinnrettigheterProxyURL("http://altinn.proxy/", PROXY_ENDEPUNKT_API_ORGANISASJONER))
    }

    @Test
    fun `getAltinnURL() fjerner trailing slash på base path`() {
        assertEquals("http://altinn.no/ekstern/altinn/api/serviceowner/reportees",
                getAltinnURL("http://altinn.no/"))
    }

}
