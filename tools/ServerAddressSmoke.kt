import hu.omegacode.meetingnotes.ServerAddress
fun main() {
    check(ServerAddress.normalize("http://10.0.2.2:5200/") == "http://10.0.2.2:5200")
    check(ServerAddress.normalize("https://meetingnotesapi.omegacode.cloud") == "https://meetingnotesapi.omegacode.cloud")
    check(runCatching { ServerAddress.normalize("localhost:5200") }.isFailure)
    check(runCatching { ServerAddress.normalize("http://example.org/evil") }.isFailure)
    check(ServerAddress.localHttpWarning("http://192.168.1.5:5200") != null)
    check(ServerAddress.localHttpWarning("https://example.org") == null)
    println("ServerAddress: 6/6 smoke tests OK")
}
