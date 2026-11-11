# Quälgeist – TODO

Offene Wünsche, Fehler und Entscheidungen des Nutzers. Neue Punkte unten anfügen und die
Nummern nicht neu vergeben, damit Verweise (z. B. „TODO 1“) stimmen. Umgesetzt wird nur auf
Nachfrage; Hintergrund und feste Regeln stehen in `CLAUDE.md`.

Stand: 2026-11-11

## Offen

1. **Smartwatch-Anbindung – zurückgestellt (Entscheidung des Nutzers, 2026-10-24).**
   Nichts programmieren, solange die nächste Smartwatch nicht feststeht (Kandidaten: Pixel
   Watch oder Pebble). Heute: Huawei Band 11 über Huawei Health, ohne Schaltflächen.
   Besprochene Optionen für den Fall Pebble (PebbleOS ist seit 2025 Open Source, neue Uhren von
   Core Devices, SDK in C bzw. JavaScript, Infrastruktur von Rebble):
   - *Stufe 1, ohne eigenen Code:* Die Pebble-App spiegelt Android-Meldungen inklusive
     Aktions-Knöpfen. Am Gerät zu prüfen: ob *Erledigt* trotz `setAuthenticationRequired(true)`
     von der Uhr auslösbar ist (bei gesperrtem Handy vermutlich nicht), dass *Verschieben…*
     auf der Uhr nichts bringt (öffnet `NagActivity` am Handy), ob `cancel` + `notify`
     zuverlässig ankommt.
   - *Stufe 1b, kleine Änderung (angeboten, vom Nutzer nicht beauftragt):* feste
     Wearable-Aktionen „+10 Min“ / „+1 Std“ über `NotificationCompat.WearableExtender`, nur
     für Uhren; am Handy bleiben die Knöpfe gleich. Würde auch Wear OS (Pixel Watch) nützen.
   - *Stufe 2, eigene Watchapp:* Liste der fälligen Erinnerungen, Erledigt und Verschieben per
     Uhrtaste, eigenes Vibrationsmuster (`vibes_enqueue_custom_pattern`). Kommunikation über
     PebbleKit Android (AppMessage, Bluetooth, kein Netzwerk) – neue Abhängigkeit in
     Quälgeist, Aktionen müssen über `Actions.markDone`/`Actions.snoozeUntil` mit
     `baseAt`-Prüfung laufen. Watchapp als eigenes Projekt. Timeline-Pins weglassen (brauchen
     einen Webdienst, widerspricht „kein Netzwerk“).
   - *Stufe 3, Wakeup-API auf der Uhr:* verworfen (zweite Planung neben dem `Scheduler`,
     Fehlerquelle).
   - Empfehlung: erst Stufe 1 ein paar Tage im Alltag testen, dann gezielt Stufe 2.

2. **AntennaPod auf der Uhr – kein Vorhaben (nur besprochen, betrifft nicht dieses Repo).**
   Die eingebaute Musik-App der Pebble (ebenso Wear OS) steuert AntennaPod über die
   Media-Session: Play/Pause, vor/zurück, Lautstärke, Titel. Vorspulen statt nächster Episode
   über die AntennaPod-Einstellung für Hardware-Tasten (am Gerät prüfen). Mehr (Warteschlange,
   Geschwindigkeit, Sleep-Timer, Kapitel, „gespielt“) bräuchte einen Fork von AntennaPod mit
   PebbleKit, weil AntennaPod keine öffentliche Schnittstelle dafür hat. Ergebnis: kein Grund
   für eine eigene App und kein Kriterium bei der Uhrenwahl.

3. **Offene Frage: Beta-Weg über `next` behalten?** `CLAUDE.md`/`README.md` beschreiben
   Feature-PRs gegen `next` und die Beta-App `de.quaelgeist.beta`. Seit PR #12 (2026-10-26)
   gehen alle PRs direkt auf `main`; `next` steht 23 Commits zurück, letzte Beta ist
   `quaelgeist-beta-v29`. Entweder `next` wieder benutzen (vorher auf `main` bringen) oder den
   Beta-Weg aus der Doku nehmen und den Branch löschen (CI-Teil kann bleiben).

4. **Offene Frage: alte Branches löschen?** Gemergt und überflüssig: `claude/nifty-faraday-wugku4`,
   `claude/vigilant-lamport-2afb5d`. `claude/obtainium-quelgeist-daten-l9pvvw` hat nur noch den
   überholten Commit `e5a8cce` (ersetzt durch PR #12). Nur mit Zustimmung löschen.

5. **Offene Frage: Probephase im Verschieben-Bildschirm.** `autoAdvance` (nach
   Erledigt/Verschieben die nächste offene zeigen) und `showOthers` (Liste der übrigen) sind
   getrennt schaltbar, weil eine Variante evtl. wieder wegfallen soll. Entscheidung des
   Nutzers steht aus.

6. **Wiederkehrende Orts-Erinnerungen** – bewusst zurückgestellt (Orts-Erinnerungen sind
   nur einmalig).

7. **Eigenes App-Icon** statt `ic_popup_reminder` (nur auf Nachfrage).

8. **Backup/Export der Erinnerungen als JSON** (nur auf Nachfrage; ohne Netzwerk, z. B. über
   den Speicherzugriffs-Dialog).

9. **Medikamente:** erschwertes Abhaken (lange drücken / Rückfrage / QR-Code),
   Einnahme-Verlauf, Rückgängig nach versehentlichem Abhaken (nur auf Nachfrage).

10. **Ruhezeiten** (nachts pausieren) und **zwei Dringlichkeitsstufen** (nur auf Nachfrage).

11. **Feiertage bei „Werktags“** – bewusst nicht umgesetzt (keine Feiertagsliste, kein Netzwerk).
    Nur aufgreifen, wenn der Nutzer es wünscht.

## Erledigt

- [x] Kein Vollbild-Alarm, eine kräftige Vibration, gleichzeitige Erinnerungen bündeln (PR #1)
- [x] Verschieben auf Montag/anderen Tag, Übersicht ohne „Fällig“, Speichern oben (PR #2)
- [x] Übersicht in Abschnitte nach nächster Meldung (PR #3)
- [x] Wiederholung „Werktags (Mo–Fr)“ (PR #4)
- [x] Schnellwahl 9:30/17:00, vergangene Uhrzeit rückt auf den nächsten Tag (PR #5, #6)
- [x] Wochenend-Modus für tägliche und wöchentliche Erinnerungen (PR #7)
- [x] Links in Erinnerungen, Beta-Variante „Quälgeist Beta“ (PR #8)
- [x] Orts-Erinnerungen beim Ankommen/Verlassen, ruhigeres Protokoll (PR #9, #10, über `next` in PR #11)
- [x] Verschieben-Bildschirm: weiter zur nächsten offenen Erinnerung, Liste der übrigen (PR #12)
- [x] Oberfläche aufgeräumt: einheitliche Editoren, Verschieben-Bildschirm, Schnelleingabe (PR #13)
- [x] Einstellungen: feste Uhrzeiten, Verschieben-Dauern, Vorgaben, Start-Tab (PR #14)
- [x] Serien zeigen „wiederholt …“ im Erledigt-Knopf (PR #15)
- [x] Überfällige Karten öffnen den Verschieben-Bildschirm (PR #16)
- [x] Kurzer Übergang zur nächsten Erinnerung (PR #17)
- [x] Schnelleingabe für die Kalender-App Chip (`QUICK_ADD`, PR #18)
- [x] Schnittstelle für „Denk dran“: anlegen, Status zurückmelden (PR #19), ändern und zurückziehen (PR #20)
- [x] Smartwatch-/Pebble- und AntennaPod-Frage geklärt und dokumentiert (Punkte 1 und 2, 2026-10-24)
- [x] Doku auf Übergabe-Stand gebracht (2026-11-11)
