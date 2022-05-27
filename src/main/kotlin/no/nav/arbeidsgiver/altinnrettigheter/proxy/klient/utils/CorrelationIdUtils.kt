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

private fun hentKorrelasjonFraKjenteMDCKeys(): String {
    val correlationId = sequenceOf(
        "X-Request-ID",
        "X-Correlation-ID",
        "callId",
        "call-id",
        "call_id",
        "x_callId"
    )
        .map { MDC.get(it) }
        .firstOrNull { !it.isNullOrBlank()} ?: UUID.randomUUID().toString()
    return correlationId
}

fun <T> withCorrelationId(action: () -> T): T {
    val correlationId = MDC.get(CORRELATION_ID_MDC_NAME)
    return if (correlationId.isNullOrBlank()) {
        runWithMDC(CORRELATION_ID_MDC_NAME, hentKorrelasjonFraKjenteMDCKeys()) {
            action()
        }
    } else {
        action()
    }
}

fun getCorrelationId(): String =
    MDC.get(CORRELATION_ID_MDC_NAME)
        ?: UUID.randomUUID().toString()
