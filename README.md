# NagMe

Android-App für hartnäckige Erinnerungen im Stil von Due: Eine Erinnerung meldet sich im
einstellbaren Abstand immer wieder, bis du sie abhakst oder verschiebst. Alle Daten bleiben
lokal auf dem Gerät, es gibt keine Google-Dienste, kein Netzwerk und keine Konten.

## Funktionen

- **Nagging bis erledigt**: pro Erinnerung ein Intervall in Minuten und optional eine
  Höchstzahl an Wiederholungen (0 = unbegrenzt).
- **Wiederkehrende Regeln**: täglich, wöchentlich (mit Wochentagen), monatlich am Datum,
  monatlich am n-ten Wochentag (z. B. jeden 3. Mittwoch), jeweils „alle N" Tage/Wochen/Monate.
- **Schnelles Verschieben**: in der Benachrichtigung *Erledigt*, *+10 Min*, *+1 Std*;
  in der App zusätzlich *Morgen* (gleiche Uhrzeit).
- **Smartwatch**: Die Benachrichtigung samt Schaltflächen wird automatisch auf gekoppelte
  Wear-OS-Uhren gespiegelt, du kannst also auch dort abhaken oder verschieben. Eine eigene
  Uhr-App gibt es nicht.

## Installieren und aktualisieren

Gleiches Prinzip wie `android-vorlage` im Monorepo `Claude_Code_Projekte` und der
AntennaPod-Fork: Jeder Push auf `main` baut per GitHub Actions eine signierte APK und
legt ein GitHub Release `nagme-v<Laufnummer>` an; installiert und aktualisiert wird über
[Obtainium](https://obtainium.imranr.dev/).

Einmalige Einrichtung:

1. **Signatur-Secrets hinterlegen** (*Settings → Secrets and variables → Actions*),
   gleiche Namen und gleicher Keystore wie bei `android-vorlage`:
   `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
   `ANDROID_KEY_PASSWORD`. Ohne Secrets baut der Workflow nur eine Debug-APK als
   Artefakt (kein Release), über die keine Updates möglich sind.
2. **Obtainium:** App hinzufügen mit der URL dieses Repos; unter „Filter release titles
   by regular expression" `^nagme-v` eintragen. Solange das Repo privat ist, braucht
   Obtainium dafür einen GitHub-Token (wie bei `android-vorlage`, siehe dort
   `ANLEITUNG-LOKAL.md`).

## Lokal bauen

JDK 21 + Android SDK (`local.properties` mit `sdk.dir=…` oder `ANDROID_HOME`), dann:

```bash
./gradlew testDebugUnitTest   # Unit-Tests der Terminberechnung
./gradlew assembleDebug       # APK unter app/build/outputs/apk/debug/
./gradlew installDebug        # direkt aufs per USB verbundene Handy
```

Debug-Builds haben die App-ID `de.nagme.debug` und heißen „NagMe (Debug)", damit sie
neben der per Obtainium installierten Release-Version laufen können.

Voraussetzungen fürs Handy: Android 8.0 (API 26) oder neuer.

## Wichtig für zuverlässiges Nagging

- Benachrichtigungen erlauben (die App fragt beim ersten Start).
- In den Android-Einstellungen für die App den Akku auf **„Nicht eingeschränkt"** stellen.
  Samsung-Geräte stoppen Hintergrund-Apps sonst gern, siehe auch dontkillmyapp.com.
- Im Tiefschlaf (Doze) darf Android sehr kurze Intervalle von unter etwa 5 bis 10 Minuten
  strecken. Ein Intervall ab 5 Minuten ist verlässlich.

## Aufbau

| Datei | Aufgabe |
| --- | --- |
| `Reminder.kt` | Datenmodell |
| `Recurrence.kt` | Berechnung des nächsten Termins (Tests: `app/src/test/…/RecurrenceTest.kt`) |
| `ReminderStore.kt` | lokale Speicherung (JSON in SharedPreferences) |
| `Scheduler.kt` | Alarme über den AlarmManager |
| `Notifier.kt` | Benachrichtigung mit Schaltflächen |
| `Actions.kt` | Erledigt / Verschieben / Löschen |
| `Receivers.kt` | Alarm, Schaltflächen, Neustart |
| `Ui.kt`, `MainActivity.kt` | Oberfläche (Jetpack Compose) |

## Bekannte Einschränkungen

- Android zeigt höchstens drei Schaltflächen in der Benachrichtigung; *Morgen* ist daher nur
  in der App verfügbar.
- Monatliche Wiederholung am 29.–31. fällt in kürzeren Monaten auf den letzten Tag des
  Monats, im Folgemonat aber wieder auf das ursprüngliche Datum.
- Erledigte einmalige Erinnerungen bleiben in der Liste, bis du sie löschst.
