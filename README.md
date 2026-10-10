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

## Bauen

1. Android Studio (aktuelle Version) öffnen, Ordner `nagme` als Projekt wählen.
2. Beim ersten Sync lädt Android Studio Gradle 8.7 und die Abhängigkeiten selbst.
3. Handy per USB anschließen (Entwickleroptionen und USB-Debugging aktivieren) und ▶ drücken.
   Für eine installierbare Datei: *Build → Build APK(s)*.

Voraussetzungen: Android 8.0 (API 26) oder neuer, JDK 17 (bringt Android Studio mit).

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
| `Recurrence.kt` | Berechnung des nächsten Termins |
| `ReminderStore.kt` | lokale Speicherung (JSON in SharedPreferences) |
| `Scheduler.kt` | Alarme über den AlarmManager |
| `Notifier.kt` | Benachrichtigung mit Schaltflächen |
| `Actions.kt` | Erledigt / Verschieben / Löschen |
| `Receivers.kt` | Alarm, Schaltflächen, Neustart |
| `Ui.kt`, `MainActivity.kt` | Oberfläche (Jetpack Compose) |

## Bekannte Einschränkungen

- Android zeigt höchstens drei Schaltflächen in der Benachrichtigung; *Morgen* ist daher nur
  in der App verfügbar.
- Monatliche Wiederholung am 29.–31. rutscht in kürzeren Monaten auf den letzten Tag und
  bleibt danach dort.
- Erledigte einmalige Erinnerungen bleiben in der Liste, bis du sie löschst.
- Das Projekt wurde nicht kompiliert (siehe unten); Tippfehler beim ersten Build sind möglich.
