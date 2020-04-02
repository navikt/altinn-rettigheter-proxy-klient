package no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model

class Fnr() {

    lateinit var verdi: String

    constructor(verdi: String) : this() {
        if (!erGyldigFnr(verdi)) {
            throw RuntimeException("Ugyldig fødselsnummer. Må bestå av 11 tegn.")
        }
        this.verdi = verdi
    }

    companion object {
        fun erGyldigFnr(fnr: String): Boolean {
            return fnr.matches(Regex("^[0-9]{11}$"))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Fnr

        if (verdi != other.verdi) return false

        return true
    }

    override fun hashCode(): Int {
        return verdi.hashCode()
    }


}