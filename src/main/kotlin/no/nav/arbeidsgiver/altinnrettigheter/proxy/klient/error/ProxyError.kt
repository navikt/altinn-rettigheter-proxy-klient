package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class ProxyError(
    val httpStatus: Int,
    val melding: String,
    val cause: String,
) {

    companion object {
        private val mapper = jacksonObjectMapper()

        fun parse(body: String, httpStatus: HttpStatusCode): ProxyError {
            val proxyResponseIErrorBody = parseBody(body)
            val melding = when {
                proxyResponseIErrorBody.message.isNotBlank() -> proxyResponseIErrorBody.message
                else -> httpStatus.toString()
            }
            return ProxyError(
                httpStatus = httpStatus.value,
                melding = melding,
                cause = proxyResponseIErrorBody.cause
            )
        }

        private fun parseBody(inputAsString: String): ProxyResponseIErrorBody {
            return if (inputAsString.isBlank()) {
                ProxyResponseIErrorBody(message = "", cause = "")
            } else try {
                mapper.readValue(inputAsString)
            } catch (e: JsonProcessingException) {
                logger.warn("Kunne ikke parse response body `${inputAsString}`. Årsak: '${e.message}'", e)
                ProxyResponseIErrorBody("Kunne ikke parse response body `${inputAsString}`", e.message!!)
            }
        }

        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    data class ProxyResponseIErrorBody(val message: String, val cause: String)

}

