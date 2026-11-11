# Quälgeist – Hinweise für Claude Code

Android-App (Kotlin, Jetpack Compose, minSdk 26, targetSdk 37) für hartnäckige Erinnerungen
im Stil der iOS-App „Due". Eine Erinnerung meldet sich im einstellbaren Abstand immer wieder,
bis sie abgehakt oder verschoben wird – zu einer Uhrzeit oder beim Ankommen/Verlassen eines
Orts. Privates Projekt eines Nutzers (Huawei Band 11 am Handgelenk), komplett lokal.
Funktionen für Nutzer: `README.md`. Offene Wünsche und Entscheidungen: `TODO.md`.
Aktueller Stand und offene Fragen: Abschnitt „Übergabe-Notizen“ ganz unten.

## Die App in Kürze

| | Stabil | Beta | Debug (lokal) |
| --- | --- | --- | --- |
| App-ID | `de.quaelgeist` | `de.quaelgeist.beta` | `de.quaelgeist.debug` |
| Name | Quälgeist | Quälgeist Beta | Quälgeist (Debug) |
| Branch | `main` | `next` | – |
| Release-Tag | `quaelgeist-v<run_number>` | `quaelgeist-beta-v<run_number>` | – |
| APK | `Quaelgeist-<n>.apk` | `Quaelgeist-Beta-<n>.apk` | – |
| Obtainium-Filter | `^quaelgeist-v` | `^quaelgeist-beta-v` | – |

- Installation beim Nutzer: über [Obtainium](https://obtainium.imranr.dev/) mit der URL
  `https://github.com/Blaintino/Quaelgeist` und dem Filter oben (kein Token nötig, Repo ist
  öffentlich). Alternativ APK aus den GitHub-Releases.
- Die Release-Nummer ist die `github.run_number` und damit auch `versionCode`. Sie zählt jeden
  CI-Lauf (auch auf PR-Branches), Releases sind also nicht lückenlos nummeriert.
- Zusammenhang mit anderen Repos des Nutzers: `Blaintino/ddbd` („Denk dran“, Listen-App,
  Schnittstelle s. u.), `Blaintino/Chip` (Kalender-App, ruft `QUICK_ADD` auf),
  `Blaintino/FH-Stempelkarte` (Vorbild der Ortserkennung).

## Aufbau des Codes

Alles in `app/src/main/java/de/quaelgeist/`, eine Tabelle pro Datei steht in `README.md`
(„Aufbau“). Die wichtigsten Wege:

- Daten: `Reminder` (Modell) → `ReminderStore` (JSON in SharedPreferences, `commit()`).
  Orte analog `Place`/`PlaceStore`, Einstellungen `AppSettings` in `Settings.kt`.
- Zeit-Alarm: `Scheduler.schedule` (`setAlarmClock`) → `AlarmReceiver` (in `Receivers.kt`) plant
  die nächste Wiederholung und ruft `Notifier.show` + `Notifier.buzz`.
- Bedienung: Benachrichtigung (`Notifier`) → `ActionReceiver` bzw. `NagActivity`
  (Verschieben-Bildschirm) → `Actions` (Erledigt, Verschieben, Löschen, Orte).
- Terminrechnung: `Recurrence` (Serien, Wochenend-Modus), `QuickTimes` (Schnellwahl),
  `DueGroups` (Abschnitte, „überfällig“), `OpenReminders` (weitere offene).
- Oberfläche: `MainActivity` + `Ui.kt` (Tabs, Karten, Editor), `PlacesUi.kt` (Orte),
  `QuickAdd.kt` (Widget + Schnelleingabe), `Settings.kt` (Einstellungen), `Reliability.kt`
  (Prüfliste).
- Orte: `GeoWatch` (Android-Anbindung) + `GeoLogic` (reine Regeln), `PlusCode`, `PlaceInput`.
- Andere Apps: `Interop.kt` + `de/blaintino/contract/AppContract.kt` (Denk dran),
  `QuickAddActivity` (Aktion `QUICK_ADD`).
- Reine Logik liegt bewusst in eigenen Objekten ohne Android-Abhängigkeit und hat
  Unit-Tests in `app/src/test/java/de/quaelgeist/` (Zeitzone fest `Europe/Berlin`, damit
  Sommer-/Winterzeit-Fälle reproduzierbar sind). Neue Logik genauso bauen.

## Bauen und testen

- Toolchain wie `android-vorlage` (Monorepo `Claude_Code_Projekte`): Gradle 9.8, AGP 9.4 mit
  eingebautem Kotlin (kein `org.jetbrains.kotlin.android`-Plugin, nur
  `org.jetbrains.kotlin.plugin.compose`), Versionen in `gradle/libs.versions.toml`, JDK 21.
- Vor jedem Push: `./gradlew testDebugUnitTest lintDebug assembleDebug` (genau das prüft auch
  das CI). Lint hat nur bekannte, harmlose Hinweise (UseKtx, AutoboxingStateCreation,
  UnusedAttribute, NewerVersionAvailable, InlinedApi) – neue Befunde nicht einschleppen.
- Cloud-Session vorbereiten (kein Android SDK vorinstalliert): Kommandozeilen-Tools von
  `dl.google.com/android/repository/commandlinetools-linux-…_latest.zip` nach
  `/root/android-sdk/cmdline-tools/latest`, Lizenzen mit `sdkmanager --licenses` annehmen,
  `local.properties` mit `sdk.dir=/root/android-sdk` (ist in `.gitignore`). Die passende
  Plattform lädt Gradle dann selbst.
- Cloud-Sessions haben weder Emulator noch Handy: Oberfläche und Alarme lassen sich nur beim
  Nutzer prüfen. Deshalb in jeder Antwort klar sagen, was nur gebaut/getestet und was am Gerät
  noch unbestätigt ist.
- Debug-Build: App-ID `de.quaelgeist.debug`, Name über den Manifest-Platzhalter `appLabel`.
  Beta lokal mit `QUAELGEIST_BETA=true`.

## Ablauf: Branch → PR → Merge → Release

1. Auf dem Branch arbeiten, den die Session vorgibt (`claude/…`), sonst neuen anlegen. Vorher
   `git fetch` und vom aktuellen `main` (bzw. `next`, s. u.) abzweigen – auf `main` landen auch
   Änderungen aus anderen Sessions.
2. Bauen/testen (s. o.), README.md und CLAUDE.md (feste Entscheidungen) mitpflegen, committen,
   pushen. Commit-Texte deutsch.
3. PR nur anlegen, wenn der Nutzer es sagt. Bisher galt fast immer „PR erstellen und mergen“:
   dann CI auf dem **aktuellen** Head abwarten (`check_suite`-Meldungen können zu einem älteren
   Commit gehören), nur bei grün mergen (Merge-Commit; `expectedHeadSha` braucht die volle
   40-stellige SHA).
4. Der Push auf `main` baut die signierte Release-APK und legt das Release
   `quaelgeist-v<run_number>` an (Dauer ca. 3 Minuten). Dem Nutzer danach den Release-Link
   nennen – er aktualisiert über Obtainium.
5. Workflow `.github/workflows/build-apk.yml`: Tests + Lint, Signatur aus den Secrets
   `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
   `ANDROID_KEY_PASSWORD`. Releases nur auf `main`/`next` und nur mit Secrets; ohne Secrets nur
   eine Debug-APK als Artefakt mit Warnung. Nie auf Debug-Signatur „umstellen“, sonst schlagen
   Updates am Handy fehl („App nicht installiert“).
6. Release-Signatur nur über Umgebungsvariablen (`ANDROID_KEYSTORE_PATH` usw.), nie einen
   Keystore einchecken oder in einer Cloud-Session erzeugen.

Beta (Wunsch des Nutzers: neue Funktionen erst neben der funktionierenden App testen, später
als Update über Quälgeist): Entwicklung auf dem Branch `next`, `main` bleibt stabil und
Rückweg. `QUAELGEIST_BETA=true` (im CI nur auf `next`) → App-ID `de.quaelgeist.beta`, Name
„Quälgeist Beta“, Symbol `ic_launcher_beta` (Platzhalter `appIcon`), gleicher Keystore.
Weil `next` später als Update über `de.quaelgeist` mit dessen echten Daten läuft, neue
`Reminder`-Felder immer mit Vorgabe und `opt…` im `ReminderStore` lesen. `taskAffinity` im
Manifest über `${applicationId}`, sonst teilen sich Beta und stabile App Aufgaben.
Achtung: Seit PR #12 sind alle Änderungen direkt nach `main` gegangen, `next` ruht (ist
vollständig in `main` enthalten). Welcher Weg künftig gilt, ist eine offene Frage (s. u.).

## Feste Entscheidungen – nicht ohne Rückfrage ändern

Alles hier hat der Nutzer so gewollt oder es trägt die Zuverlässigkeit. Wer davon abweichen
will, fragt vorher nach.


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

## Gelernte Fallen

- Maven Central antwortet in Cloud-Sessions zeitweise mit 429 (Too Many Requests): Gradle mit
  `--max-workers=1` mehrmals wiederholen, bereits geladene Pakete bleiben im Cache.
- Einstellungen eines Benachrichtigungskanals (Vibration, Ton) sind nach dem Anlegen fest. Wer
  sie ändern muss, braucht eine neue Kanal-ID und löscht die alte (so entstand `nag_alert`).
- Kanal-Vibration plus eigene Vibration ergab doppelte Vibration; deshalb vibriert nur
  `Notifier.buzz`. Ein einzelner 1-s-Impuls war zu schwach (übersehen).
- `setSilent(true)` von NotificationCompat steckt die Meldung in eine Gruppe „silent“ – gewollt,
  damit gleichzeitige Erinnerungen nur einmal klingeln.
- Compose-`Text` mit `maxLines = 1` bricht am Leerzeichen um und schneidet ab: „Morgen 9:30“
  erschien auf schmalen Knöpfen nur als „Morgen“. Zweizeilig setzen oder `softWrap = false`.
- Direkt in die Mitteilungszentrale springen geht nicht (keine öffentliche API).
- Receiver dürfen ab Android 12 keine Activity starten (daher `OpenLinkActivity`).
- Android-Aktionen aus alten Benachrichtigungen können nach dem Weiterlaufen einer Serie
  ankommen – deshalb der `baseAt`-Abgleich in `Actions`.
- Wochenend-Modus/Zeitumstellung: Uhrzeiten immer aus `anchorAt`/`Recurrence.timeOn` holen, nie
  aus `baseAt` weiterrechnen, sonst rutschen Serien dauerhaft.
- Die Stop-Hook-Prüfung meldet „unpushed commits“, wenn der Session-Branch nur auf `main`
  zurückgesetzt wurde – dann einfach pushen (enthält keine neuen Änderungen).
- Feiertage kennt die App nicht (kein Netzwerk, keine Feiertagsliste).

## Übergabe-Notizen (Stand 2026-11-11)

- **Letzter Release:** `quaelgeist-v53` vom 2026-11-09 (Merge von PR #20, „Denk dran“ ändern und
  zurückziehen). Letzte Beta: `quaelgeist-beta-v29`; die Beta ruht, `next` ist vollständig in
  `main` enthalten.
- **Branches:** `main` = aktueller Stand, alles gemergt. Keine offenen PRs, keine Issues.
  `next`, `claude/nifty-faraday-wugku4`, `claude/vigilant-lamport-2afb5d` und
  `claude/obtainium-quelgeist-daten-l9pvvw` sind vollständig in `main` enthalten (letzterer
  trägt nur diese Doku-Änderung). Ein älterer Commit dort („weiter zur nächsten offenen
  Erinnerung“) ist durch PR #12 überholt.
- **Halbfertig/Probephase:** Die beiden Hilfen des Verschieben-Bildschirms (`autoAdvance`,
  `showOthers`) laufen beide; der Nutzer wollte nach dem Ausprobieren entscheiden, ob eine
  wegfällt. Inzwischen sind beide in den Einstellungen abschaltbar.
- **Offene Fragen an den Nutzer:**
  1. Verschieben-Bildschirm: „Weiter zur nächsten“ und/oder „Liste der übrigen“ behalten, oder
     beide als Einstellung lassen?
  2. Beta-Weg: Sollen neue Funktionen wieder über `next` (Beta) gehen, wie oben beschrieben,
     oder weiter direkt nach `main`?
  3. Zeitumstellung am 25.10.2026: Kamen tägliche Erinnerungen (auch mit Wochenend-Modus) am
     25. und 26.10. zur richtigen Uhrzeit? Am Gerät nie bestätigt.
  4. Wiederkehrende Orts-Erinnerungen (bisher bewusst zurückgestellt): noch gewünscht?
- **Artifacts/Mockups:** Für Quälgeist gibt es keine; im Chat zur Übergabe wurden keine
  erstellt. Die Entscheidung „Variante C“ (Serien zeigen „wiederholt …“ im Erledigt-Knopf)
  stammt aus einer anderen Session; das Ergebnis steht oben unter `NagActivity`.

