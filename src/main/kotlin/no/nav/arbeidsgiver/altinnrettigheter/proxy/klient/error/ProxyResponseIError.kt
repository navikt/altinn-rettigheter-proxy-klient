package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.InputStream

class ProxyResponseIError(responseBody: ProxyResponseIErrorBody, val httpStatus: Int) {

    val melding: String = responseBody.message

    val kilde: Kilde = try {
        Kilde.valueOf(responseBody.origin.toUpperCase())
    } catch (e: Exception) {
        Kilde.ALTINN_RETTIGHETER_PROXY_KLIENT
    }

    data class ProxyResponseIErrorBody(val message: String, val origin: String) {
        constructor(message: String, kilde: Kilde) : this(message, origin = kilde.verdi)
    }

    companion object {
        fun parse(body: InputStream, httpStatus: Int): ProxyResponseIError {
            val inputAsString = body.bufferedReader().use { it.readText() }
            return ProxyResponseIError(parseBody(inputAsString), httpStatus)
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

    enum class Kilde(val verdi: String) {
        ALTINN("ALTINN"),
        ALTINN_RETTIGHETER_PROXY("ALTINN_RETTIGHETER_PROXY"),
        ALTINN_RETTIGHETER_PROXY_KLIENT("ALTINN_RETTIGHETER_PROXY_KLIENT")
    }
}

