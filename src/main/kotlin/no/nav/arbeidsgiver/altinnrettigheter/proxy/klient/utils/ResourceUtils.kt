package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils


internal class ResourceUtils {

    companion object {
        private const val RESOURCE_FILE = "/altinn-rettigheter-proxy-client.properties"
        private const val KLIENT_VERSJON_PROPERTY_KEY = "klient.versjon"
        const val INGEN_VERSJON_TILGJENGELIG: String = "INGEN_VERSJON"

        fun getKlientVersjon(resourceFile: String = RESOURCE_FILE): String {
            val resource = getAllResources(resourceFile).find { r -> r.key == KLIENT_VERSJON_PROPERTY_KEY }

            return resource?.value?: return INGEN_VERSJON_TILGJENGELIG
        }


        fun getAllResources(resourceFile: String): List<Resource> {
            val resourceAsStream = this::class.java.getResourceAsStream(resourceFile) ?: return emptyList()

            val lines = resourceAsStream.bufferedReader().readLines()
            val arrays = lines
                    .filter{
                        line -> line.contains('=')
                    }
                    .map {
                        line -> line.split("=").toTypedArray()
                    }

            return arrays.map { array ->  Resource(array[0], array[1]) }
        }
    }
}
