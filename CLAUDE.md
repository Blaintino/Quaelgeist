# NagMe – Hinweise für Claude Code

Android-App (Kotlin, Jetpack Compose, minSdk 26, targetSdk 37) für hartnäckige Erinnerungen
im Stil der iOS-App „Due". Eine Erinnerung meldet sich im einstellbaren Abstand immer wieder,
bis sie abgehakt oder verschoben wird. Siehe `README.md` für Funktionen und Dateiübersicht.

## Status und Werkzeuge

Kompiliert, Lint ohne echte Befunde, Unit-Tests für `Recurrence.kt` in
`app/src/test/java/de/nagme/RecurrenceTest.kt` (Zeitzone fest `Europe/Berlin`, damit
Sommer-/Winterzeit-Fälle reproduzierbar sind). Auf einem echten Gerät noch nicht getestet –
Cloud-Sessions haben weder Emulator noch Handy.

- Toolchain wie `android-vorlage` (Monorepo `Claude_Code_Projekte`): Gradle 9.8, AGP 9.4 mit
  eingebautem Kotlin (kein `org.jetbrains.kotlin.android`-Plugin, nur
  `org.jetbrains.kotlin.plugin.compose`), Versionen in `gradle/libs.versions.toml`, JDK 21.
- Befehle: `./gradlew testDebugUnitTest lintDebug assembleDebug`.
- `.github/workflows/build-apk.yml`: Tests + Lint, signierte Release-APK aus den Secrets
  `ANDROID_KEYSTORE_BASE64` usw.; nur auf `main` mit Secrets ein GitHub Release
  `nagme-v<run_number>` (Obtainium filtert auf `^nagme-v`). Ohne Secrets Debug-APK mit
  Warnung. Nie auf Debug-Signatur „umstellen", sonst schlagen Updates am Handy fehl.
- Release-Signatur nur über Umgebungsvariablen (`ANDROID_KEYSTORE_PATH` usw.), nie einen
  Keystore einchecken oder in einer Cloud-Session erzeugen.
- `versionCode` kommt aus `VERSION_CODE` (= `github.run_number` im CI).
- Debug-Build: App-ID `de.nagme.debug`, Name über den Manifest-Platzhalter `appLabel`.

## Verhalten, das erhalten bleiben muss

- `AlarmReceiver`: zeigt die Meldung und plant die nächste Wiederholung (`nagCount`, `fireAt`).
  Nach der letzten erlaubten Meldung rücken Serien zum nächsten Termin vor, einmalige bleiben
  überfällig.
- `Actions`: Aktionen aus alten Benachrichtigungen werden ignoriert, wenn `baseAt` nicht mehr
  zum aktuellen Termin passt (sonst würde ein Abhaken einen späteren Termin überspringen).
- Snooze setzt nur `fireAt` und `nagCount` zurück, `baseAt` bleibt.
- `Recurrence.next` rechnet ab dem Kalenderdatum von `baseAt`, nimmt Uhrzeit, Tag im Monat
  und „n-ter Wochentag" aber immer aus `anchorAt` (erster Termin der Serie). Damit rutscht
  „am 31." nach einem kurzen Monat nicht dauerhaft auf den 30., „5. Donnerstag = letzter"
  nicht auf „4. Donnerstag" und 02:30 nach der Zeitumstellung nicht auf 03:30. Beim
  Bearbeiten bleibt `anchorAt` erhalten, solange Termin und Wiederholungsart gleich bleiben.
  Alte Daten ohne `anchorAt` übernehmen `baseAt`.
- Wöchentlich mit Wochentagsauswahl: beim Speichern verschiebt `Recurrence.alignStart` den
  Starttermin auf den ersten ausgewählten Tag, falls der gewählte Starttag selbst nicht
  dabei ist.
- `BootReceiver` plant nach Neustart, App-Update und Zeitänderung alle Alarme neu.

## Rahmenbedingungen

- Nur lokal: keine Google-Dienste, kein Netzwerk, keine Tracker, keine Konten.
- Keine zusätzlichen Abhängigkeiten ohne guten Grund. Aktuell nur Compose, Core-KTX,
  Activity-Compose und Coroutines.
- Oberfläche und Texte sind deutsch.
- Die App ist für den privaten Gebrauch gedacht (`USE_EXACT_ALARM` ist deshalb gesetzt).
- Eine eigene Wear-OS-App ist nicht vorgesehen: Die Benachrichtigung samt Schaltflächen wird
  automatisch auf die Uhr gespiegelt.

## Mögliche nächste Schritte (nur auf Nachfrage)

- Eigenes App-Icon statt `ic_popup_reminder`.
- Hinweis in der App, wenn die Akku-Optimierung das Nagging stören könnte.
- Backup/Export der Erinnerungen als JSON.
- Dritte Schaltfläche in der Benachrichtigung ersetzbar machen (z. B. „Morgen" statt „+1 Std").
