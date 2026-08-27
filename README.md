# Autobuild GUI

A client-side Fabric mod for **Minecraft 26.2**. A hotkey opens a menu listing every
schematic placement currently loaded in Litematica; a click on a row makes **Baritone**
build exactly that placement.

The mod is GUI and glue code, nothing else. Pathfinding and block placement are entirely
Baritone's job, reading the schematics entirely Litematica's.

---

## Website

The project page — download, a short description, a look at the interface and the terms
of use — lives as a static page in [`site/`](site/index.html).

GitHub Pages' "Deploy from a branch" option only serves the repository root or a `/docs`
folder, not `/site` — publish this way instead: **Settings → Pages → Source: "GitHub
Actions"**, then add a workflow that uploads `site/` as the Pages artifact (see
[`actions/upload-pages-artifact`](https://github.com/actions/upload-pages-artifact)). It
is then reachable at `https://aquaxs1.github.io/Autobuild-GUI/`. Any other static host
(Vercel, Netlify, Cloudflare Pages, …) can point its build output directory at `site/`
directly.

The download button points at the release asset `autobuild-gui-0.1.0.jar`. On a new
release, the version number, the file size and the SHA-256 in `site/index.html` have to
be updated.

---

## What the mod does

- **`B`** (rebindable) opens the menu.
- A list of every loaded placement with name, size, block count and status.
- The search box filters by name.
- **A material check before the start:** if the inventory falls short, the row reads
  "N blocks missing" and the click is locked — rather than getting stuck halfway through.
- **A click starts the build** for exactly that placement, and the menu closes.
- The placement being built shows a running indicator with an **✕** to cancel when the
  menu is opened again. Only ever one build runs; a new one cancels the old one cleanly.
- If Litematica or Baritone is missing, the menu still opens and says what is missing.

---

## Required mods

| Mod | Version | Required |
|---|---|---|
| Fabric Loader | ≥ `0.19.3` | yes |
| Fabric API | `0.156.0+26.2` | yes |
| [Litematica](https://www.curseforge.com/minecraft/mc-mods/litematica) | `26.2-0.28.4` or newer | for the placement list |
| [MaLiLib](https://www.curseforge.com/minecraft/mc-mods/malilib) | `26.2-0.29.2` or newer | needed by Litematica |
| Baritone | see below | for the building |
| Java | 25 | yes |

Without Litematica or Baritone the mod still starts and shows a message instead of
crashing — both are genuine soft dependencies.

### Baritone — please the right variant

> **Recommended:** [meteorclient.com](https://meteorclient.com) → the download button
> **"\*Baritone [26.2]"**
>
> That is the standalone Baritone fork by MeteorDevelopment. It is **not** a part of the
> full Meteor Client — the utility mod does *not* have to be installed. Just drop the JAR
> into the `mods` folder.

**Why this matters:** Baritone is built in three variants, and **one of them does not work
with this mod**:

| Variant | `baritone.api.*` | Usable |
|---|---|---|
| `api` | preserved | ✅ |
| `unoptimized` | preserved | ✅ |
| `standalone` | obfuscated away | ❌ |

In the `standalone` build, Baritone's own ProGuard run deliberately removes the keep rule
for the API package (`scripts/proguard.pro`: `-keep class baritone.api.** { *; }
# this is the keep api`). There every API method is called just `a()`, `b()`, `c()` — no
other mod can call them any more.

If a `standalone` build ends up in the `mods` folder anyway, nothing crashes: the click
then reports *"This Baritone build has no API package"*.

**Alternative:** [`dysnasia/baritone-26.2`](https://github.com/dysnasia/baritone-26.2).
Careful — the release published there right now (tag `26.2`) **is** the `standalone`
variant and does **not** work with this mod. Only useful if you find an `api` or
`unoptimized` variant there, or build one yourself.

---

## Installation

1. Install Fabric Loader for Minecraft 26.2.
2. Drop these JARs into the `mods` folder:
   - Fabric API
   - MaLiLib + Litematica
   - Baritone (see above — *not* the `standalone` variant)
   - `autobuild-gui-<version>.jar`
3. Start Minecraft, enter a world, press **`B`**.

---

## Configuration

`config/autobuildgui.json`, created with default values on the first start:

```json
{
  "closeScreenOnBuildStart": true,
  "materialCheckEnabled": true
}
```

| Key | Meaning |
|---|---|
| `closeScreenOnBuildStart` | Whether the menu closes as soon as a build has been started. `false` leaves it open so you can watch the running indicator directly. |
| `materialCheckEnabled` | Whether the inventory is checked before the start. The check runs once when the menu opens, across the complete schematic volume; for very large schematics that is a noticeable hitch. `false` turns it off — every build then starts unchecked. |

**The keybind deliberately does not live in this file.** Minecraft manages key bindings
itself and stores them in `options.txt`; a second source would overwrite the change made
in the controls menu on the next start. The hotkey is changed through
**Options → Controls → "Open Autobuild menu"**.

---

## Building it yourself

Prerequisite: **JDK 25**.

Baritone has no Maven artefact for 26.2, so the JAR is wired in locally:

```bash
git clone https://github.com/aquaxs1/Autobuild-GUI.git
cd Autobuild-GUI

mkdir -p libs
# put the Baritone JAR (api or unoptimized variant, see above) into libs/:
cp ~/Downloads/baritone-26.2.jar libs/

./gradlew build
```

Result: `build/libs/autobuild-gui-<version>.jar`.

To test it in the development client: `./gradlew runClient`.

The Baritone JAR is only needed at compile time (`compileOnly`) and is excluded through
`.gitignore` — Baritone is licensed LGPL-3.0 and is linked against, not shipped along.
Litematica and MaLiLib come in automatically as `compileOnly` from `masa.dy.fi`.

---

## Known limitations

- **The progress bar shows no percentage.** Baritone's `IBuilderProcess` offers no
  progress information at all, so the indicator is deliberately indeterminate
  ("running") rather than showing a made-up number. A real percentage would need
  Litematica's `SchematicVerifier`, which runs asynchronously and produces chat messages
  of its own — noted down for after the first working release.
- **The material check is pessimistic on builds already started.** Litematica's
  synchronous material list counts what a build from zero needs, without looking at the
  world. A half-finished placement therefore asks for more material than is actually
  still needed.
- **The check runs when the menu opens, not continuously.** If the inventory changes
  while the menu is open, the badges are stale. The click does check again, though, so it
  never builds on stale data.
- **No check in creative mode.** Litematica's material accounting has no special case for
  creative and would report everything as missing there.
- Client-side only, no network packets. On servers the same rules apply as for Baritone
  itself.

Details and the API research behind it: [`docs/RESEARCH.md`](docs/RESEARCH.md).

---

## Licence

MIT — see [`LICENSE`](LICENSE). Baritone (LGPL-3.0) and Litematica (LGPLv3) are wired in
as external dependencies only; their source is not contained here.
