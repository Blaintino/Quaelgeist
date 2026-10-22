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
- Beta (Wunsch des Nutzers: neue Funktionen erst neben der funktionierenden App testen,
  später als Update über Quälgeist): Entwicklung auf dem Branch `next`, `main` bleibt stabil
  und Rückweg. `QUAELGEIST_BETA=true` (im CI nur auf `next`) → App-ID `de.quaelgeist.beta`,
  Name „Quälgeist Beta“, Symbol `ic_launcher_beta` (Platzhalter `appIcon`), Release
  `quaelgeist-beta-v<run_number>` (Obtainium `^quaelgeist-beta-v`), gleicher Keystore.
  Feature-PRs zielen auf `next`. Weil `next` später als Update über `de.quaelgeist` mit
  dessen echten Daten läuft, neue `Reminder`-Felder immer mit Vorgabe und `opt…` im
  `ReminderStore` lesen. `taskAffinity` im Manifest über `${applicationId}`, sonst teilen sich
  Beta und stabile App Aufgaben.

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
- Benachrichtigung hat nur *Erledigt* und *Verschieben…* (mit Link zusätzlich *Link öffnen*); Verschieben öffnet `NagActivity`
  (Wunsch des Nutzers). Standard-Nag-Intervall 10 Minuten. *Erledigt* mit
  `setAuthenticationRequired(true)`: auf dem Sperrbildschirm erst nach dem Entsperren.
- `NagActivity`: Verschieben um 10/30 Min, 1/3 Std, Morgen, Montag (`QuickTimes.nextMonday`,
  Uhrzeit aus `baseAt`, heute Montag → in einer Woche), Uhrzeit…, Anderer Tag… (Datum, dann
  Uhrzeit; alles reines Snooze über `Actions.snoozeUntil`). „In der App bearbeiten“ startet
  `MainActivity` mit `EXTRA_EDIT_ID` (`editRequest`) und öffnet den Editor.
- Übersicht (Tabs Wiederkehrend und Einmalig) in Abschnitte nach `fireAt` geteilt
  (`DueGroups.kt`, Tests `DueGroupsTest`): Überfällig / Heute / Morgen / Diese Woche (bis
  Sonntag) / Ab nächster Woche, leere entfallen (Wunsch des Nutzers). Überfällig =
  `fireAt <= jetzt || nagCount > 0` (nervt gerade; Snooze setzt `nagCount` zurück), dieselbe
  Regel färbt die Karte rot.
- Karten in der Übersicht zeigen nur `fireAt` (nächste Meldung) ohne Beschriftung, kein
  „Fällig:“ mehr (Wunsch des Nutzers). Benachrichtigung und `NagActivity` zeigen weiter den
  Termin (`baseAt`).
- Editor (`EditDialog`) ist ein `Dialog` mit Kopfzeile ✕ / Überschrift / *Speichern*, damit die
  Tastatur Speichern nicht verdeckt; Eingabetaste im Titelfeld speichert. Schnellwahl-Chips
  `QuickTimes.PRESETS` (9:30, 17:00); dieselben Uhrzeiten als Knöpfe im Widget-Dialog
  („Heute …“/„Morgen …“, je nachdem ob schon vorbei).
- Uhrzeit-Regel (Wunsch des Nutzers): Wird im Editor Datum oder Uhrzeit gewählt (auch per
  Chip) oder beim Verschieben „Anderer Tag…“ genutzt und liegt der Zeitpunkt nicht in der
  Zukunft, gilt der nächste Tag zu dieser Uhrzeit (`QuickTimes.onDayOrNext`). Unangetastete
  Termine beim Bearbeiten werden nicht verschoben.
- Links (`Links.kt`, Tests `LinksTest`): optionales Feld `Reminder.link`, nur http(s)
  (Wunsch des Nutzers). Beim Speichern (Editor und Schnelleingabe) wandert ein Link aus dem
  Titel ins leere Linkfeld; besteht der Titel nur aus dem Link, bleibt er Titel. Mit Link
  hat die Meldung einen dritten Knopf „Link öffnen“ (`OpenLinkActivity`, eine Activity, weil
  Receiver ab Android 12 keine Activity starten dürfen): öffnet per `ACTION_VIEW` und hakt
  danach über `Actions.markDone` ab (bei Serien: dieser Termin), mit
  `setAuthenticationRequired(true)` wie *Erledigt*.
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
- „Werktags (Mo–Fr)“ ist kein eigener `RepeatType`, sondern eine Voreinstellung im Editor:
  `WEEKLY`, `repeatEvery = 1`, `weekdayMask = Recurrence.WORKDAYS`. Feiertage werden nicht
  berücksichtigt (kein Netzwerk, keine Feiertagsliste).
- Wochenend-Modus (`Reminder.weekendMode`, `weekendTimeMin`, Vorgabe 7:00): nur für
  `DAILY` und `WEEKLY` (`Recurrence.weekendApplies`). `SKIP` überspringt Sa/So in
  `Recurrence.next` (Rhythmus „alle N Tage“ läuft weiter), `OTHER_TIME` nimmt am Wochenende
  die eigene Uhrzeit (`Recurrence.timeOn`); die normale Uhrzeit bleibt in `anchorAt`.
  `alignStart` rückt einen Start am Wochenende auf Montag bzw. die Wochenend-Uhrzeit. Im
  Editor: „Aussetzen“ nur bei täglich (wöchentlich: Sa/So abwählen), Abschnitt nur, wenn die
  Regel Sa/So trifft; am Wochenende zeigt der Editor die normale Uhrzeit (`editorStart`).
  Verschieben auf Morgen/Montag nimmt `Recurrence.timeOn` des Zieltags. Nags unverändert.
- `BootReceiver` plant nach Neustart, App-Update und Zeitänderung alle Alarme neu.
- Tabs in `App()`: `TAB_RECURRING` (offen, `repeatType != NONE`), `TAB_ONCE` (offen,
  einmalig), `TAB_ARCHIVE` (`done`). Start-Tab beim Öffnen ist `TAB_ONCE` (Wunsch des Nutzers). `save()` wechselt in den Tab der gespeicherten
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
