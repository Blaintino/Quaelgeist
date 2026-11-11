# Quälgeist – Wünsche, Fehler, Entscheidungen

Stand 2026-11-11. Gesammelt aus den Chats mit dem Nutzer (Frederik). `[x]` = erledigt und
gemergt, `[ ]` = offen, `[-]` = vom Nutzer verworfen oder zurückgestellt. Feste Entscheidungen
zum Verhalten stehen ausführlich in `CLAUDE.md`.

## Offen

1. [ ] **Verschieben-Bildschirm: eine der beiden Hilfen streichen?** „Weiter zur nächsten
   offenen Erinnerung“ (`autoAdvance`) und „Liste der weiteren offenen“ (`showOthers`) laufen
   in der Probephase beide (Wunsch: „erstmal beides, je nachdem was ich nutze kann das andere
   weg“). Beide sind in den Einstellungen abschaltbar. Entscheidung des Nutzers steht aus.
2. [ ] **Zeitumstellung 25.10.2026 am Gerät bestätigen.** Tägliche Erinnerungen (auch mit
   Wochenend-Modus, Wochenend-Uhrzeit 7:00) sollten am 25. und 26.10. zur richtigen Uhrzeit
   gekommen sein. Unit-Tests decken es ab, Rückmeldung vom Nutzer fehlt.
3. [ ] **Beta-Weg klären.** CLAUDE.md beschreibt Feature-Entwicklung auf `next` (Beta),
   seit PR #12 ging alles direkt nach `main`. Nutzer fragen, was künftig gilt.
4. [-] **Wiederkehrende Orts-Erinnerungen** – bewusst zurückgestellt, Orts-Erinnerungen sind
   nur einmalig.
5. [-] **Eigenes App-Icon** statt `ic_popup_reminder` – „brauchen wir erstmal nicht“.
6. [-] **Backup/Export der Erinnerungen als JSON** – „brauchen wir erstmal nicht“.
7. [-] **Medikamente:** erschwertes Abhaken (lange drücken/Rückfrage/QR-Code),
   Einnahme-Verlauf, Rückgängig nach versehentlichem Abhaken – nur auf Nachfrage.
8. [-] **Ruhezeiten** (nachts pausieren), zwei Dringlichkeitsstufen – nur auf Nachfrage.

## Verworfen (nicht wieder vorschlagen ohne Anlass)

9. [-] **TickTick-Anbindung** – geprüft (Cloud-API mit OAuth vs. lokales Mitlesen der
   TickTick-Meldungen), Nutzer: „brauchen wir nicht mehr“. Hätte die Regel „nur lokal“ berührt.
10. [-] **`allowBackup` abschalten / Schutz gegen verspätete Alarme / überfällige deutlicher
    zeigen** – Vorschläge aus einer Code-Durchsicht, Nutzer: „brauche ich nicht“.
11. [-] **Direkt in die Mitteilungszentrale springen** nach dem Schließen des
    Verschieben-Bildschirms – technisch nicht sauber möglich (keine öffentliche API); ersetzt
    durch Punkt 1.
12. [-] **Feiertage bei „Werktags“** – nicht vorgesehen (kein Netzwerk, keine Feiertagsliste).

## Erledigt

13. [x] Obtainium-Einrichtung beschrieben (`^quaelgeist-v`, README).
14. [x] Kein Vollbild-Alarm; Verschieben-Bildschirm nicht über dem Sperrbildschirm (PR #1).
15. [x] *Erledigt* in der Meldung auf dem Sperrbildschirm erst nach dem Entsperren (PR #2).
16. [x] Nur eine Vibration pro Meldung (PR #1); später auf dreimal lang erhöht, weil ein
    1-s-Impuls übersehen wurde.
17. [x] Gleichzeitig fällige Erinnerungen: eigene Meldung je Erinnerung, aber nur einmal
    klingeln/vibrieren (PR #1).
18. [x] Verschieben auf „Montag“ (nächster Montag, gleiche Uhrzeit) (PR #2).
19. [x] Auf neues Datum legen: zuerst „Anderer Tag…“ + „In der App bearbeiten“ (PR #2); später
    in einer anderen Session auf „Im Editor bearbeiten“ reduziert (PR #13).
20. [x] Übersicht ohne „Fällig:“, nur Zeitpunkt der nächsten Meldung (PR #2).
21. [x] Editor: *Speichern* oben in der Kopfzeile, Eingabetaste speichert (PR #2).
22. [x] Übersicht in Abschnitte Überfällig / Heute / Morgen / Diese Woche / Ab nächster Woche;
    „überfällig“ schließt gerade nervende ein (PR #3).
23. [x] Wiederholung „Werktags (Mo–Fr)“ (PR #4).
24. [x] Schnellwahl 9:30 / 17:00 im Editor und im Widget; vergangene Uhrzeit rückt auf den
    nächsten Tag (PR #5). Später in den Einstellungen änderbar (PR #14).
25. [x] Widget-Knöpfe zeigten nur „Morgen“ (abgeschnitten) – zweizeilig (PR #6).
26. [x] Wochenend-Modus: Normal / Aussetzen / Andere Uhrzeit (Vorgabe 7:00) (PR #7).
27. [x] Verschieben-Bildschirm mit mehreren offenen: weiter zur nächsten + Liste (PR #12,
    Übergang PR #17).
28. [x] Weitere Funktionen aus anderen Sessions: Links (PR #8), Beta-Variante (PR #8),
    Orts-Erinnerungen (PR #9, #10, #11), Oberfläche vereinheitlicht (PR #13), Einstellungen
    (PR #14), „wiederholt …“ im Erledigt-Knopf (PR #15), überfällige Karten öffnen den
    Verschieben-Bildschirm (PR #16), `QUICK_ADD` für Chip (PR #18), Schnittstelle „Denk dran“
    (PR #19, #20).
