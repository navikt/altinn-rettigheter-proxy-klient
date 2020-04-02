package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.error

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.http.HttpStatus
import java.io.InputStream

class ProxyResponseIError(responseBody: ProxyResponseIErrorBody, val httpStatus: HttpStatus) {

    val melding: String = responseBody.message
    val kilde: Kilde = Kilde.valueOf(responseBody.origin.toUpperCase())

    data class ProxyResponseIErrorBody(val message: String, val origin: String)

    companion object {
        fun parse(body: InputStream, httpStatus: HttpStatus): ProxyResponseIError {
            val inputAsString = body.bufferedReader().use { it.readText() }
            return ProxyResponseIError(parseBody(inputAsString), httpStatus)
        }

        private fun parseBody(inputAsString: String): ProxyResponseIErrorBody {
            val mapper = jacksonObjectMapper()

            return try {
                mapper.readValue(inputAsString)
            } catch (e: Exception) {
                ProxyResponseIErrorBody("Uhåndtert feil", "ALTINN_RETTIGHETER_PROXY")
            }
        }
    }

    enum class Kilde(val verdi: String) {
        ALTINN("ALTINN"),
        PROXY("PROXY"),
        ANNET("ANNET")
    }
}

