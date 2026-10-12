# Quälgeist

(Früher „NagMe“; Repo und Paketname schreiben sich ohne Umlaut: `Quaelgeist`, `de.quaelgeist`.)

Android-App für hartnäckige Erinnerungen im Stil von Due: Eine Erinnerung meldet sich im
einstellbaren Abstand immer wieder, bis du sie abhakst oder verschiebst. Alle Daten bleiben
lokal auf dem Gerät, es gibt keine Google-Dienste, kein Netzwerk und keine Konten.

## Funktionen

- **Nagging bis erledigt**: pro Erinnerung ein Intervall in Minuten und optional eine
  Höchstzahl an Wiederholungen (0 = unbegrenzt).
- **Wiederkehrende Regeln**: täglich, wöchentlich (mit Wochentagen), monatlich am Datum,
  monatlich am n-ten Wochentag (z. B. jeden 3. Mittwoch), jeweils „alle N" Tage/Wochen/Monate.
- **Erledigt oder Verschieben**: In der Benachrichtigung stehen *Erledigt* und
  *Verschieben…*. *Verschieben…* (und Tippen auf die Meldung) öffnet einen Bildschirm mit
  10 Min, 30 Min, 1 Std, 3 Std, Morgen und frei wählbarer Uhrzeit. Bei gesperrtem Handy
  muss vorher entsperrt werden, damit nichts versehentlich abgehakt oder verschoben wird.
- **Mehrere Erinnerungen gleichzeitig**: Jede bekommt ihre eigene Meldung und lässt sich
  einzeln abhaken oder verschieben. Klingeln und vibrieren tut aber nur die erste; die übrigen
  kommen still dazu.
- **Drei Tabs**: *Wiederkehrend* (z. B. Medikamente), *Einmalig* (Aufgaben) und *Archiv*.
  Ein neues + im Tab *Wiederkehrend* startet gleich mit täglicher Wiederholung; nach dem
  Speichern springt die App in den Tab, in dem die Erinnerung steht.
- **Archiv**: Erledigte einmalige Erinnerungen wandern in den Tab *Archiv* (mit
  Erledigt-Datum, neueste zuerst). Dort *Wieder öffnen* (mit neuem Termin), einzeln löschen
  oder *Archiv leeren*. Wiederkehrende Erinnerungen springen beim Abhaken zum nächsten
  Termin und bleiben unter *Wiederkehrend*.
- **Schnelleingabe-Widget**: Homescreen lange drücken → *Widgets* → *Quälgeist*. Ein Tipp
  öffnet ein kleines Fenster mit Tastatur: Titel tippen, dann *In 1 Std*, *Heute 18:00*,
  *Morgen 8:00* oder *Uhrzeit…* – gespeichert mit 10-Minuten-Nagging, ohne Wiederholung.
  *Mehr Optionen* öffnet den vollen Editor mit dem getippten Titel.
- **Smartwatch / Fitnessband**: Die Meldung ist so gebaut, dass Begleit-Apps wie Huawei
  Health sie weiterleiten (nicht „laufend“, jede Wiederholung als neue Meldung). Eine eigene
  Uhr-App gibt es nicht.

## Installieren und aktualisieren

Gleiches Prinzip wie `android-vorlage` im Monorepo `Claude_Code_Projekte` und der
AntennaPod-Fork: Jeder Push auf `main` baut per GitHub Actions eine signierte APK und
legt ein GitHub Release `quaelgeist-v<Laufnummer>` an; installiert und aktualisiert wird über
[Obtainium](https://obtainium.imranr.dev/).

Einmalige Einrichtung:

1. **Signatur-Secrets hinterlegen** (*Settings → Secrets and variables → Actions*),
   gleiche Namen und gleicher Keystore wie bei `android-vorlage`:
   `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
   `ANDROID_KEY_PASSWORD`. Ohne Secrets baut der Workflow nur eine Debug-APK als
   Artefakt (kein Release), über die keine Updates möglich sind.
2. **Obtainium:** App hinzufügen mit der URL dieses Repos; unter „Filter release titles
   by regular expression" `^quaelgeist-v` eintragen. Das Repo ist öffentlich, ein
   GitHub-Token ist nicht nötig. Alternativ die APK einfach unter *Releases*
   herunterladen und installieren.

## Lokal bauen

JDK 21 + Android SDK (`local.properties` mit `sdk.dir=…` oder `ANDROID_HOME`), dann:

```bash
./gradlew testDebugUnitTest   # Unit-Tests der Terminberechnung
./gradlew assembleDebug       # APK unter app/build/outputs/apk/debug/
./gradlew installDebug        # direkt aufs per USB verbundene Handy
```

Debug-Builds haben die App-ID `de.quaelgeist.debug` und heißen „Quälgeist (Debug)", damit sie
neben der per Obtainium installierten Release-Version laufen können.

Voraussetzungen fürs Handy: Android 8.0 (API 26) oder neuer.

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
| `Receivers.kt` | Alarm, Schaltflächen, Neustart |
| `Ui.kt`, `MainActivity.kt` | Oberfläche (Jetpack Compose): Tabs *Offen* / *Archiv*, Editor |
| `QuickAdd.kt` | Homescreen-Widget und Schnelleingabe-Fenster |
| `QuickTimes.kt` | Zeitvorschläge der Schnelleingabe (Tests: `QuickTimesTest.kt`) |

## Bekannte Einschränkungen

- Ob das Huawei Band 11 die Meldungen anzeigt, hängt auch von Huawei Health ab
  (Weiterleitung oft nur bei ausgeschaltetem Handy-Bildschirm). Schaltflächen gibt es auf
  dem Band nicht, abhaken geht nur am Handy.
- Monatliche Wiederholung am 29.–31. fällt in kürzeren Monaten auf den letzten Tag des
  Monats, im Folgemonat aber wieder auf das ursprüngliche Datum.
- Das Archiv räumt sich nicht selbst auf; *Archiv leeren* löscht alles darin.

## Lizenz

Copyright (C) 2026 Frederik Langner

Dieses Programm ist freie Software unter der **GNU General Public License v3.0**,
siehe [`LICENSE`](LICENSE). Es wird ohne jede Gewährleistung bereitgestellt.
