package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils

import org.slf4j.MDC
import java.util.*

class CorrelationIdUtils {

    companion object {
        private const val CORRELATION_ID_MDC_NAME = "correlationId"

        fun getCorrelationId(): String {

            if (MDC.get(CORRELATION_ID_MDC_NAME).isNullOrBlank()) {
                MDC.put(CORRELATION_ID_MDC_NAME, UUID.randomUUID().toString())
            }
            return MDC.get(CORRELATION_ID_MDC_NAME)?: UUID.randomUUID().toString()
        }
    }
}