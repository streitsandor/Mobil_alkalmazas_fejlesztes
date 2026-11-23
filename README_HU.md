# GDE MeetingNotes – Android / Kotlin (natív kliens)

**Kiadás:** 2026-11-16 • 1.0.0 / Android-minimum 8.0 (API 26)  
**Kapcsolódó rendszer:** GDE MeetingNotes .NET 10 API + OmegaAI egykulcsos kiadás / HTML5 UI3.  
**Státusz:** Kotlin forrás és statikus ellenőrzés készült. Az Android APK nem lett lefordítva: a jelen feladatkörnyezetben nincs Android SDK/Gradle és a szükséges csomagok hálózati letöltése sikertelen.

## 1. Mit tud?

- E-mail-címes/jelszavas bejelentkezés a **MeetingNotesAPI**-ba, ASP.NET Identity munkamenet, CSRF-token.
- Saját megbeszélések listázása, részletes feldolgozási állapot és sorhelyzet, átirat, Qwen összesítő, beszélőszakaszok.
- Beszélőnevek kézi szerkesztése; összesítő folytatása és újragenerálása; feladatmegszakítás.
- **Végleges szerveres törlés** megerősítés után. 404/405/410 esetén **csak a telefonon történő elrejtés** külön jóváhagyással.
- Új MP3/WAV/M4A hangfájl kiválasztása Android fájlválasztóból és feltöltése; diarizáció és összesítő kapcsolók.
- Személylista, személyfelvétel és -törlés, hozzájárulással hangminták feltöltése és törlése (20 minta/személy szerverkorlát).
- API-/OmegaAI-/munkasor-/modell-/hardverállapot kijelzése.

**Jelen verzió korlátai:** a hangszakasz/hangminta-lejátszás, a streamelt Qwen tokenenkénti kijelzése, az automatikus beszélőazonosítás jóváhagyási panelje, az offline tárolás és a telefonos mikrofonfelvétel külön fejlesztést igényelnek. Az előrehaladás **kézzel frissíthető**, nem háttérértesítés. Az alkalmazás a már meglévő HTML5 weboldalt nem módosítja.

## 2. Biztonság és API-kulcs

**A telefonon NINCS `X-Api-Key`, `X-Tenant-Key`, TenantID vagy `X-Meeting-Key`.** Az egyetlen OmegaAI főkulcs továbbra is kizárólag a Windows szerveren található `C:\OmegaAI\config\api-key.txt` fájlban és a `.NET API` privát szerveroldali konfigurációjában. A mobilalkalmazás kizárólag a `.NET` végpontot hívja `HttpOnly` Identity-süti és `X-CSRF-TOKEN` segítségével. Jelszó nem kerül fájlba, SharedPreferences-be vagy forrásba. A bejelentkezés folyamatszintű: az app újraindításakor ismét be kell jelentkezni.

**HTTP (titkosítatlan) csak helyi tesztelésre.** A megadott Android manifest engedélyezi a tiszta HTTP-t a LAN-os próba érdekében. Éles, interneten elérhető szervernél csak **érvényes tanúsítványú HTTPS** címet használj; a publikus buildből célszerű eltávolítani a `usesCleartextTraffic="true"` beállítást.

## 3. Helyi Windows + telefon (LAN)

1. Windows PC-n indítsd a már telepített OmegaAI-t és a .NET MeetingNotesAPI-t.
2. Teszt a Windows PC-n: `http://localhost:5200/api/health/live` (a konfiguráció szerinti valós porttal).
3. A telefon és a Windows PC legyen ugyanazon a megbízható Wi-Fi/hálózaton. A `localhost` a telefon saját magát jelenti, **nem** a Windows PC-t.
4. A Windows PC IPv4 címét PowerShellben: `ipconfig`. Példa: `192.168.1.20`.
5. A .NET API-nak a PC hálózati IP-címén is figyelnie kell (nem csak 127.0.0.1-en), és a Windows tűzfalon a **helyi hálózatra korlátozott** portengedély szükséges. Az API-portot ne nyisd meg a nyilvános internetre.
6. A mobilalkalmazás első mezőjébe: `http://192.168.1.20:5200` (példa, saját IP/porttal). Kattints `API-kapcsolat ellenőrzése`, majd jelentkezz be.
7. Android Emulator esetén a Windows host gép címe rendszerint: `http://10.0.2.2:5200`.

Az API-cím **nem** OmegaAI-cím (`8768`) és nem Ollama (`11434`). Mindig a .NET API-t (`5200`, illetve a ténylegesen beállított portot) add meg.

## 4. Android Studio – első fordítás és APK

1. Telepíts Android Studio-t, a **JDK 17**-et (Android Studio embedded JDK megfelelő), valamint az **Android SDK Platform 35** és **Build-tools 35.0.0** csomagokat.
2. `File > Open`: válaszd azt a mappát, amelyben a `settings.gradle.kts` van.
3. A projekt Android Gradle Plugin 8.7.3 / Kotlin 2.0.21 / Gradle 8.9 verziót céloz. Az első Gradle Sync során a bővítményekhez internet szükséges.
4. `Build > Build Bundle(s) / APK(s) > Build APK(s)` vagy terminálban `gradle :app:assembleDebug`.
5. Sikeres build esetén az APK helye: `app/build/outputs/apk/debug/app-debug.apk`.
6. Másold az APK-t Android 8.0+ eszközre, és telepítsd. Ez **debug APK**, nem Google Play-re aláírt végleges kiadás.

Az archívumban van `tools/BUILD_APK.bat` és `tools/BUILD_APK.sh`; ezek **telepített Gradle** és Android SDK mellett működnek. A csomagban nincs `gradle-wrapper.jar`, ezért a `gradlew` parancs helyett a `gradle` parancs szerepel. Alternatív lehetőség az Android Studio menüje.

## 5. GitHub Actions – APK automatikus előállítása

A projekt tartalmazza a `.github/workflows/android-apk.yml` fájlt. Ha a projekt forrását egy saját GitHub-repositoryba teszed, a `Actions > Android debug APK > Run workflow` művelet debug APK-t épít és **Artifacts** formában menti. Az első futáshoz internet és elérhető Android SDK/Gradle függőségek szükségesek. A workflow **nem** helyettesíti a céleszközön végzett próbát.

## 6. Kötelező működési próba

- API-kapcsolat ellenőrzése – HTTP 200, helyes API URL.
- E-mail/jelszó belépés – profil megjelenik, nincs titkos API-kulcs a telefonon.
- Új hang feltöltése – 202/egyedi feladatazonosító, majd végül `completed`.
- Átirat és összesítő megnyitása; beszélőszakasz és kézi névhozzárendelés.
- Személylista, hozzájárulásos hangminta próba; 404 esetén a háttér API/OmegaAI verzió ellenőrzése.
- Egy saját **teszt**megbeszélés végleges törlése; utána nem jelenik meg újra.
- Régi, nem létező ID eltüntetése – csak helyileg; visszaállítás.
- Jogosultság: más felhasználó munkája ne legyen elérhető.
- HTTPS-es éles szerverkapcsolat; kilépés, újrabelépés, újraindítás.

## 7. API-hivatkozások (a tényleges 2026-11-16-i C# forrás szerint)

- `/api/auth/csrf`, `/api/auth/login`, `/api/auth/me`, `/api/auth/logout`
- `/api/meetings` GET és POST; `/api/meetings/{id}` GET és DELETE
- `/api/meetings/{id}/transcript`, `/summary`, `/segments`, `/speaker-names`
- `/api/meetings/{id}/cancel`, `/resume-summary`, `/regenerate-summary`
- `/api/people` GET/POST; `/api/people/{id}` DELETE; `/api/people/{id}/samples` GET/POST; `/api/people/{id}/samples/{sampleId}` DELETE
- `/api/health/live`, `/api/health/omegaai`, `/api/queue`, `/api/system/models`, `/api/system/hardware`

Az API-k meghatározása: a feltöltött `GDE_MeetingNotes_OmegaAI_EGYKULCS_FORRASCsomag_2026-11-16.zip` `Controllers/*.cs` fájljai. A **beszélőprofilok 404 hibáját** az Android felület sem tudja önmagában kijavítani, ha a szükséges Python/OmegaAI route hiányzik a ténylegesen futó kiszolgálóból.

## 8. Ellenőrzési státusz

Ellenőrizve: Kotlin/JVM szervercím-logika 6 tesztje, Kotlin források nyelvi/type-checkje lokális Android API-stubokkal, Gradle/Manifest/projektfájlok statikus ellenőrzése. **Nem ellenőrzött:** valódi Android SDK-fordítás, APK létrehozás, emulátor- vagy telefonos futtatás, végponttól végpontig élő szerverteszt. Ezeket sikeres APK-build és telepítés után kell igazolni.
