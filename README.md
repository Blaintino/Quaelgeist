# Quälgeist

(Früher „NagMe“; Repo und Paketname schreiben sich ohne Umlaut: `Quaelgeist`, `de.quaelgeist`.)

Android-App für hartnäckige Erinnerungen im Stil von Due: Eine Erinnerung meldet sich im
einstellbaren Abstand immer wieder, bis du sie abhakst oder verschiebst – zu einer Uhrzeit oder
beim Ankommen/Verlassen eines Orts. Alle Daten bleiben lokal auf dem Gerät, es gibt keine
Google-Dienste, kein Netzwerk und keine Konten.

## Funktionen

- **Erinnern bis erledigt**: pro Erinnerung ein Intervall in Minuten.
- **Wochenend-Modus** (täglich und wöchentlich mit Sa/So): am Wochenende *Normal*,
  *Aussetzen* (nur täglich) oder *Andere Uhrzeit* (Vorgabe 7:00).
- **Wiederkehrende Regeln**: täglich, werktags (Mo–Fr), wöchentlich (mit Wochentagen), monatlich am Datum,
  monatlich am n-ten Wochentag (z. B. jeden 3. Mittwoch), jeweils „alle N" Tage/Wochen/Monate.
- **Erledigt oder Verschieben**: In der Benachrichtigung stehen *Erledigt* und
  *Verschieben…*. *Verschieben…* (und Tippen auf die Meldung) öffnet einen Bildschirm mit
  10 Min, 1 Std, 9:30 und 17:00 (heute oder morgen), Morgen und Montag (jeweils zur gleichen
  Uhrzeit) sowie *Im Editor bearbeiten* für alles Genauere; ✕ oben links schließt.
  Sind noch weitere Erinnerungen offen (fällig oder nervend), zeigt der Bildschirm nach
  *Erledigt*/Verschieben gleich die nächste („Danach noch n offen“) und listet unten alle
  weiteren zum Antippen; erst wenn keine mehr offen ist, schließt er sich.
  Bei gesperrtem Handy muss vorher entsperrt werden – auch für *Erledigt* in der Meldung
  (ab Android 12) –, damit nichts versehentlich abgehakt oder verschoben wird.
- **Übersicht**: Jede Karte zeigt den Zeitpunkt der nächsten Meldung (rot, wenn überfällig).
  In *Wiederkehrend* und *Einmalig* ist die Liste in Abschnitte geteilt: *Überfällig*,
  *Heute*, *Morgen*, *Diese Woche* (bis Sonntag) und *Ab nächster Woche*; leere Abschnitte
  entfallen. Überfällig ist, was gerade nervt oder ohne Erledigung liegen geblieben ist –
  Verschobenes steht unter dem neuen Tag.
- **Editor**: *Speichern* steht oben in der Kopfzeile, damit die Tastatur es nicht verdeckt;
  die Eingabetaste im Titelfeld speichert ebenfalls. Schnellwahl *9:30* und *17:00* vor Datum
  und Uhrzeit. Liegt eine gewählte Uhrzeit an dem Tag schon zurück, rückt der Termin auf den
  nächsten Tag zu dieser Uhrzeit. Das Link-Feld steht am Ende.
- **Links**: Im Editor gibt es ein optionales Feld *Link* (nur `http(s)`, ohne Schema wird
  `https://` ergänzt). Steht beim Speichern ein Link im Titel und ist das Feld leer, wandert
  der Link ins Feld (auch in der Schnelleingabe); besteht der Titel nur aus dem Link, bleibt er
  Titel. Mit Link bekommt die Meldung einen dritten Knopf *Link öffnen*: öffnet den Link
  (Android wählt Browser bzw. passende App) und hakt die Erinnerung ab – bei Serien nur den
  aktuellen Termin. Auf dem Sperrbildschirm wie *Erledigt* erst nach dem Entsperren.
- **Orts-Erinnerungen** (Tab *Orte*, nur einmalig): „Beim Ankommen“ oder „Beim Verlassen“
  eines gespeicherten Orts, z. B. „Toilettenpapier kaufen“ beim Verlassen der Arbeit oder
  „Jacke beim Schneider abholen“, wenn der Termin nicht feststeht. Danach nervt sie wie jede
  andere; *Verschieben…* bietet zusätzlich *Beim nächsten Ankommen/Verlassen*. Pro Erinnerung
  optional ein Zeitfenster (außerhalb wird weder geprüft noch erinnert) und das Prüfintervall
  (Vorgabe 15 Min, mindestens 2).
  - **Orte** (*Orte verwalten*): Name, Radius (Vorgabe 150 m) und beliebig viele WLANs. Anlegen
    per *Aktueller Standort*, durch Einfügen von Koordinaten oder Plus Code (in Google Maps
    lange auf den Punkt drücken, Koordinaten oder Plus Code antippen = kopiert; der Knopf
    *Google Maps* öffnet die App) oder per Teilen aus einer Karten-App (*Als Ort in
    Quälgeist*). Alles ohne Internet; kurze Plus Codes („3V4M+2X Erfurt“) werden über den
    letzten eigenen Standort ergänzt (bis grob 50 km). Ein reiner `maps.app.goo.gl`-Link
    enthält keine Koordinaten – die App sagt dann, was zu tun ist.
  - **Erkennung** wie in der FH-Stempelkarte: Geofence des Systems, Standort-Updates,
    WLAN-Meldungen und ein Prüf-Alarm im Prüfintervall – der erste Hinweis zählt. Nur echte
    Wechsel lösen aus: Wer „beim Heimkommen“ zu Hause anlegt, wird erst nach Weggehen und
    Wiederkommen erinnert. Verbunden mit einem WLAN des Orts gilt immer als „da“ (ein Wechsel
    zwischen den WLANs löst nichts aus); „weg“ erst mit 50 m Abstand über den Radius hinaus.
  - **Protokoll** unten in *Orte verwalten*: jede Messung, jeder Wechsel und jede Auslösung.
- **Mehrere Erinnerungen gleichzeitig**: Jede bekommt ihre eigene Meldung und lässt sich
  einzeln abhaken oder verschieben. Klingeln und vibrieren tut aber nur die erste; die übrigen
  kommen still dazu.
- **Vier Tabs**: *Wiederkehrend* (z. B. Medikamente), *Einmalig* (Aufgaben), *Orte* und *Archiv*.
  Ein neues + im Tab *Wiederkehrend* startet gleich mit täglicher Wiederholung; nach dem
  Speichern springt die App in den Tab, in dem die Erinnerung steht.
- **Archiv**: Erledigte einmalige Erinnerungen wandern in den Tab *Archiv* (mit
  Erledigt-Datum, neueste zuerst). Dort *Wieder öffnen* (mit neuem Termin), einzeln löschen
  oder *Archiv leeren*. Wiederkehrende Erinnerungen springen beim Abhaken zum nächsten
  Termin und bleiben unter *Wiederkehrend*.
- **Schnelleingabe-Widget**: Homescreen lange drücken → *Widgets* → *Quälgeist*. Ein Tipp
  öffnet ein kleines Fenster mit Tastatur: Titel tippen, dann *10 Min*, *1 Std*, *9:30* oder
  *17:00* (jeweils heute, falls noch nicht vorbei, sonst morgen – die Beschriftung zeigt es) –
  gespeichert mit 10-Minuten-Erinnern, ohne Wiederholung. *Im Editor öffnen* öffnet den
  vollen Editor mit dem getippten Titel.
- **Einstellungen** (Zahnrad oben rechts): oben *Zuverlässigkeit* (Prüfliste und Test), dann
  feste Uhrzeiten (2–4, Vorgabe 9:30 und 17:00), die beiden Verschieben-Dauern (Vorgabe 10 Min
  und 1 Std, auch auf den Karten und in der Schnelleingabe), Vorgaben für neue Erinnerungen
  (Erinnern-Intervall, Wochenend-Uhrzeit, Radius/Prüfintervall/Zeitfenster für Orte), die
  beiden Hilfen des Verschieben-Bildschirms und der Start-Tab; *Auf Standard zurücksetzen*.
  Änderungen gelten für neue Erinnerungen, bestehende bleiben, wie sie sind.
- **Smartwatch / Fitnessband**: Die Meldung ist so gebaut, dass Begleit-Apps wie Huawei
  Health sie weiterleiten (nicht „laufend“, jede Wiederholung als neue Meldung). Eine eigene
  Uhr-App gibt es nicht.

## Installieren und aktualisieren

Voraussetzung: Android 8.0 oder neuer.

- **Direkt:** Die neueste `Quaelgeist-….apk` unter
  [Releases](https://github.com/Blaintino/Quaelgeist/releases/latest) herunterladen und
  installieren (Android fragt einmalig, ob der Browser Apps installieren darf).
- **Mit automatischen Updates über [Obtainium](https://obtainium.imranr.dev/):** App
  hinzufügen mit der URL `https://github.com/Blaintino/Quaelgeist`, unter „Filter release
  titles by regular expression“ `^quaelgeist-v` eintragen. Ein GitHub-Token ist nicht nötig.

### Beta (Branch `next`)

Neue Funktionen entstehen erst auf dem Branch `next`. Das CI baut dort die Variante
**„Quälgeist Beta“** (App-ID `de.quaelgeist.beta`, orangefarbenes Symbol, Releases
`quaelgeist-beta-v…` mit `Quaelgeist-Beta-….apk`). Sie läuft *neben* dem stabilen Quälgeist,
mit eigenen (anfangs leeren) Daten. In Obtainium als zweite App mit derselben URL und dem
Filter `^quaelgeist-beta-v` hinzufügen.

Hat sich die Beta bewährt, wird `next` in `main` gemergt: Daraus entsteht ein normales
Quälgeist-Release (`de.quaelgeist`, gleicher Schlüssel), das sich als Update über die
stabile App installiert und deren Daten übernimmt – neue Felder sind deshalb immer optional.
Die Beta danach deinstallieren. Lokal entsteht die Beta mit `QUAELGEIST_BETA=true`.

## Lokal bauen

JDK 21 + Android SDK (`local.properties` mit `sdk.dir=…` oder `ANDROID_HOME`), dann:

```bash
./gradlew testDebugUnitTest   # Unit-Tests der Terminberechnung
./gradlew assembleDebug       # APK unter app/build/outputs/apk/debug/
./gradlew installDebug        # direkt aufs per USB verbundene Handy
```

Debug-Builds haben die App-ID `de.quaelgeist.debug` und heißen „Quälgeist (Debug)", damit sie
neben der installierten Release-Version laufen können.

## Wichtig für zuverlässiges Nagging

Die App prüft das selbst: Zahnrad oben rechts → **Zuverlässigkeit**. Dort stehen alle
Punkte mit Knopf direkt in die passende Systemeinstellung, plus ein **Test**, der in
15 Sekunden eine Erinnerung auslöst und noch zweimal im Minutentakt nachhakt. Solange ein
prüfbarer Punkt fehlt, zeigt die Hauptansicht einen roten Hinweis.

Was die App von sich aus tut:

- Alarme über `setAlarmClock` (wie eine Wecker-App): pünktlich auch im Tiefschlaf, keine
  Streckung durch Android. Dafür zeigt die Statusleiste ein Wecker-Symbol, solange ein
  Termin ansteht.
- Meldung mit Kategorie *Alarm* (kommt bei „Nicht stören“ durch, solange Alarme erlaubt
  sind), höchster Priorität und sichtbar auf dem Sperrbildschirm. Bewusst kein Vollbild-Alarm,
  damit der Erledigt-/Verschieben-Bildschirm nicht ohne Entsperren bedienbar ist.
- Eine direkte Alarm-Vibration pro Meldung, dreimal lang (der Kanal selbst vibriert nicht), die
  auch im Lautlos-Modus läuft und nicht von Herstellereinstellungen für
  Benachrichtigungs-Vibration gedämpft wird.
- Jede Wiederholung wird als neue Meldung gepostet statt still aktualisiert, damit Ton,
  Vibration und Weiterleitung an die Uhr jedes Mal greifen.
- Die nächste Wiederholung wird gespeichert und geplant, *bevor* die Meldung erscheint.
- Bei jedem App-Start, nach Neustart, Update und Zeitänderung werden alle Alarme neu
  angemeldet.

Für Orts-Erinnerungen zusätzlich (erscheint in der Prüfliste, sobald es eine gibt): Standort
„Genau“ und „Immer erlauben“, Standortdienst eingeschaltet.

Was du einmalig einstellen musst (die Prüfliste führt hin): Benachrichtigungen erlauben,
Akku „Nicht eingeschränkt“, „App bei Nichtnutzung pausieren“ aus,
bei Samsung Quälgeist zu den „Nie schlafenden Apps“, und in Huawei Health die Meldungen von
Quälgeist für das Band einschalten.

## Aufbau

| Datei | Aufgabe |
| --- | --- |
| `Reminder.kt` | Datenmodell |
| `Recurrence.kt` | Berechnung des nächsten Termins (Tests: `app/src/test/…/RecurrenceTest.kt`) |
| `ReminderStore.kt` | lokale Speicherung (JSON in SharedPreferences) |
| `Scheduler.kt` | Alarme über den AlarmManager |
| `Notifier.kt` | Benachrichtigung, Kanal, direkte Alarm-Vibration |
| `NagActivity.kt` | Erledigt-/Verschieben-Bildschirm (nur nach Entsperren) |
| `Reliability.kt` | Prüfliste „Zuverlässigkeit“ und Test-Erinnerung |
| `Actions.kt` | Erledigt / Verschieben / Löschen |
| `Links.kt`, `OpenLinkActivity.kt` | Link aus dem Titel übernehmen, „Link öffnen“ (Tests: `LinksTest.kt`) |
| `Place.kt` | Orte, Zustand je Ort, Protokoll der Ortserkennung |
| `GeoWatch.kt` | Ortserkennung: Geofence, Standort, WLAN, Prüf-Alarm |
| `GeoLogic.kt` | Regeln „da/weg“, Zeitfenster, WLAN-Vergleich (Tests: `GeoLogicTest.kt`) |
| `PlusCode.kt`, `PlaceInput.kt` | Plus Codes und Koordinaten offline lesen (Tests: `PlusCodeTest.kt`, `PlaceInputTest.kt`) |
| `PlacesUi.kt` | Tab *Orte*, Editor für Orts-Erinnerungen, Orte verwalten |
| `Receivers.kt` | Alarm, Schaltflächen, Neustart |
| `Ui.kt`, `MainActivity.kt` | Oberfläche (Jetpack Compose): Tabs *Offen* / *Archiv*, Editor |
| `QuickAdd.kt` | Homescreen-Widget und Schnelleingabe-Fenster |
| `Settings.kt` | Einstellungen: Speicher, Regeln (Tests: `SettingsTest.kt`), Bildschirm |
| `QuickTimes.kt` | Zeitvorschläge der Schnelleingabe (Tests: `QuickTimesTest.kt`) |

## Bekannte Einschränkungen

- Ob das Huawei Band 11 die Meldungen anzeigt, hängt auch von Huawei Health ab
  (Weiterleitung oft nur bei ausgeschaltetem Handy-Bildschirm). Schaltflächen gibt es auf
  dem Band nicht, abhaken geht nur am Handy.
- Monatliche Wiederholung am 29.–31. fällt in kürzeren Monaten auf den letzten Tag des
  Monats, im Folgemonat aber wieder auf das ursprüngliche Datum.
- Das Archiv räumt sich nicht selbst auf; *Archiv leeren* löscht alles darin.
- Orts-Erinnerungen: Kommt nach dem Anlegen (bzw. nach Fensterbeginn) keine einzige Messung
  zustande, bevor du ankommst, ist der Ausgangszustand unbekannt und das Ankommen löst nicht
  aus. Beim Speichern wird deshalb sofort einmal gemessen. Android liefert Standort im
  Hintergrund nur wenige Male pro Stunde; ein WLAN des Orts macht vor allem das Ankommen
  schneller und das Verlassen zuverlässiger. Wiederkehrende Orts-Erinnerungen gibt es noch nicht.

## Lizenz

Copyright (C) 2026 Frederik Langner

Dieses Programm ist freie Software unter der **GNU General Public License v3.0**,
siehe [`LICENSE`](LICENSE). Es wird ohne jede Gewährleistung bereitgestellt.
