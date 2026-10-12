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
  - Kanal `nag_alert` ohne Kanal-Vibration (neue ID, weil Kanaleinstellungen nach dem Anlegen
    fest sind; die alten Kanäle `nag` und `nag_alarm` werden gelöscht), Kategorie `ALARM`
    (durch „Nicht stören“), `VISIBILITY_PUBLIC`.
  - Kein Vollbild-Intent und `NagActivity` nicht über dem Sperrbildschirm (Wunsch des
    Nutzers: Angst vor versehentlichem Abhaken/Verschieben ohne Entsperren).
  - Meldung nicht `ongoing` und vor jedem Nag `cancel` + `notify`: Huawei Health und
    ähnliche Begleit-Apps spiegeln laufende oder nur aktualisierte Meldungen oft nicht.
  - `Notifier.buzz`: die einzige Vibration pro Meldung (Kanal vibriert nicht, sonst doppelt),
    Muster dreimal lang (`BUZZ_PATTERN`), mit `USAGE_ALARM` (läuft auch lautlos und aus dem
    Hintergrund). Ein einzelner 1-s-Impuls war zu schwach: Der Nutzer hat ihn beim Benutzen
    des Handys übersehen (per `adb shell dumpsys vibrator_manager` belegt, 2026-10-12).
  - Gleichzeitig fällige Erinnerungen: jede bekommt eine eigene Meldung (eigene ID, einzeln
    bedienbar), aber nur die erste innerhalb von 15 s klingelt und vibriert
    (`Notifier.claimAlert`), die übrigen werden mit `setSilent` gepostet.
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
- Tabs in `App()`: `TAB_RECURRING` (offen, `repeatType != NONE`), `TAB_ONCE` (offen,
  einmalig), `TAB_ARCHIVE` (`done`). `save()` wechselt in den Tab der gespeicherten
  Erinnerung; + im Tab „Wiederkehrend“ startet den Editor mit `prefillType = DAILY`.
- Archiv: erledigt = `done` (nur einmalige; Serien rücken beim Abhaken vor). `doneAt` wird in
  `Actions.markDone` gesetzt, alte Einträge haben 0 und werden im Archiv nach `baseAt`
  sortiert. „Wieder öffnen“ ist kein eigener Datenpfad, sondern der Editor mit einer Kopie
  (`done = false`, neuer Termin als `baseAt`/`fireAt`/`anchorAt`).
- Schnelleingabe (`QuickAdd.kt`): Widget mit klassischen `RemoteViews` (bewusst kein Glance,
  keine neue Abhängigkeit), Klick öffnet `QuickAddActivity` (durchsichtiges Theme
  `Theme.Quaelgeist.Translucent`, nur ein Dialog). Zeitknöpfe speichern sofort mit
  Standardwerten; „Mehr Optionen“ startet `MainActivity` mit `EXTRA_NEW_TITLE`
  (`singleTop`, `newTitleRequest`), die den Editor vorbefüllt öffnet. Zeitberechnung in
  `QuickTimes` (reine Funktionen, `QuickTimesTest`).

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
- Medikamente: erschwertes Abhaken (lange drücken/Rückfrage/QR-Code), Einnahme-Verlauf,
  Rückgängig nach versehentlichem Abhaken.
- Ruhezeiten (nachts pausieren), zwei Dringlichkeitsstufen.
