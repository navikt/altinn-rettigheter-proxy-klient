package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.InputStream

class ProxyErrorMedResponseBody(private val responseBody: ProxyResponseIErrorBody, val httpStatus: Int): ProxyError()  {

    override val melding: String
        get() = responseBody.message

    override val kilde: Kilde = try {
        Kilde.valueOf(responseBody.origin.toUpperCase())
    } catch (e: Exception) {
        Kilde.ALTINN_RETTIGHETER_PROXY_KLIENT
    }

    data class ProxyResponseIErrorBody(val message: String, val origin: String) {
        constructor(message: String, kilde: Kilde) : this(message, origin = kilde.verdi)
    }

    companion object {
        fun parse(body: InputStream, httpStatus: Int): ProxyErrorMedResponseBody {
            val inputAsString = body.bufferedReader().use { it.readText() }
            return ProxyErrorMedResponseBody(parseBody(inputAsString), httpStatus)
        }

        private fun parseBody(inputAsString: String): ProxyResponseIErrorBody {
            val mapper = jacksonObjectMapper()

            return try {
                mapper.readValue(inputAsString)
            } catch (e: Exception) {
                ProxyResponseIErrorBody(
                        "Uhåndtert feil: ${e.message}",
                        Kilde.ALTINN_RETTIGHETER_PROXY_KLIENT)
            }
        }
    }
}

