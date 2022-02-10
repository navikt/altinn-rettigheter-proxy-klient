package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable

interface Token {
        val value: String
}
data class SelvbetjeningToken(override val value: String): Token
data class TokenXToken(override val value: String): Token
data class Subject(val value: String)
data class ServiceCode(val value: String)
data class ServiceEdition(val value: String)

data class AltinnReportee(
        @JsonProperty("Name")
        val name: String,
        @JsonProperty("Type")
        val type: String,
        @JsonProperty("ParentOrganizationNumber")
        val parentOrganizationNumber: String? = null,
        @JsonProperty("OrganizationNumber")
        val organizationNumber: String?,
        @JsonProperty("OrganizationForm")
        val organizationForm: String?,
        @JsonProperty("Status")
        val status: String?,
        @JsonProperty("SocialSecurityNumber")
        val socialSecurityNumber: String?,
) : Serializable