# Pixel Plumber Land

Ein Jump'n'Run für Android im Stil der klassischen Handheld-Spiele von 1989:
4-Farben-Grafik in 160×144 Pixeln, Chiptune-Musik, 4 Welten mit je 3 Leveln,
Endgegner, Bonusräume und zwei Shooter-Level.

Alle Grafiken, Level, Figuren und Melodien sind **eigene Werke** – das Spiel
ist vom Aufbau der Klassiker inspiriert, enthält aber keine fremden Inhalte.

## Installation

1. Unter **Releases** die neueste `PixelPlumberLand.apk` herunterladen
   (wird bei jedem Push auf `main` automatisch gebaut).
2. Auf dem Handy öffnen und „Installation aus unbekannten Quellen“ erlauben.

Android 5.0 oder neuer. Hochformat (wie ein Handheld) und Querformat werden unterstützt.

## Steuerung

| Touch | Gamepad | Tastatur | Funktion |
|---|---|---|---|
| Steuerkreuz | Steuerkreuz / Stick | Pfeiltasten / WASD | Laufen, ↓ auf einer Röhre = hineinsteigen |
| A | A | Leertaste / K | Springen (länger halten = höher) |
| B | B / X | J / Shift | Rennen, Feuerkugel werfen, im Shooter schießen |
| START | Start | Enter / P | Pause |
| TON | – | – | Ton an/aus |

Die Zurück-Taste pausiert das Spiel.

## Die Geschichte

Der **Sturmfürst** hat das Wolkenreich überfallen und **Königin Lumi** entführt.
Pip macht sich auf den Weg …

| Welt | Level | Boss |
|---|---|---|
| 1 – Dünental | Dünental, Pyramidenpfad, Skarabäus-Gruft | Skarabäus Rex |
| 2 – Korallensee | Korallenstrand, Hafenmole, Tiefsee (U-Boot) | Tiefseekrake |
| 3 – Felsenreich | Felsenpass, Kristallhöhle, Golemfeste | Felsgolem |
| 4 – Wolkenreich | Wolkensteg, Sturmturm, Himmelsschlacht (Flugzeug) | Sturmfürst |

**Gegenstände:** Kraftbeere (groß werden), Feuerblume (Feuerkugeln), Funkelstern
(unverwundbar), Herz (Extraleben). 100 Münzen = Extraleben. Wer über das Zieltor
springt, bekommt ein Extraleben. Der Fortschritt wird gespeichert – bereits
erreichte Level lassen sich im Titelbild mit ← / → auswählen.

## Projektaufbau

- `core/` – die komplette Spiellogik in reinem Kotlin (Physik, Level, Gegner,
  Bosse, Grafik, Synthesizer). Läuft ohne Android und ist mit Tests abgedeckt.
- `app/` – die Android-App: Anzeige, Touch-/Gamepad-Steuerung, Ton, Speicherstand.

```bash
./gradlew :core:test              # Tests (ohne Android SDK möglich)
./gradlew :app:assembleRelease    # APK bauen (Android SDK nötig)
```

Die Tests prüfen unter anderem, dass jedes Level vom Start bis zum Ziel mit der
tatsächlichen Sprungweite begehbar ist, und legen Screenshots unter
`core/build/shots/` ab.

Die APK wird mit dem Schlüssel aus `keystore/` signiert, damit Updates ohne
Deinstallation eingespielt werden können. Für eine Veröffentlichung im Play
Store sollte ein eigener, geheimer Schlüssel verwendet werden.
