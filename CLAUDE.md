# NagMe – Hinweise für Claude Code

Android-App (Kotlin, Jetpack Compose, minSdk 26, targetSdk 34) für hartnäckige Erinnerungen
im Stil der iOS-App „Due". Eine Erinnerung meldet sich im einstellbaren Abstand immer wieder,
bis sie abgehakt oder verschoben wird. Siehe `README.md` für Funktionen und Dateiübersicht.

## Status

Der Code wurde geschrieben, aber **nie kompiliert oder getestet**. Die erste Aufgabe ist, ihn
zum Laufen zu bringen.

## Erste Aufgabe

1. Gradle-Wrapper erzeugen (liegt nur als `gradle-wrapper.properties` vor):
   `gradle wrapper --gradle-version 8.7`
2. `./gradlew assembleDebug` ausführen und alle Compile-Fehler beheben. Dabei nur das Nötige
   ändern, keine Umbauten.
3. `./gradlew lint` ausführen und echte Probleme beheben (z. B. fehlende Berechtigungsprüfungen,
   falsche PendingIntent-Flags). Reine Stil-Warnungen können bleiben.
4. Unit-Tests für `Recurrence.kt` ergänzen (`app/src/test`), denn die Terminberechnung ist die
   fehleranfälligste Stelle:
   - täglich, alle N Tage
   - wöchentlich mit Wochentagsmaske, auch alle 2 Wochen und über Monatsgrenzen
   - monatlich am Datum (29.–31. in kurzen Monaten)
   - n-ter Wochentag, inklusive „letzter" bei 5. Vorkommen
   - Sommer-/Winterzeitumstellung (Uhrzeit bleibt gleich)
   - späte Erledigung: der nächste Termin muss nach „jetzt" liegen
5. Beschreibe am Ende, was du geändert hast und was noch offen ist.

## Verhalten, das erhalten bleiben muss

- `AlarmReceiver`: zeigt die Meldung und plant die nächste Wiederholung (`nagCount`, `fireAt`).
  Nach der letzten erlaubten Meldung rücken Serien zum nächsten Termin vor, einmalige bleiben
  überfällig.
- `Actions`: Aktionen aus alten Benachrichtigungen werden ignoriert, wenn `baseAt` nicht mehr
  zum aktuellen Termin passt (sonst würde ein Abhaken einen späteren Termin überspringen).
- Snooze setzt nur `fireAt` und `nagCount` zurück, `baseAt` bleibt (die Regel der Serie
  wird aus `baseAt` abgeleitet).
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
- Monatliche Wiederholung am 29.–31. ohne Abrutschen auf den kürzeren Monatstag.
- Dritte Schaltfläche in der Benachrichtigung ersetzbar machen (z. B. „Morgen" statt „+1 Std").
