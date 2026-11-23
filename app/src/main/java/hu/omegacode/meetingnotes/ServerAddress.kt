package hu.omegacode.meetingnotes

/** An Android device's localhost is NOT the Windows computer's localhost. */
object ServerAddress {
    fun normalize(raw: String): String {
        val value = raw.trim().trimEnd('/')
        require(value.startsWith("https://", true) || value.startsWith("http://", true)) {
            "A címet http:// vagy https:// előtaggal add meg."
        }
        val url = java.net.URI(value)
        require(url.host != null && url.host.isNotBlank()) { "Hibás kiszolgálócím." }
        require(url.userInfo == null && url.query == null && url.fragment == null) {
            "A kiszolgálócím ne tartalmazzon jelszót, paramétert vagy töredéket."
        }
        require(url.path.isNullOrEmpty() || url.path == "/") {
            "Csak az API alapcímét add meg (például http://192.168.1.20:5200)."
        }
        require(url.port in -1..65535 && url.port != 0) { "Hibás portszám." }
        return value
    }

    fun localHttpWarning(url: String): String? {
        if (!url.startsWith("http://", true)) return null
        return "FIGYELEM: a HTTP titkosítatlan. Csak megbízható helyi hálózaton használd; éles szerveren HTTPS szükséges."
    }
}
