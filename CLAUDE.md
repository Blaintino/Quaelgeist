# Quälgeist – Hinweise für Claude Code

Android-App (Kotlin, Jetpack Compose, minSdk 26, targetSdk 37) für hartnäckige Erinnerungen
im Stil der iOS-App „Due". Eine Erinnerung meldet sich im einstellbaren Abstand immer wieder,
bis sie abgehakt oder verschoben wird. Siehe `README.md` für Funktionen und Dateiübersicht.

## Status und Werkzeuge

Kompiliert, Lint ohne echte Befunde, Unit-Tests für `Recurrence.kt` in
`app/src/test/java/de/quaelgeist/RecurrenceTest.kt` (Zeitzone fest `Europe/Berlin`, damit
Sommer-/Winterzeit-Fälle reproduzierbar sind). Auf einem echten Gerät noch nicht getestet –
Cloud-Sessions haben weder Emulator noch Handy.

- Toolchain wie `android-vorlage` (Monorepo `Claude_Code_Projekte`): Gradle 9.8, AGP 9.4 mit
  eingebautem Kotlin (kein `org.jetbrains.kotlin.android`-Plugin, nur
  `org.jetbrains.kotlin.plugin.compose`), Versionen in `gradle/libs.versions.toml`, JDK 21.
- Befehle: `./gradlew testDebugUnitTest lintDebug assembleDebug`.
- `.github/workflows/build-apk.yml`: Tests + Lint, signierte Release-APK aus den Secrets
  `ANDROID_KEYSTORE_BASE64` usw.; nur auf `main` mit Secrets ein GitHub Release
  `quaelgeist-v<run_number>` (Obtainium filtert auf `^quaelgeist-v`). Ohne Secrets Debug-APK mit
  Warnung. Nie auf Debug-Signatur „umstellen", sonst schlagen Updates am Handy fehl.
- Release-Signatur nur über Umgebungsvariablen (`ANDROID_KEYSTORE_PATH` usw.), nie einen
  Keystore einchecken oder in einer Cloud-Session erzeugen.
- `versionCode` kommt aus `VERSION_CODE` (= `github.run_number` im CI).
- Debug-Build: App-ID `de.quaelgeist.debug`, Name über den Manifest-Platzhalter `appLabel`.

## Verhalten, das erhalten bleiben muss

- Zuverlässigkeit hat Vorrang vor neuen Funktionen (Wunsch des Nutzers: bei einer anderen
  Erinnerungs-App kamen Meldungen und Vibration unzuverlässig und nie aufs Huawei Band 11).
  Deshalb nicht zurückbauen ohne Rücksprache:
  - `Scheduler` nutzt `setAlarmClock` (Wecker-Alarm), Fallback nur ohne Exakt-Berechtigung.
  - Kanal `nag_alarm` (neue ID, weil Kanaleinstellungen nach dem Anlegen fest sind; der alte
    Kanal `nag` wird gelöscht), Kategorie `ALARM` (durch „Nicht stören“), Vollbild-Intent auf
    `NagActivity`, `VISIBILITY_PUBLIC`.
  - Meldung nicht `ongoing` und vor jedem Nag `cancel` + `notify`: Huawei Health und
    ähnliche Begleit-Apps spiegeln laufende oder nur aktualisierte Meldungen oft nicht.
  - `Notifier.buzz`: zusätzliche direkte Vibration mit `USAGE_ALARM` (läuft auch lautlos und
    aus dem Hintergrund).
  - `ReminderStore` schreibt mit `commit()`, nicht `apply()` (Receiver-Prozess kann sofort
    sterben).
  - `MainActivity` ruft bei jedem Start `Scheduler.rescheduleAll` auf; das überspringt
    überfällige Erinnerungen ohne erlaubte Wiederholung (`Reminder.canNag`), sonst klingeln
    sie bei jedem Öffnen.
  - `Reliability.kt`: Prüfliste mit Systemeinstellungs-Links und Test-Erinnerung; neue
    Voraussetzungen dort ergänzen.
- Benachrichtigung hat nur *Erledigt* und *Verschieben…*; Verschieben öffnet `NagActivity`
  (Wunsch des Nutzers). Standard-Nag-Intervall 10 Minuten.
- `AlarmReceiver`: plant zuerst die nächste Wiederholung (`nagCount`, `fireAt`), zeigt dann die
  Meldung.
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

- Hieß früher „NagMe“ (umbenannt, damit es nicht nach der Play-Store-App „Nag Reminder“
  klingt). App-Name mit Umlaut „Quälgeist“, alles Technische ohne: Repo `Quaelgeist`,
  Paket/App-ID `de.quaelgeist`, Release-Tags `quaelgeist-v…`, APK `Quaelgeist-….apk`.
- Öffentliches Repo, Lizenz GPL-3.0 (`LICENSE`).

- Nur lokal: keine Google-Dienste, kein Netzwerk, keine Tracker, keine Konten.
- Keine zusätzlichen Abhängigkeiten ohne guten Grund. Aktuell nur Compose, Core-KTX,
  Activity-Compose und Coroutines.
- Oberfläche und Texte sind deutsch.
- Die App ist für den privaten Gebrauch gedacht (`USE_EXACT_ALARM` ist deshalb gesetzt).
- Eine eigene Uhr-App ist nicht vorgesehen. Der Nutzer trägt ein Huawei Band 11 (kein
  Wear OS): Meldungen kommen nur über Huawei Health an, Schaltflächen gibt es dort nicht.

## Mögliche nächste Schritte (nur auf Nachfrage)

- Eigenes App-Icon statt `ic_popup_reminder`.
- Backup/Export der Erinnerungen als JSON.
