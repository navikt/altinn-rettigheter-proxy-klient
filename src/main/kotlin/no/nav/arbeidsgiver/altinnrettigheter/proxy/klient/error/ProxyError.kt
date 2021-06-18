package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream

open class ProxyError(
    val httpStatus: Int,
    val melding: String,
    val cause: String
) {

    companion object {
        fun parse(body: InputStream, httpStatus: Int): ProxyError {
            val inputAsString = body.bufferedReader().use { it.readText() }
            val proxyResponseIErrorBody = parseBody(inputAsString)
            return ProxyError(
                httpStatus = httpStatus,
                melding = proxyResponseIErrorBody.message,
                cause = proxyResponseIErrorBody.cause
            )
        }

        private fun parseBody(inputAsString: String): ProxyResponseIErrorBody {
            val mapper = jacksonObjectMapper()

            return try {
                mapper.readValue(inputAsString)
            } catch (e: Exception) {
                logger.error("Kunne ikke parse response body `${inputAsString}`. Årsak: '${e.message}'", e)
                ProxyResponseIErrorBody("Uhåndtert feil i proxy", e.message!!)
            }
        }

        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    data class ProxyResponseIErrorBody(val message: String, val cause: String)

}

