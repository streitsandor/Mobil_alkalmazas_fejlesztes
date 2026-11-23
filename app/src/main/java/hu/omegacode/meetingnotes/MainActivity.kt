package hu.omegacode.meetingnotes

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * GDE MeetingNotes - Kotlin natív Android-kliens.
 * A bejelentkezés a .NET API Identity-sütijével működik. A telefonnak
 * SOHA nem adjuk át az OmegaAI titkos X-Api-Key kulcsát.
 */
class MainActivity : Activity() {
    private val green = Color.rgb(33, 110, 57)
    private val dark = Color.rgb(26, 59, 41)
    private val muted = Color.rgb(98, 123, 108)
    private val surface = Color.rgb(244, 248, 244)
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("gde_meetingnotes_mobile", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var spinner: ProgressBar
    private lateinit var client: ApiClient
    private var apiAddress = "http://10.0.2.2:5200"
    private var email = ""
    private var selectedAudio: Uri? = null
    private var selectedSample: Uri? = null
    private var pickerMode = "meeting"
    private var pickerLabel: TextView? = null
    private var selectedPersonId = ""

    companion object { private const val PICK_AUDIO = 209 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = dark
        apiAddress = prefs.getString("apiAddress", "http://10.0.2.2:5200") ?: "http://10.0.2.2:5200"
        client = ApiClient(apiAddress)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(surface) }
        setContentView(root)
        showLogin()
    }

    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }

    private fun dp(px: Int): Int = (px * resources.displayMetrics.density).toInt()
    private fun layoutMargins(view: View, bottom: Int = 8): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(bottom) }
    private fun rounded(background: Int = Color.WHITE, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        setColor(background)
        if (stroke != 0) setStroke(dp(1), stroke)
    }
    private fun label(text: String, size: Float = 15f, bold: Boolean = false, color: Int = dark): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(3), dp(3), dp(3), dp(7))
    }
    private fun addLabel(parent: LinearLayout, text: String, size: Float = 15f, bold: Boolean = false, color: Int = dark): TextView =
        label(text, size, bold, color).also { parent.addView(it, layoutMargins(it)) }

    private fun field(parent: LinearLayout, title: String, value: String = "", password: Boolean = false, multi: Boolean = false): EditText {
        addLabel(parent, title, 14f, true, muted)
        return EditText(this).also { input ->
            input.setSingleLine(!multi)
            input.setText(value)
            input.textSize = 16f
            input.setPadding(dp(12), dp(8), dp(12), dp(8))
            input.background = rounded(Color.rgb(250, 252, 250), Color.rgb(219, 230, 219))
            input.inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else if (multi) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else InputType.TYPE_CLASS_TEXT
            parent.addView(input, layoutMargins(input, 12))
        }
    }
    private fun action(parent: LinearLayout, text: String, danger: Boolean = false, onClick: () -> Unit): Button {
        val view = Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 14f
            setTextColor(if (danger) Color.rgb(145, 38, 33) else green)
            background = rounded(if (danger) Color.rgb(255, 242, 241) else Color.rgb(230, 245, 229))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onClick() }
        }
        parent.addView(view, layoutMargins(view, 6))
        return view
    }
    private fun card(parent: LinearLayout): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(11))
        background = rounded(Color.WHITE, Color.rgb(224, 233, 225))
        parent.addView(this, layoutMargins(this, 12))
    }
    private fun screen(title: String, subtitle: String? = null, showNav: Boolean = true): LinearLayout {
        root.removeAllViews()
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(14), dp(17), dp(12)); setBackgroundColor(Color.WHITE) }
        addLabel(toolbar, "▣ GDE MeetingNotes", 20f, true, green)
        if (showNav) {
            val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val items = listOf("Megbeszélések" to { showMeetings() }, "Új" to { showUpload() }, "Profilok" to { showPeople() }, "Rendszer" to { showSystem() })
            for ((name, callback) in items) {
                val b = Button(this).apply { text = name; isAllCaps = false; textSize = 11f; setOnClickListener { callback() } }
                nav.addView(b, LinearLayout.LayoutParams(0, dp(49), 1f))
            }
            toolbar.addView(nav)
        }
        root.addView(toolbar)
        spinner = ProgressBar(this).apply { isIndeterminate = true; visibility = View.GONE }
        root.addView(spinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)))
        val scroller = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(18), dp(17), dp(40))
        }
        addLabel(content, title, 25f, true)
        if (subtitle != null) addLabel(content, subtitle, 14f, false, muted)
        scroller.addView(content)
        root.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return content
    }

    private fun <T> io(task: () -> T, onFailure: ((Throwable) -> Unit)? = null, onSuccess: (T) -> Unit) {
        spinner.visibility = View.VISIBLE
        executor.execute {
            val result = runCatching { task() }
            runOnUiThread {
                spinner.visibility = View.GONE
                result.onSuccess(onSuccess).onFailure { error ->
                    if (onFailure != null) { onFailure(error); return@onFailure }
                    val text = if (error is ApiException && error.code == 404) {
                        "HTTP 404 – a kért API-végpont vagy adat nem található. Ellenőrizd a telepített egykulcsos API/OmegaAI verziót."
                    } else (error.message ?: "Ismeretlen hálózati hiba")
                    alert("Hiba", text)
                }
            }
        }
    }
    private fun alert(title: String, message: String) {
        if (!isFinishing) AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Rendben", null).show()
    }
    private fun confirm(title: String, message: String, yes: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton("Mégse", null).setPositiveButton("Igen") { _, _ -> yes() }.show()
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun saveServer(input: String): Boolean = try {
        val address = ServerAddress.normalize(input)
        client.configure(address)
        apiAddress = address
        prefs.edit().putString("apiAddress", address).apply()
        true
    } catch (e: Exception) { alert("Hibás cím", e.message ?: "Hibás API-alapcím"); false }

    private fun showLogin() {
        val content = screen("Bejelentkezés", "Közvetlen kapcsolat a .NET 10 MeetingNotesAPI-hoz – e-mail és jelszó.", false)
        val form = card(content)
        val url = field(form, "MeetingNotesAPI címe", apiAddress)
        addLabel(form, "Telefon: http://A_WINDOWS_GEP_IP_CIME:5200  •  Emulátor: http://10.0.2.2:5200", 12f, false, muted)
        val warning = ServerAddress.localHttpWarning(apiAddress)
        if (warning != null) addLabel(form, warning, 12f, false, Color.rgb(168, 73, 27))
        action(form, "API-kapcsolat ellenőrzése") {
            if (!saveServer(url.text.toString())) return@action
            io({ client.get("/health/live") }) { r -> alert("Kapcsolat", "API elérhető (HTTP ${r.status}).") }
        }
        val mail = field(form, "E-mail-cím")
        val pass = field(form, "Jelszó", password = true)
        action(form, "Belépés") {
            if (!saveServer(url.text.toString())) return@action
            val address = mail.text.toString().trim()
            val password = pass.text.toString()
            if (address.isBlank() || password.isBlank()) { alert("Hiányzó adat", "E-mail és jelszó szükséges."); return@action }
            io({
                val data = JSONObject().put("email", address).put("password", password)
                client.postJson("/auth/login", data).obj()
            }) { result ->
                pass.setText("")
                email = result.optString("email", address)
                client.clearCsrf() // Új bejelentkezéshez új antiforgery-token szükséges.
                toast("Sikeres bejelentkezés")
                showMeetings()
            }
        }
        addLabel(content, "Új felhasználót a Windowsos 09_UJ_FELHASZNALO.bat segítségével hozhatsz létre. Az OmegaAI X-Api-Key kulcsát tilos a telefonra másolni.", 13f, false, muted)
    }

    private fun hiddenKey() = "hidden.${apiAddress}.${email.lowercase()}"
    private fun hiddenJobs(): Set<String> = prefs.getStringSet(hiddenKey(), emptySet())?.toSet() ?: emptySet()
    private fun hideLocally(id: String) {
        prefs.edit().putStringSet(hiddenKey(), hiddenJobs() + id).apply()
        toast("A régi bejegyzés csak ezen a telefonon lett elrejtve")
        showMeetings()
    }
    private fun showMeetings() {
        val content = screen("Feldolgozott megbeszélések", "Saját munkák az SQLite tulajdonosi nyilvántartásból.")
        action(content, "Lista frissítése") { showMeetings() }
        if (hiddenJobs().isNotEmpty()) action(content, "Helyileg elrejtett bejegyzések megjelenítése") {
            prefs.edit().remove(hiddenKey()).apply()
            showMeetings()
        }
        val results = card(content)
        addLabel(results, "Betöltés…", 14f, false, muted)
        io({ client.get("/meetings").arr() }) { jobs ->
            results.removeAllViews()
            val hidden = hiddenJobs()
            var count = 0
            for (i in 0 until jobs.length()) {
                val job = jobs.optJSONObject(i) ?: continue
                val id = job.optString("id")
                if (id.isBlank() || hidden.contains(id)) continue
                count++
                val row = card(results)
                addLabel(row, job.optString("title", "Megbeszélés"), 16f, true)
                addLabel(row, job.optString("time") + "  ·  " + id, 12f, false, muted)
                action(row, "Megnyitás") { showJob(id) }
                action(row, "Törlés", true) { deleteJob(id) }
            }
            if (count == 0) addLabel(results, "Még nincs saját feldolgozás.", 15f, false, muted)
        }
    }

    private fun deleteJob(id: String) {
        confirm("Megbeszélés törlése", "Töröljük ezt a feldolgozást, a hozzá tartozó hangot, átiratot és összesítőt? A művelet nem vonható vissza. Futó munkát előbb meg kell szakítani.") {
            io({ client.delete("/meetings/${encode(id)}") }, { error ->
                if (error is ApiException && error.code in listOf(404, 405, 410)) {
                    confirm("Régi bejegyzés", "A szerver nem tudta törölni (HTTP ${error.code}). Csak ezen a telefonon elrejtsük? A szerver adatai ettől nem törlődnek.") {
                        hideLocally(id)
                    }
                } else alert("A törlés sikertelen", error.message ?: "Ismeretlen hiba")
            }) {
                toast("Szerveres törlés kész")
                showMeetings()
            }
        }
    }

    private fun showJob(id: String) {
        val content = screen("Megbeszélés", id)
        val state = card(content)
        addLabel(state, "Állapot lekérdezése…", 14f, false, muted)
        action(content, "Állapot frissítése") { showJob(id) }
        action(content, "Átirat") { showRemoteText("Átirat", "/meetings/${encode(id)}/transcript") }
        action(content, "Összesítő") { showRemoteText("Qwen összesítő", "/meetings/${encode(id)}/summary") }
        action(content, "Beszélőszakaszok") { showRemoteText("Beszélők", "/meetings/${encode(id)}/segments") }
        action(content, "Beszélőnevek szerkesztése") { showSpeakerNames(id) }
        action(content, "Összesítő újragenerálása") {
            confirm("Új összesítő", "A jóváhagyott beszélőnevekkel új Qwen-összesítőt készítünk?") {
                io({ client.post("/meetings/${encode(id)}/regenerate-summary") }) { toast("Újragenerálás elindult"); showJob(id) }
            }
        }
        action(content, "Összesítő folytatása") {
            io({ client.post("/meetings/${encode(id)}/resume-summary") }) {
                toast("Folytatás sorba állítva")
                showJob(id)
            }
        }
        action(content, "Feldolgozás megszakítása") {
            confirm("Megszakítás", "Biztosan megszakítod a feladatot?") {
                io({ client.post("/meetings/${encode(id)}/cancel") }) { toast("Megszakítás elküldve"); showJob(id) }
            }
        }
        action(content, "Megbeszélés törlése", true) { deleteJob(id) }
        io({ client.get("/meetings/${encode(id)}").obj() }) { data ->
            state.removeAllViews()
            addLabel(state, "Státusz: ${data.optString("status", "ismeretlen")}", 18f, true)
            addLabel(state, "Fázis: ${data.optString("stage", "—")}")
            addLabel(state, "Haladás: ${data.optString("progress", "—")}%")
            val ahead = data.optInt("jobs_ahead", -1)
            if (ahead >= 0) addLabel(state, "Sorban előtted: $ahead")
            val message = data.optString("message", "")
            if (message.isNotBlank()) addLabel(state, message)
        }
    }

    private fun showSpeakerNames(jobId: String) {
        val content = screen("Beszélőnevek", "A SPEAKER_00 címkéket itt lehet kézzel névhez kötni.")
        val form = card(content)
        addLabel(form, "Beszélők betöltése…", 13f, false, muted)
        io({
            val segments = client.get("/meetings/${encode(jobId)}/segments").arr()
            val names = runCatching { client.get("/meetings/${encode(jobId)}/speaker-names").obj() }.getOrNull()
            val speakers = linkedSetOf<String>()
            for (i in 0 until segments.length()) {
                val speaker = segments.optJSONObject(i)?.optString("speaker_id", "") ?: ""
                if (speaker.isNotBlank()) speakers.add(speaker)
            }
            speakers.toList() to names
        }) { (speakers, known) ->
            form.removeAllViews()
            if (speakers.isEmpty()) {
                addLabel(form, "A feldolgozás nem tartalmaz beszélőszakaszokat.", 13f, false, muted)
                return@io
            }
            val editors = linkedMapOf<String, EditText>()
            for (speaker in speakers) editors[speaker] = field(form, speaker, known?.optString(speaker, "") ?: "")
            action(form, "Nevek mentése") {
                val names = JSONObject()
                for ((speaker, editor) in editors) {
                    val newName = editor.text.toString().trim()
                    if (newName.isNotEmpty()) names.put(speaker, newName)
                }
                io({ client.putJson("/meetings/${encode(jobId)}/speaker-names", JSONObject().put("names", names)) }) {
                    toast("Beszélőnevek mentve")
                    showJob(jobId)
                }
            }
        }
    }

    private fun showRemoteText(title: String, path: String) {
        val content = screen(title)
        val view = card(content)
        addLabel(view, "Betöltés…", 14f, false, muted)
        io({ client.getText(path) }) { result ->
            view.removeAllViews()
            addLabel(view, if (result.isBlank()) "Még nincs elkészült eredmény." else result, 14f)
        }
    }

    private fun showUpload() {
        val content = screen("Új megbeszélés", "Hangfeltöltés, Whisper, beszélőszétválasztás és Qwen.")
        val form = card(content)
        val title = field(form, "Megbeszélés címe", "Megbeszélés – " + OffsetDateTime.now().toLocalDate())
        val date = field(form, "Kezdés (ISO 8601)", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        val diarize = CheckBox(this).apply { text = "Beszélők elkülönítése (pyannote)"; isChecked = true }
        val summarize = CheckBox(this).apply { text = "Magyar összesítő (Qwen)"; isChecked = true }
        form.addView(diarize)
        form.addView(summarize)
        pickerLabel = addLabel(form, selectedAudio?.let { fileName(it) } ?: "Nincs kiválasztott hangfájl.", 13f, false, muted)
        action(form, "MP3 / WAV / M4A kiválasztása") { pickAudio("meeting") }
        action(form, "Feldolgozás indítása") {
            val chosen = selectedAudio
            if (chosen == null) { alert("Hangfájl", "Előbb válassz egy hangot."); return@action }
            if (title.text.toString().isBlank()) { alert("Cím", "A cím nem lehet üres."); return@action }
            val fields = mapOf("title" to title.text.toString().trim(), "startedAt" to date.text.toString().trim(),
                "diarize" to diarize.isChecked.toString(), "summarize" to summarize.isChecked.toString())
            io({ client.upload("/meetings", fileName(chosen), contentResolver.getType(chosen) ?: "application/octet-stream", fields) {
                contentResolver.openInputStream(chosen) ?: error("A kiválasztott fájl nem olvasható.")
            }.obj() }) { response ->
                selectedAudio = null
                val id = response.optString("id", "")
                if (id.isNotBlank()) { toast("Feladat létrehozva"); showJob(id) }
                else { toast("A szerver nem adott feladatazonosítót"); showMeetings() }
            }
        }
        addLabel(content, "A 202 Accepted csak sorba vételt jelent. A telefonról később is lekérdezhető a feladat állapota.", 13f, false, muted)
    }

    private fun showPeople() {
        val content = screen("Beszélőprofilok", "Név, munkakör, szervezet és legfeljebb 20 hangminta személyenként.")
        action(content, "Személylista frissítése") { showPeople() }
        val form = card(content)
        addLabel(form, "Új személy", 18f, true)
        val name = field(form, "Teljes név")
        val org = field(form, "Szervezet")
        val pos = field(form, "Munkakör")
        val elected = field(form, "Választott tisztség")
        action(form, "Személy mentése") {
            if (name.text.toString().isBlank()) { alert("Hiányzó név", "A személy neve kötelező."); return@action }
            io({
                client.postJson("/people", JSONObject().put("name", name.text.toString().trim())
                    .put("organization", org.text.toString().trim()).put("position", pos.text.toString().trim())
                    .put("electedPosition", elected.text.toString().trim()))
            }) { toast("Személy mentve"); showPeople() }
        }
        val list = card(content)
        addLabel(list, "Személyek betöltése…", 14f, false, muted)
        io({ client.get("/people").arr() }) { persons ->
            list.removeAllViews()
            if (persons.length() == 0) addLabel(list, "Még nincs személy.", 14f, false, muted)
            for (i in 0 until persons.length()) {
                val person = persons.optJSONObject(i) ?: continue
                val id = person.optString("id")
                val personName = person.optString("name", id)
                val row = card(list)
                addLabel(row, personName, 16f, true)
                addLabel(row, listOf(person.optString("organization"), person.optString("position"))
                    .filter { it.isNotBlank() }.joinToString(" · ") + "  (${person.optInt("sample_count", 0)}/20)", 12f, false, muted)
                action(row, "Hangminták") { showSamples(id, personName) }
                action(row, "Személy és hangminták törlése", true) {
                    confirm("Végleges törlés", "Töröljük $personName személyt az összes hangmintájával? Nem vonható vissza.") {
                        io({ client.delete("/people/${encode(id)}") }) { toast("Személy törölve"); showPeople() }
                    }
                }
            }
        }
    }

    private fun showSamples(personId: String, name: String) {
        selectedPersonId = personId
        val content = screen("Hangminták – $name", "Csak jogszerű és dokumentált hozzájárulással rögzíthetők.")
        val form = card(content)
        val consent = CheckBox(this).apply { text = "A hozzájárulást ellenőriztem és dokumentáltam" }
        form.addView(consent)
        val note = field(form, "Hozzájárulás leírása (kötelező)", multi = true)
        pickerLabel = addLabel(form, selectedSample?.let { fileName(it) } ?: "Nincs hangminta kiválasztva.", 13f, false, muted)
        action(form, "Hangminta kiválasztása") { pickAudio("sample") }
        action(form, "Hangminta feltöltése") {
            val chosen = selectedSample
            if (chosen == null || !consent.isChecked || note.text.toString().trim().length < 3) {
                alert("Hiányzó hozzájárulás", "Válassz fájlt, erősítsd meg a hozzájárulást és írd le legalább 3 karakterrel.")
                return@action
            }
            val fields = mapOf("consentConfirmed" to "true", "consentNote" to note.text.toString().trim())
            io({ client.upload("/people/${encode(personId)}/samples", fileName(chosen), contentResolver.getType(chosen) ?: "application/octet-stream", fields) {
                contentResolver.openInputStream(chosen) ?: error("Nem olvasható a hangminta.")
            } }) { selectedSample = null; toast("Hangminta felvétele elindítva"); showSamples(personId, name) }
        }
        val listing = card(content)
        addLabel(listing, "Minták betöltése…", 13f, false, muted)
        io({ client.get("/people/${encode(personId)}/samples").arr() }) { samples ->
            listing.removeAllViews()
            if (samples.length() == 0) addLabel(listing, "Még nincs hangminta.", 14f, false, muted)
            for (i in 0 until samples.length()) {
                val sample = samples.optJSONObject(i) ?: continue
                val id = sample.optString("sample_id", sample.optString("id"))
                val row = card(listing)
                addLabel(row, sample.optString("filename", "Hangminta") + " • " + id, 13f, true)
                action(row, "Hangminta törlése", true) {
                    confirm("Hangminta törlése", "Biztosan törlöd ezt a hangmintát?") {
                        io({ client.delete("/people/${encode(personId)}/samples/${encode(id)}") }) {
                            toast("Hangminta törölve"); showSamples(personId, name)
                        }
                    }
                }
            }
        }
    }

    private fun showSystem() {
        val content = screen("Rendszerállapot", "MeetingNotesAPI, OmegaAI és feldolgozási munkasor.")
        action(content, "Újraellenőrzés") { showSystem() }
        val area = card(content)
        addLabel(area, "Állapot betöltése…", 14f, false, muted)
        io({
            val checks = listOf("MeetingNotesAPI" to "/health/live", "OmegaAI" to "/health/omegaai",
                "Munkasor" to "/queue", "AI-modellek" to "/system/models", "Hardver" to "/system/hardware")
            checks.map { (title, path) ->
                val result = runCatching { client.get(path) }
                title to result.fold({ it.body.take(2200) }, { "HIBA: ${it.message}" })
            }
        }) { checks ->
            area.removeAllViews()
            for ((name, result) in checks) {
                addLabel(area, name, 17f, true)
                addLabel(area, result, 12f, false, muted)
            }
        }
        action(content, "Kijelentkezés") {
            io({ client.post("/auth/logout") }) {
                client.clearSession()
                email = ""
                showLogin()
            }
        }
    }

    private fun encode(id: String): String = java.net.URLEncoder.encode(id, "UTF-8")
    private fun pickAudio(mode: String) {
        pickerMode = mode
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        startActivityForResult(intent, PICK_AUDIO)
    }
    @Deprecated("Used for the Android storage access framework without AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_AUDIO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (pickerMode == "sample") selectedSample = uri else selectedAudio = uri
        pickerLabel?.text = fileName(uri)
    }
    private fun fileName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: "hang.mp3"
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "hang.mp3"
    }
}
