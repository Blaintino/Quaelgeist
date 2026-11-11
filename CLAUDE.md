# Quälgeist – Hinweise für Claude Code

Android-App (Kotlin, Jetpack Compose, minSdk 26, targetSdk/compileSdk 37) für hartnäckige
Erinnerungen im Stil der iOS-App „Due". Eine Erinnerung meldet sich im einstellbaren Abstand
immer wieder, bis sie abgehakt oder verschoben wird – zu einer Uhrzeit oder beim
Ankommen/Verlassen eines Orts. Nur für den privaten Gebrauch des Nutzers (Samsung Galaxy S26
Ultra, Huawei Band 11), kein Play Store. Funktionen für Nutzer: `README.md`. Offene Wünsche und
Entscheidungen: `TODO.md`. Aktueller Stand: Abschnitt „Übergabe-Notizen“ ganz unten.

## Die App auf einen Blick

| | |
| --- | --- |
| App-ID | `de.quaelgeist` (Debug `de.quaelgeist.debug`, Beta `de.quaelgeist.beta`) |
| App-Name | „Quälgeist“ (Debug „Quälgeist (Debug)“, Beta „Quälgeist Beta“) |
| Release-Tag | `quaelgeist-v<run_number>`, APK `Quaelgeist-<run_number>.apk` (Beta: `quaelgeist-beta-v…`, `Quaelgeist-Beta-….apk`) |
| Installation | GitHub Releases + [Obtainium](https://obtainium.imranr.dev/), URL `https://github.com/Blaintino/Quaelgeist`, Filter `^quaelgeist-v` (Beta: zweite App, Filter `^quaelgeist-beta-v`). Kein Token nötig (öffentliches Repo). |
| `versionCode` | `VERSION_CODE` = `github.run_number`; `versionName` = `1.0.<run_number>` |
| Signatur | Release-Keystore nur in den Repo-Secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) und lokal beim Nutzer |
| Verwandte Repos | `Blaintino/ddbd` („Denk dran“, Schnittstelle), `Blaintino/Chip` (Kalender, ruft `QUICK_ADD`), `Blaintino/FH-Stempelkarte` (Vorbild Ortserkennung), Monorepo `Blaintino/claude_code_projekte` (`android-vorlage`, Toolchain-Vorbild) |

## Aufbau des Codes

Ein Modul `app`, Paket `de.quaelgeist` (plus `de.blaintino.contract.AppContract`, identisch im
Repo `ddbd`). Dateitabelle mit Aufgaben: `README.md`, Abschnitt „Aufbau“. Grobe Schichten:

- **Daten:** `Reminder.kt` (Modell), `ReminderStore.kt` (JSON in SharedPreferences, `commit()`),
  `Place.kt`/`PlaceStore` (Orte, Ortszustand, `GeoLog`), `Settings.kt` (`AppSettings`).
- **Reine Logik, JVM-testbar (immer hier hinein, mit Test):** `Recurrence.kt`, `DueGroups.kt`,
  `OpenReminders.kt`, `QuickTimes.kt`, `Links.kt`, `GeoLogic.kt`, `PlusCode.kt`,
  `PlaceInput.kt`, `Interop.validate`/`applyUpdate`, `SettingsRules`. Tests in
  `app/src/test/java/de/quaelgeist/*Test.kt` (Zeitzone fest `Europe/Berlin`, damit
  Sommer-/Winterzeit-Fälle reproduzierbar sind).
- **System:** `Scheduler.kt` (AlarmManager), `Receivers.kt` (Alarm, Knöpfe, Neustart),
  `Notifier.kt` (Meldung, Kanal, Vibration), `Actions.kt` (Erledigt/Verschieben/Löschen –
  einziger Weg für Zustandsänderungen), `GeoWatch.kt` (Ortserkennung), `Interop.kt`
  (Denk-dran-Schnittstelle), `Reliability.kt` (Prüfliste).
- **Oberfläche (Compose):** `MainActivity.kt` + `Ui.kt` (Tabs, Editor), `NagActivity.kt`
  (Verschieben-Bildschirm), `PlacesUi.kt`, `QuickAdd.kt` (Widget mit `RemoteViews` +
  `QuickAddActivity`), `OpenLinkActivity.kt`, Einstellungen in `Settings.kt`.

## Bauen und testen

- Toolchain wie `android-vorlage`: Gradle 9.8 (Wrapper), AGP 9.4 mit eingebautem Kotlin
  (kein `org.jetbrains.kotlin.android`-Plugin, nur `org.jetbrains.kotlin.plugin.compose`),
  Versionen in `gradle/libs.versions.toml`, JDK 21, Android SDK über `ANDROID_HOME` oder
  `local.properties`.
- Vor jedem Push: `./gradlew testDebugUnitTest lintDebug assembleDebug` (genau das, plus
  `assembleRelease`, läuft im CI).
- Beta lokal: `QUAELGEIST_BETA=true ./gradlew assembleDebug`.
- Cloud-Sessions haben weder Emulator noch Handy. Getestet auf dem Gerät wird nur vom Nutzer
  (Release über Obtainium, Diagnose z. B. per `adb shell dumpsys …`). UI-Änderungen deshalb
  klein halten und im PR beschreiben, was der Nutzer am Handy prüfen soll.

## Ablauf: Branch → PR → Merge → Release

So läuft es tatsächlich seit PR #12 (2026-10-26):

1. Arbeit auf einem Feature-Branch **`claude/<name>`** – nur `main`, `next` und `claude/**`
   lösen den Workflow aus. Andere Branch-Namen (z. B. `ddbd-schnittstelle`, `ccr-…`) werden vor
   dem Merge **gar nicht** gebaut; der Workflow hat keinen `pull_request`-Trigger.
2. PR gegen `main` (Titel und Beschreibung deutsch). Der Nutzer merged selbst; Claude merged
   nicht ungefragt.
3. Push auf `main` → `.github/workflows/build-apk.yml`: Tests + Lint, signierte Release-APK,
   GitHub Release `quaelgeist-v<run_number>` (nur `main` *und* Secrets vorhanden). Ohne Secrets
   nur eine Debug-APK mit Warnung.
4. Obtainium auf dem Handy holt das Release als Update.
5. Reine Doku-Commits direkt auf `main` mit `[skip ci]` im Commit-Titel, damit kein Release
   entsteht (so geschehen bei `8428103`, `065e768`).

Beta-Weg (dokumentiert, aber seit 2026-10-25 nicht mehr benutzt, siehe Übergabe-Notizen):
Feature-PRs gegen `next` → Release `quaelgeist-beta-v…` → später PR `next` → `main`.

## Feste Entscheidungen und Toolchain-Regeln (nicht ohne Rückfrage ändern)

- Nie auf Debug-Signatur „umstellen“: Updates am Handy schlagen sonst fehl („App nicht
  installiert“).
- Release-Signatur nur über Umgebungsvariablen (`ANDROID_KEYSTORE_PATH` usw.), nie einen
  Keystore einchecken oder in einer Cloud-Session erzeugen.
- `versionCode` kommt aus `VERSION_CODE` (= `github.run_number` im CI).
- Debug-Build: App-ID `de.quaelgeist.debug`, Name über den Manifest-Platzhalter `appLabel`.
- Beta (Wunsch des Nutzers: neue Funktionen erst neben der funktionierenden App testen,
  später als Update über Quälgeist): Entwicklung auf dem Branch `next`, `main` bleibt stabil
  und Rückweg. `QUAELGEIST_BETA=true` (im CI nur auf `next`) → App-ID `de.quaelgeist.beta`,
  Name „Quälgeist Beta“, Symbol `ic_launcher_beta` (Platzhalter `appIcon`), Release
  `quaelgeist-beta-v<run_number>` (Obtainium `^quaelgeist-beta-v`), gleicher Keystore.
  Ursprünglich zielten Feature-PRs auf `next`; seit PR #12 gehen sie direkt auf `main` (ob der
  Beta-Weg bleibt, ist eine offene Frage, siehe `TODO.md`). Unabhängig davon: Jede neue Version
  läuft als Update über die echten Daten des Nutzers, deshalb neue `Reminder`-Felder immer mit
  Vorgabe und `opt…` im `ReminderStore` lesen, nie ein Feld umbenennen oder umdeuten. `taskAffinity` im Manifest über `${applicationId}`, sonst teilen sich
  Beta und stabile App Aufgaben.

## Verhalten, das erhalten bleiben muss (Entscheidungen des Nutzers, nicht ohne Rückfrage ändern)

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
- `NagActivity` (Wunsch des Nutzers, aufgeräumt 2026-10-27): ✕ oben links schließt; Verschieben
  um 10 Min / 1 Std, `QuickTimes.PRESETS` 9:30 / 17:00 (heute oder morgen, kleine Zeile
  „Heute“/„Morgen“), Morgen / Montag mit kleiner Zeile „gleiche Uhrzeit“
  (`QuickTimes.nextMonday`, Uhrzeit aus `baseAt`, heute Montag → in einer Woche), alles reines
  Snooze über `Actions.snoozeUntil`. Für alles Genauere „Im Editor bearbeiten“: startet
  `MainActivity` mit `EXTRA_EDIT_ID` (`editRequest`). Kein „Uhrzeit…“/„Anderer Tag…“ mehr.
  `SnoozeButton` ist auch der Zeitknopf der Schnelleingabe. Bei Serien steht im *Erledigt*-Knopf
  klein „wiederholt …“ (`ruleLabel`, ohne Wochenend-Zusatz; Wunsch des Nutzers, Variante C).
- `NagActivity` mit mehreren offenen Erinnerungen (Wunsch des Nutzers, Probephase – eine der
  beiden Varianten fällt evtl. wieder weg, daher getrennt schaltbar): `AUTO_ADVANCE` (nach
  Erledigt/Verschieben die nächste offene zeigen statt schließen, Hinweis „Danach noch n
  offen“) und `SHOW_OTHERS` (Liste `OtherOpen` unten, Tippen wechselt). Offen =
  `OpenReminders.others` (= `DueGroups.isOverdue`, ohne die aktuelle und ohne auf den Ort
  wartende Orts-Erinnerungen, älteste zuerst).
  Beim Wechsel gleitet die neue Erinnerung kurz von rechts herein (`AnimatedContent`, 250 ms;
  nur die angezeigte schließt den Bildschirm, `active`).
  Direkt in die Mitteilungszentrale springen geht nicht (keine öffentliche API).
- Übersicht (Tabs Wiederkehrend und Einmalig) in Abschnitte nach `fireAt` geteilt
  (`DueGroups.kt`, Tests `DueGroupsTest`): Überfällig / Heute / Morgen / Diese Woche (bis
  Sonntag) / Ab nächster Woche, leere entfallen (Wunsch des Nutzers). Überfällig =
  `fireAt <= jetzt || nagCount > 0` (nervt gerade; Snooze setzt `nagCount` zurück), dieselbe
  Regel färbt die Karte rot.
- Tippen auf eine Karte (`App.open`): überfällige (`DueGroups.isOverdue`, nicht auf den Ort
  wartend; auch ausgelöste Orts-Karten) öffnen `NagActivity` (Wunsch des Nutzers), alle
  anderen den Editor.
- Karten in der Übersicht zeigen nur `fireAt` (nächste Meldung) ohne Beschriftung, kein
  „Fällig:“ mehr (Wunsch des Nutzers). Benachrichtigung und `NagActivity` zeigen weiter den
  Termin (`baseAt`).
- Editor (`EditDialog`) ist ein `Dialog` mit Kopfzeile ✕ / Überschrift / *Speichern*, damit die
  Tastatur Speichern nicht verdeckt; Eingabetaste im Titelfeld speichert. Schnellwahl-Chips
  `QuickTimes.PRESETS` (9:30, 17:00) vor Datum/Uhrzeit. Gestaltung (Wunsch des Nutzers, in
  allen Editoren gleich): Bedienelemente zentriert, Unterüberschriften (`SectionLabel`) und
  Eingabefelder links, Hinweise (`Hint`) zentriert; Abschnitt „Erinnern“ (nicht „Nagging“)
  nur mit dem Intervall – „Max. Wiederholungen“ gibt es im Editor nicht mehr, ein alter
  `maxNags` bleibt beim Speichern erhalten; Link-Feld ganz am Ende. Karten-Schnellaktionen
  (`CardActions`) auch auf ausgelösten Orts-Karten.
- Uhrzeit-Regel (Wunsch des Nutzers): Wird im Editor Datum oder Uhrzeit gewählt (auch per
  Chip) und liegt der Zeitpunkt nicht in der Zukunft, gilt der nächste Tag zu dieser Uhrzeit (`QuickTimes.onDayOrNext`). Unangetastete
  Termine beim Bearbeiten werden nicht verschoben.
- Links (`Links.kt`, Tests `LinksTest`): optionales Feld `Reminder.link`, nur http(s)
  (Wunsch des Nutzers). Beim Speichern (Editor und Schnelleingabe) wandert ein Link aus dem
  Titel ins leere Linkfeld; besteht der Titel nur aus dem Link, bleibt er Titel. Mit Link
  hat die Meldung einen dritten Knopf „Link öffnen“ (`OpenLinkActivity`, eine Activity, weil
  Receiver ab Android 12 keine Activity starten dürfen): öffnet per `ACTION_VIEW` und hakt
  danach über `Actions.markDone` ab (bei Serien: dieser Termin), mit
  `setAuthenticationRequired(true)` wie *Erledigt*.
- Orts-Erinnerungen (Tab „Orte“, `TAB_PLACES`; Wunsch des Nutzers: Erinnerungen an
  Ereignisse ohne festen Zeitpunkt, z. B. beim Heimkommen). Nur einmalig (wiederkehrend
  bewusst zurückgestellt). `Reminder.placeId != 0`, `placeTrigger` ARRIVE/LEAVE, `placeArmed` =
  wartet (dann kein Zeit-Alarm: `Scheduler.schedule` meldet ab, `AlarmReceiver.fire` ignoriert),
  `windowStartMin`/`windowEndMin` (-1 = immer), `checkIntervalMin` (Vorgabe 15, min. 2).
  Auslösen: `Actions.firePlace` setzt `baseAt`/`fireAt` = jetzt, dann `AlarmReceiver.fire`
  (normale Nag-Kette). „Beim nächsten Ankommen/Verlassen“ in `NagActivity` →
  `Actions.rearmPlace`.
  - Orte (`Place.kt`, `PlaceStore`): Name, Koordinaten, Radius (150 m), mehrere SSIDs. Anlegen
    ohne Internet (kein `INTERNET`-Recht!): aktueller Standort, Koordinaten/Plus Code einfügen
    (`PlaceInput`, `PlusCode` – eigene OLC-Umsetzung, kurze Codes über den letzten Standort),
    Teilen über den Alias `.SharePlace` (`ACTION_SEND`, `MainActivity.sharedPlaceText`).
    Google-Maps-Kurzlinks sind ohne Netz nicht auflösbar (bewusst, Wunsch des Nutzers).
  - Erkennung `GeoWatch` nach Vorbild der FH-Stempelkarte (Repo `Blaintino/FH-Stempelkarte`,
    dort zuverlässig): `addProximityAlert` je Ort, `requestLocationUpdates` (PendingIntent),
    `registerNetworkCallback` (WLAN), Prüf-Alarm `setExactAndAllowWhileIdle`. Alles nur für
    Orte mit wartender Erinnerung im Zeitfenster (`replan`, auch aus
    `Scheduler.rescheduleAll`). Regeln in `GeoLogic` (Tests `GeoLogicTest`): nur echte Wechsel
    lösen aus, nie aus UNKNOWN; Ort neu/nicht mehr überwacht oder geändert → UNKNOWN. WLAN des
    Orts = immer „da“; „weg“ erst bei Abstand − Genauigkeit > Radius + 50 m; „da“ nur bei
    Genauigkeit ≤ 250 m. Geofence-„verlassen“ löst nur eine neue Messung aus. Zuletzt bekannter
    Standort: die neueste Messung, nicht die genaueste. Alles ins `GeoLog` (Orte verwalten).
    `replan` meldet nur neu an, wenn sich die Anmeldung ändert (`PlaceStore.registration`:
    Orte/Radius/WLANs/Intervall/Anbieter) oder Android sie verworfen hat (PendingIntent per
    `FLAG_NO_CREATE` weg, z. B. nach Neustart/„Stopp erzwingen“) – jede Anmeldung liefert sofort
    Standort und WLAN-Stand und füllte sonst bei jedem App-Start das Protokoll (vom Nutzer
    bemerkt). Ein geplanter Prüf-Alarm (`PlaceStore.tickAt`) bleibt stehen statt bei jedem
    Aufruf nach hinten zu rutschen.
  - Prüfliste: Standort genau / „Immer erlauben“ / Standortdienst nur, wenn es offene
    Orts-Erinnerungen gibt (`Check.permission` fragt Rechte direkt an).
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
- Einstellungen (`Settings.kt`, Tests `SettingsTest`; Wunsch des Nutzers: Werte ändern ohne neue
  Version). Zahnrad → `SettingsScreen`, oben Eintrag „Zuverlässigkeit“ (Prüfliste; roter
  Hinweis auf der Hauptseite führt weiter direkt dorthin). `AppSettings` als JSON in
  SharedPreferences `settings` (`commit()`), Vorgaben = frühere feste Werte. Einstellbar: feste
  Uhrzeiten (2–4, `SettingsRules.normalizePresets`; überall statt `QuickTimes.PRESETS`, je zwei
  pro Reihe), zwei Verschieben-Dauern (Verschieben-Bildschirm, Schnelleingabe, Karten
  `CardActions`), Vorgaben für neue Erinnerungen (Erinnern-Intervall, Wochenend-Uhrzeit,
  Orte: Radius, Prüfintervall, Zeitfenster), `autoAdvance`/`showOthers` (vorher Konstanten in
  `NagActivity`), Start-Tab. Gelten nur für neu angelegte Erinnerungen. Bewusst nicht
  einstellbar: alles, was die Zuverlässigkeit trägt (Alarmart, Vibration, Kanal, Meldung).
  In Compose über `rememberSettings()` (lädt bei `Settings.version` neu).
- `BootReceiver` plant nach Neustart, App-Update und Zeitänderung alle Alarme neu.
- Tabs in `App()`: `TAB_RECURRING` (offen, `repeatType != NONE`), `TAB_ONCE` (offen,
  einmalig), `TAB_ARCHIVE` (`done`). Start-Tab beim Öffnen aus den Einstellungen (Vorgabe `TAB_ONCE`, Wunsch des Nutzers); dazu `TAB_PLACES`. `save()` wechselt in den Tab der gespeicherten
  Erinnerung; + im Tab „Wiederkehrend“ startet den Editor mit `prefillType = DAILY`.
- Archiv: erledigt = `done` (nur einmalige; Serien rücken beim Abhaken vor). `doneAt` wird in
  `Actions.markDone` gesetzt, alte Einträge haben 0 und werden im Archiv nach `baseAt`
  sortiert. „Wieder öffnen“ ist kein eigener Datenpfad, sondern der Editor mit einer Kopie
  (`done = false`, neuer Termin als `baseAt`/`fireAt`/`anchorAt`).
- Schnelleingabe (`QuickAdd.kt`): Widget mit klassischen `RemoteViews` (bewusst kein Glance,
  keine neue Abhängigkeit), Klick öffnet `QuickAddActivity` (durchsichtiges Theme
  `Theme.Quaelgeist.Translucent`, nur ein Dialog, aufgebaut wie die Editoren mit ✕ oben).
  Zeitknöpfe wie im Verschieben-Bildschirm (10 Min, 1 Std, 9:30, 17:00) speichern sofort mit
  Standardwerten; „Im Editor öffnen“ startet `MainActivity` mit `EXTRA_NEW_TITLE`
  (`singleTop`, `newTitleRequest`), die den Editor vorbefüllt öffnet. Zeitberechnung in
  `QuickTimes` (reine Funktionen, `QuickTimesTest`).
  `QuickAddActivity` ist über die Aktion `de.quaelgeist.action.QUICK_ADD` exportiert (Wunsch des
  Nutzers: Knopf „+ Quälgeist“ im Widget der Kalender-App Chip, Repo `Blaintino/Chip`). Sie darf
  deshalb keine Extras auswerten und nichts ohne Eingabe speichern.
- Schnittstelle zur Listen-App „Denk dran“ (`Interop.kt`, Spezifikation im Repo
  `Blaintino/ddbd`, `docs/spezifikation.md` Abschnitt 12): `CreateReminderReceiver` legt per
  `de.quaelgeist.action.CREATE_REMINDER` eine einmalige Erinnerung ohne Oberfläche an und meldet per
  `REMINDER_STATUS` explizit an `SOURCE_PACKAGE` zurück (`accepted`, `done`, `dismissed`,
  `rejected`). Geschützt durch die Signatur-Berechtigung `de.blaintino.permission.INTERNAL`, die
  jede beteiligte App selbst festlegt. Dabei gilt:
  - `AppContract.kt` (`de.blaintino.contract`) ist in beiden Repos identisch; nur gemeinsam ändern.
  - Idempotent über `EXTERNAL_ID` (gespeichert in `Reminder.origin`); `origin` muss beim Bearbeiten
    erhalten bleiben (Editor, Orts-Editor), sonst geht die Rückmeldung verloren.
  - Ändern: dieselbe id mit größerer `REVISION` passt eine offene Erinnerung an
    (`Interop.applyUpdate`, reine Funktion); gleicher oder kleinerer Stand ändert nichts, damit ein
    erneuter Zustellversuch kein Verschieben überschreibt. `CANCEL_REMINDER` entfernt eine offene
    Erinnerung ohne Rückmeldung (`Interop.cancel`, nur vom selben `SOURCE_PACKAGE`).
  - Jedes Extra wird in `Interop.validate` geprüft (reine Funktion, `InteropTest`); `OPEN_URI` nur
    `ddbd://`, „Liste öffnen“ hakt nicht ab.
  - `Actions.markDone` meldet `done`, `Actions.delete` bei offenen `dismissed`.
  - Senden mit `FLAG_INCLUDE_STOPPED_PACKAGES`, sonst erreicht die Rückmeldung eine gestoppte App nicht.

## Rahmenbedingungen

- Hieß früher „NagMe“ (umbenannt, damit es nicht nach der Play-Store-App „Nag Reminder“
  klingt). App-Name mit Umlaut „Quälgeist“, alles Technische ohne: Repo `Quaelgeist`,
  Paket/App-ID `de.quaelgeist`, Release-Tags `quaelgeist-v…`, APK `Quaelgeist-….apk`.
- Öffentliches Repo, Lizenz GPL-3.0 (`LICENSE`).

- Nur lokal: keine Google-Dienste, kein Netzwerk, keine Tracker, keine Konten. Standort und
  WLAN-Name werden nur lokal ausgewertet (Orts-Erinnerungen), es gibt kein `INTERNET`-Recht.
- Keine zusätzlichen Abhängigkeiten ohne guten Grund. Aktuell nur Compose, Core-KTX,
  Activity-Compose und Coroutines.
- Oberfläche und Texte sind deutsch.
- Die App ist für den privaten Gebrauch gedacht (`USE_EXACT_ALARM` ist deshalb gesetzt).
- Eine eigene Uhr-App ist nicht vorgesehen. Der Nutzer trägt ein Huawei Band 11 (kein
  Wear OS): Meldungen kommen nur über Huawei Health an, Schaltflächen gibt es dort nicht.
  Entscheidung des Nutzers (2026-10-24): Für eine Uhr wird nichts programmiert, solange die
  nächste Smartwatch nicht feststeht (Kandidaten: Pixel Watch oder Pebble). Die besprochenen
  Optionen stehen in `TODO.md`, Punkt 1.


Weitere Ideen und alles Offene stehen nummeriert in `TODO.md` (nur auf Nachfrage umsetzen).

## Gelernte Fallen

- **Kanaleinstellungen sind nach dem Anlegen fest.** Wer Ton/Vibration/Wichtigkeit eines
  Kanals ändern will, braucht eine neue Kanal-ID und muss die alte löschen (so entstand
  `nag_alert`).
- **Huawei Health spiegelt nur neue Meldungen:** `ongoing` oder ein bloßes Update kommen oft
  nicht aufs Band an, deshalb `cancel` + `notify` bei jedem Nag.
- **Vibration:** Kanal-Vibration plus eigene Vibration = doppelt; Herstellereinstellungen
  dämpfen Benachrichtigungs-Vibration, `USAGE_ALARM` nicht. Ein 1-s-Impuls wurde übersehen.
- **`apply()` statt `commit()` verliert Daten**, wenn der Receiver-Prozess direkt danach stirbt.
- **Ab Android 12 dürfen Receiver keine Activity starten** (Trampolin-Verbot) – deshalb ist
  „Link öffnen“ eine eigene `OpenLinkActivity`.
- **Rückmeldung an gestoppte Apps** (Denk dran) kommt nur mit `FLAG_INCLUDE_STOPPED_PACKAGES` an.
- **Beta und stabile App** teilen sich ohne `taskAffinity="${applicationId}"` die Aufgaben.
- **Ortserkennung:** Jede Neuanmeldung liefert sofort Standort und WLAN und füllte das Protokoll
  bei jedem App-Start – nur neu anmelden, wenn sich die Anmeldung ändert.
- **`rescheduleAll` bei jedem App-Start** ließ überfällige Erinnerungen ohne erlaubte
  Wiederholung bei jedem Öffnen klingeln (`Reminder.canNag`).
- **`QuickAddActivity` ist exportiert** (für Chip) – darf keine Extras auswerten und nichts
  ohne Eingabe speichern.
- **CI:** Debug-APKs aus dem CI sind bei jedem Lauf anders signiert (nur zum Ausprobieren).
  Obtainium ignoriert Vorabversionen, deshalb sind Beta-Releases normale Releases mit eigenem
  Tag-Präfix. Branches außerhalb von `main`/`next`/`claude/**` werden nicht gebaut. Die
  Release-Nummern springen (z. B. v46 → v49), weil auch Läufe auf `claude/**`-Branches die
  `run_number` hochzählen; das ist gewollt.
- **Cloud-Session-Branches** (`ccr-…`) starten oft auf einem alten Stand von `main`. Vor der
  Arbeit `git fetch origin main` und auf den aktuellen Stand setzen.

## Übergabe-Notizen (Stand 2026-11-11)

- **Letzter Release:** [`quaelgeist-v53`](https://github.com/Blaintino/Quaelgeist/releases/tag/quaelgeist-v53)
  vom 2026-11-09, gebaut aus `16af64f` (Merge von PR #20 „Erinnerungen von ‚Denk dran‘ ändern und
  zurückziehen“). Letzter Beta-Release: `quaelgeist-beta-v29` (2026-10-22).
- **Offene PRs:** #22 (diese Doku-Übergabe, Branch `ccr-5aa1b614-7b3ubx`) und #21 (Branch
  `claude/obtainium-quelgeist-daten-l9pvvw`), eine zeitgleich entstandene zweite Doku-Übergabe
  derselben Aufgabe. Beide ändern `CLAUDE.md`, `README.md` und `TODO.md` und schließen sich
  gegenseitig aus: nur eine mergen, die andere schließen. Offene Issues: keine.
- **Halbfertiges:** nichts bekannt. Alle Funktions-PRs #1–#20 sind gemergt und released.
- **Branches auf GitHub:**
  - `main` – stabil, Quelle aller Releases.
  - `next` – Beta-Branch, enthält nichts Eigenes mehr und steht 23 Commits hinter `main`
    (zuletzt gemergt über PR #11 am 2026-10-25).
  - `claude/nifty-faraday-wugku4`, `claude/vigilant-lamport-2afb5d` – vollständig gemergt.
  - `claude/obtainium-quelgeist-daten-l9pvvw` – Kopf von PR #21; enthält außerdem `e5a8cce`, eine
    ältere Fassung von „Verschieben-Bildschirm: weiter zur nächsten offenen Erinnerung“, die über
    `0cd28c3` (PR #12) schon in `main` ist (in PR #21 per Merge-Commit zusammengeführt).
  - `ccr-5aa1b614-7b3ubx` – diese Doku-Übergabe.
- **Offene Fragen an den Nutzer:** siehe `TODO.md`, Abschnitt „Offene Fragen“ (Beta-Weg `next`,
  Aufräumen alter Branches, Probephase im Verschieben-Bildschirm, Smartwatch-Wahl).
- **Außerhalb des Repos:** keine Artifacts, Mockups oder Dateien aus der Übergabe-Session; die
  Smartwatch-/Pebble-/AntennaPod-Besprechung steht vollständig in `TODO.md`.
