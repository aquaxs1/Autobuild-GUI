# Autobuild GUI

Client-seitiger Fabric-Mod für **Minecraft 26.2**. Ein Hotkey öffnet ein Menü mit allen
aktuell in Litematica geladenen Schematic-Placements; ein Klick auf eine Zeile lässt
**Baritone** genau dieses Placement bauen.

Der Mod ist ausschließlich GUI und Glue-Code. Pathfinding und Blockplatzierung macht
komplett Baritone, das Einlesen der Schematics komplett Litematica.

---

## Website

Die Projektseite — Download, Kurzbeschreibung, Interface-Ansicht und Nutzungsbedingungen —
liegt als statische Seite in [`docs/`](docs/index.html). Veröffentlichen über
**Settings → Pages → Source: „Deploy from a branch", Branch `main`, Ordner `/docs`**;
danach erreichbar unter `https://aquaxs1.github.io/Autobuild-GUI/`.

Der Download-Button zeigt auf das Release-Asset `autobuild-gui-0.1.0.jar`. Bei einem neuen
Release müssen in `docs/index.html` die Versionsnummer, die Dateigröße und der SHA-256
angepasst werden.

---

## Was der Mod tut

- **`B`** (frei belegbar) öffnet das Menü.
- Liste aller geladenen Placements mit Name, Größe, Blockanzahl und Status.
- Suchfeld filtert nach Name.
- **Material-Check vor dem Start:** reicht das Inventar nicht, zeigt die Zeile
  „N Blöcke fehlen" und der Klick ist gesperrt — statt mittendrin steckenzubleiben.
- **Klick startet den Build** für genau dieses Placement, das Menü schließt sich.
- Das laufende Placement zeigt beim erneuten Öffnen eine Laufanzeige mit **✕** zum
  Abbrechen. Es läuft immer nur ein Build; ein neuer bricht den alten sauber ab.
- Fehlt Litematica oder Baritone, öffnet sich das Menü trotzdem und sagt, was fehlt.

---

## Benötigte Mods

| Mod | Version | Pflicht |
|---|---|---|
| Fabric Loader | ≥ `0.19.3` | ja |
| Fabric API | `0.156.0+26.2` | ja |
| [Litematica](https://www.curseforge.com/minecraft/mc-mods/litematica) | `26.2-0.28.4` oder neuer | für die Placement-Liste |
| [MaLiLib](https://www.curseforge.com/minecraft/mc-mods/malilib) | `26.2-0.29.2` oder neuer | von Litematica benötigt |
| Baritone | siehe unten | für das Bauen |
| Java | 25 | ja |

Ohne Litematica bzw. Baritone startet der Mod trotzdem und zeigt eine Meldung statt
abzustürzen — beide sind echte Soft-Dependencies.

### Baritone — bitte die richtige Variante

> **Empfohlen:** [meteorclient.com](https://meteorclient.com) → Download-Button
> **„\*Baritone [26.2]"**
>
> Das ist der eigenständige Baritone-Fork von MeteorDevelopment. Er ist **kein**
> Bestandteil des vollen Meteor Clients — der Utility-Mod muss *nicht* installiert
> werden. Einfach die JAR in den `mods`-Ordner legen.

**Warum das wichtig ist:** Baritone wird in drei Varianten gebaut, und **eine davon
funktioniert mit diesem Mod nicht**:

| Variante | `baritone.api.*` | nutzbar |
|---|---|---|
| `api` | erhalten | ✅ |
| `unoptimized` | erhalten | ✅ |
| `standalone` | wegobfuskiert | ❌ |

Im `standalone`-Build entfernt Baritones eigener ProGuard-Lauf gezielt die Keep-Regel
für das API-Paket (`scripts/proguard.pro`: `-keep class baritone.api.** { *; }
# this is the keep api`). Dort heißen alle API-Methoden nur noch `a()`, `b()`, `c()` —
kein anderer Mod kann sie mehr aufrufen.

Landet trotzdem ein `standalone`-Build im `mods`-Ordner, stürzt nichts ab: der Klick
meldet dann *„Diese Baritone-Variante hat kein API-Paket"*.

**Alternative:** [`dysnasia/baritone-26.2`](https://github.com/dysnasia/baritone-26.2).
Achtung — der aktuell dort veröffentlichte Release (Tag `26.2`) **ist** die
`standalone`-Variante und funktioniert mit diesem Mod **nicht**. Nur brauchbar, wenn
man dort eine `api`- oder `unoptimized`-Variante findet oder selbst baut.

---

## Installation

1. Fabric Loader für Minecraft 26.2 installieren.
2. Diese JARs in den `mods`-Ordner legen:
   - Fabric API
   - MaLiLib + Litematica
   - Baritone (siehe oben — *nicht* die `standalone`-Variante)
   - `autobuild-gui-<version>.jar`
3. Minecraft starten, Welt betreten, **`B`** drücken.

---

## Konfiguration

`config/autobuildgui.json`, wird beim ersten Start mit Standardwerten angelegt:

```json
{
  "closeScreenOnBuildStart": true,
  "materialCheckEnabled": true
}
```

| Schlüssel | Bedeutung |
|---|---|
| `closeScreenOnBuildStart` | Ob sich das Menü schließt, sobald ein Build gestartet wurde. `false` lässt es offen, damit man die Laufanzeige direkt sieht. |
| `materialCheckEnabled` | Ob vor dem Start geprüft wird, ob das Inventar reicht. Die Prüfung läuft einmal beim Öffnen des Menüs über das komplette Schematic-Volumen; bei sehr großen Schematics ist das ein spürbarer Hitch. `false` schaltet sie ab — dann startet jeder Build ungeprüft. |

**Der Keybind steht bewusst nicht in dieser Datei.** Minecraft verwaltet
Tastenbelegungen selbst und speichert sie in `options.txt`; eine zweite Quelle würde die
Änderung aus dem Steuerungs-Menü beim nächsten Start überschreiben. Der Hotkey wird über
**Optionen → Steuerung → „Autobuild-Menü öffnen"** geändert.

---

## Selbst bauen

Voraussetzung: **JDK 25**.

Baritone hat für 26.2 kein Maven-Artefakt, deshalb wird die JAR lokal eingebunden:

```bash
git clone https://github.com/aquaxs1/Autobuild-GUI.git
cd Autobuild-GUI

mkdir -p libs
# Baritone-JAR (api- oder unoptimized-Variante, siehe oben) nach libs/ legen:
cp ~/Downloads/baritone-26.2.jar libs/

./gradlew build
```

Ergebnis: `build/libs/autobuild-gui-<version>.jar`.

Zum Testen im Entwicklungs-Client: `./gradlew runClient`.

Die Baritone-JAR wird nur zur Compile-Zeit gebraucht (`compileOnly`) und ist per
`.gitignore` ausgeschlossen — Baritone steht unter LGPL-3.0 und wird eingebunden, nicht
mitgeliefert. Litematica und MaLiLib kommen automatisch als `compileOnly` von
`masa.dy.fi`.

---

## Bekannte Einschränkungen

- **Der Fortschrittsbalken zeigt keine Prozentzahl.** Baritones `IBuilderProcess` bietet
  keinerlei Fortschritts-Auskunft, deshalb ist die Anzeige bewusst unbestimmt
  („läuft") statt eine erfundene Zahl zu zeigen. Eine echte Prozentzahl bräuchte
  Litematicas `SchematicVerifier`, der asynchron läuft und eigene Chat-Meldungen
  erzeugt — vorgemerkt für nach dem ersten funktionierenden Release.
- **Der Material-Check ist bei angefangenen Bauten pessimistisch.** Litematicas
  synchrone Materialliste zählt den Bedarf für einen Bau von Null aus, ohne die Welt zu
  betrachten. Ein halb fertiges Placement verlangt also mehr Material, als tatsächlich
  noch nötig ist.
- **Der Check läuft beim Öffnen des Menüs, nicht laufend.** Ändert sich das Inventar bei
  offenem Menü, sind die Badges veraltet. Der Klick prüft aber erneut, baut also nie auf
  veralteten Daten.
- **Im Creative-Modus wird nicht geprüft.** Litematicas Materialermittlung kennt keinen
  Creative-Sonderfall und würde dort alles als fehlend melden.
- Nur Client-Seite, keine Netzwerkpakete. Auf Servern gelten dieselben Regeln wie für
  Baritone selbst.

Details und die dahinterliegende API-Recherche: [`docs/RESEARCH.md`](docs/RESEARCH.md).

---

## Lizenz

MIT — siehe [`LICENSE`](LICENSE). Baritone (LGPL-3.0) und Litematica (LGPLv3) werden nur
als externe Abhängigkeiten eingebunden, ihr Quellcode ist hier nicht enthalten.
