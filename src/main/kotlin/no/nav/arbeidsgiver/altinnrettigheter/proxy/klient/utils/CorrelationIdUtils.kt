package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.utils

import org.slf4j.MDC
import java.util.*

private const val CORRELATION_ID_MDC_NAME = "correlationId"

private fun <T> runWithMDC(key: String, value: String, action: () -> T): T {
    MDC.put(key, value)
    return try {
        action()
    } finally {
        MDC.remove(key)
    }
}

fun <T> withCorrelationId(action: () -> T): T {
    val correlationId = MDC.get(CORRELATION_ID_MDC_NAME)
    return if (correlationId.isNullOrBlank()) {
        runWithMDC(CORRELATION_ID_MDC_NAME, UUID.randomUUID().toString()) {
            action()
        }
    } else {
        action()
    }
}

fun getCorrelationId(): String =
    MDC.get(CORRELATION_ID_MDC_NAME)
        ?: UUID.randomUUID().toString()
