# Quälgeist – Wünsche, Fehler, Entscheidungen

Stand 2026-11-11. Gesammelt aus den Chats mit dem Nutzer. `[ ]` = offen, `[-]` = vom Nutzer
verworfen oder zurückgestellt, `[x]` = erledigt und gemergt. Neue Punkte unten im passenden
Abschnitt mit der nächsten freien Nummer anfügen; Nummern nicht neu vergeben, damit Verweise
(„TODO 1“) stimmen. Umgesetzt wird nur auf Nachfrage; feste Regeln stehen in `CLAUDE.md`.

## Offen / offene Fragen an den Nutzer

1. [-] **Smartwatch-Anbindung – zurückgestellt (Entscheidung des Nutzers, 2026-10-24).**
   Nichts programmieren, solange die nächste Smartwatch nicht feststeht (Kandidaten: Pixel
   Watch oder Pebble). Heute: Huawei Band 11 über Huawei Health, ohne Schaltflächen.
   Besprochene Optionen für den Fall Pebble (PebbleOS ist seit 2025 Open Source, neue Uhren von
   Core Devices, SDK in C bzw. JavaScript, Infrastruktur von Rebble):
   - *Stufe 1, ohne eigenen Code:* Die Pebble-App spiegelt Android-Meldungen inklusive
     Aktions-Knöpfen. Am Gerät zu prüfen: ob *Erledigt* trotz `setAuthenticationRequired(true)`
     von der Uhr auslösbar ist (bei gesperrtem Handy vermutlich nicht), dass *Verschieben…*
     auf der Uhr nichts bringt (öffnet `NagActivity` am Handy), ob `cancel` + `notify`
     zuverlässig ankommt.
   - *Stufe 1b, kleine Änderung (angeboten, nicht beauftragt):* feste Wearable-Aktionen
     „+10 Min“ / „+1 Std“ über `NotificationCompat.WearableExtender`, nur für Uhren; am Handy
     bleiben die Knöpfe gleich. Würde auch Wear OS (Pixel Watch) nützen.
   - *Stufe 2, eigene Watchapp:* Liste der fälligen Erinnerungen, Erledigt und Verschieben per
     Uhrtaste, eigenes Vibrationsmuster (`vibes_enqueue_custom_pattern`). Kommunikation über
     PebbleKit Android (AppMessage, Bluetooth, kein Netzwerk) – neue Abhängigkeit in
     Quälgeist, Aktionen müssen über `Actions.markDone`/`Actions.snoozeUntil` mit
     `baseAt`-Prüfung laufen. Watchapp als eigenes Projekt. Timeline-Pins weglassen (brauchen
     einen Webdienst, widerspricht „kein Netzwerk“).
   - *Stufe 3, Wakeup-API auf der Uhr:* verworfen (zweite Planung neben dem `Scheduler`).
   - Empfehlung: erst Stufe 1 ein paar Tage im Alltag testen, dann gezielt Stufe 2.

2. [-] **AntennaPod auf der Uhr – kein Vorhaben (nur besprochen, betrifft nicht dieses Repo).**
   Die eingebaute Musik-App der Pebble (ebenso Wear OS) steuert AntennaPod über die
   Media-Session: Play/Pause, vor/zurück, Lautstärke, Titel. Vorspulen statt nächster Episode
   über die AntennaPod-Einstellung für Hardware-Tasten (am Gerät prüfen). Mehr (Warteschlange,
   Geschwindigkeit, Sleep-Timer, Kapitel, „gespielt“) bräuchte einen Fork von AntennaPod mit
   PebbleKit, weil AntennaPod keine öffentliche Schnittstelle dafür hat. Ergebnis: kein Grund
   für eine eigene App und kein Kriterium bei der Uhrenwahl.

3. [ ] **Beta-Weg über `next` behalten?** `CLAUDE.md`/`README.md` beschreiben Feature-PRs gegen
   `next` und die Beta-App `de.quaelgeist.beta`. Seit PR #12 (2026-10-26) gehen alle PRs direkt
   auf `main`; `next` ist vollständig in `main` enthalten, letzte Beta `quaelgeist-beta-v29`.
   Entweder `next` wieder benutzen (vorher auf `main` bringen) oder den Beta-Weg aus der Doku
   nehmen.

4. [ ] **Alte Branches löschen?** Vollständig in `main` enthalten: `next`,
   `claude/nifty-faraday-wugku4`, `claude/vigilant-lamport-2afb5d`,
   `claude/obtainium-quelgeist-daten-l9pvvw` (dessen Doku-PR #21 ist in PR #22 aufgegangen),
   `ccr-5aa1b614-7b3ubx` (nach dem Merge von PR #22). Nur mit Zustimmung löschen.

5. [ ] **Verschieben-Bildschirm: eine der beiden Hilfen streichen?** „Weiter zur nächsten
   offenen Erinnerung“ (`autoAdvance`) und „Liste der weiteren offenen“ (`showOthers`) laufen
   in der Probephase beide (Wunsch: „erstmal beides, je nachdem was ich nutze kann das andere
   weg“). Beide sind in den Einstellungen abschaltbar. Entscheidung steht aus.

6. [ ] **Zeitumstellung 25.10.2026 am Gerät bestätigen.** Tägliche Erinnerungen (auch mit
   Wochenend-Modus, Wochenend-Uhrzeit 7:00) sollten am 25. und 26.10. zur richtigen Uhrzeit
   gekommen sein. Unit-Tests decken es ab, Rückmeldung vom Nutzer fehlt.

7. [-] **Wiederkehrende Orts-Erinnerungen** – bewusst zurückgestellt (Orts-Erinnerungen sind
   nur einmalig). Nachfragen, ob noch gewünscht.

8. [-] **Eigenes App-Icon** statt `ic_popup_reminder` – Nutzer: „brauchen wir erstmal nicht“.

9. [-] **Backup/Export der Erinnerungen als JSON** – Nutzer: „brauchen wir erstmal nicht“.

10. [-] **Medikamente:** erschwertes Abhaken (lange drücken/Rückfrage/QR-Code),
    Einnahme-Verlauf, Rückgängig nach versehentlichem Abhaken – nur auf Nachfrage.

11. [-] **Ruhezeiten** (nachts pausieren), **zwei Dringlichkeitsstufen** – nur auf Nachfrage.

## Verworfen (nicht wieder vorschlagen ohne Anlass)

12. [-] **TickTick-Anbindung** – geprüft (Cloud-API mit OAuth vs. lokales Mitlesen der
    TickTick-Meldungen), Nutzer: „brauchen wir nicht mehr“. Hätte die Regel „nur lokal“ berührt.
13. [-] **`allowBackup` abschalten / Schutz gegen verspätete Alarme / überfällige deutlicher
    zeigen** – Vorschläge aus einer Code-Durchsicht, Nutzer: „brauche ich nicht“.
14. [-] **Direkt in die Mitteilungszentrale springen** nach dem Schließen des
    Verschieben-Bildschirms – technisch nicht sauber möglich (keine öffentliche API); ersetzt
    durch Punkt 5.
15. [-] **Feiertage bei „Werktags“** – nicht vorgesehen (kein Netzwerk, keine Feiertagsliste).

## Erledigt

16. [x] Obtainium-Einrichtung beschrieben (`^quaelgeist-v`, README).
17. [x] Kein Vollbild-Alarm; Verschieben-Bildschirm nicht über dem Sperrbildschirm (PR #1).
18. [x] *Erledigt* in der Meldung auf dem Sperrbildschirm erst nach dem Entsperren (PR #2).
19. [x] Nur eine Vibration pro Meldung (PR #1); später auf dreimal lang erhöht, weil ein
    1-s-Impuls übersehen wurde.
20. [x] Gleichzeitig fällige Erinnerungen: eigene Meldung je Erinnerung, aber nur einmal
    klingeln/vibrieren (PR #1).
21. [x] Verschieben auf „Montag“ (nächster Montag, gleiche Uhrzeit) (PR #2).
22. [x] Auf neues Datum legen: zuerst „Anderer Tag…“ + „In der App bearbeiten“ (PR #2); später
    auf „Im Editor bearbeiten“ reduziert (PR #13).
23. [x] Übersicht ohne „Fällig:“, nur Zeitpunkt der nächsten Meldung (PR #2).
24. [x] Editor: *Speichern* oben in der Kopfzeile, Eingabetaste speichert (PR #2).
25. [x] Übersicht in Abschnitte Überfällig / Heute / Morgen / Diese Woche / Ab nächster Woche;
    „überfällig“ schließt gerade nervende ein (PR #3).
26. [x] Wiederholung „Werktags (Mo–Fr)“ (PR #4).
27. [x] Schnellwahl 9:30 / 17:00 im Editor und im Widget; vergangene Uhrzeit rückt auf den
    nächsten Tag (PR #5). Später in den Einstellungen änderbar (PR #14).
28. [x] Widget-Knöpfe zeigten nur „Morgen“ (abgeschnitten) – zweizeilig (PR #6).
29. [x] Wochenend-Modus: Normal / Aussetzen / Andere Uhrzeit (Vorgabe 7:00) (PR #7).
30. [x] Links (PR #8), Beta-Variante (PR #8), Orts-Erinnerungen (PR #9, #10, über `next` in #11).
31. [x] Verschieben-Bildschirm mit mehreren offenen: weiter zur nächsten + Liste (PR #12,
    Übergang PR #17).
32. [x] Oberfläche vereinheitlicht (PR #13), Einstellungen (PR #14), „wiederholt …“ im
    Erledigt-Knopf (PR #15, Variante C), überfällige Karten öffnen den Verschieben-Bildschirm
    (PR #16).
33. [x] `QUICK_ADD` für die Kalender-App Chip (PR #18), Schnittstelle „Denk dran“: anlegen und
    Status zurückmelden (PR #19), ändern und zurückziehen (PR #20).
34. [x] Smartwatch-/Pebble- und AntennaPod-Frage geklärt (Punkte 1 und 2, 2026-10-24).
35. [x] Doku auf Übergabe-Stand (PR #22, enthält die parallele PR #21; 2026-11-11).
