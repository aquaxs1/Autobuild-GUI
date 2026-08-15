# Recherche: verifizierte Versionen und API-Signaturen für Minecraft 26.2

Alles hier ist aus **echtem Quellcode** gelesen, nicht aus dem Gedächtnis rekonstruiert.
Jede Zeile nennt ihre Quelle. Stand: 2026-08-15.

Geklonte Referenzen (alle über GitHub erreichbar):

| Repo | Branch | Commit |
|---|---|---|
| `FabricMC/fabric-example-mod` | HEAD | — |
| `FabricMC/fabric` (Fabric API) | `26.2` | `adde6dd` |
| `cabaletta/baritone` | `26.2` | `2991d92` |
| `sakura-ryoko/litematica` | `26.2` | `d59910a` |

---

## 1. Toolchain

| Komponente | Version | Quelle |
|---|---|---|
| Minecraft | `26.2` | `fabric-example-mod/gradle.properties` |
| Fabric Loader | `0.19.3` | dito; identisch in Baritone + Litematica |
| Fabric API | `0.156.0+26.2` | dito; identisch in Litematica |
| Fabric Loom | `1.17-SNAPSHOT` | dito (Litematica nutzt `1.17.+`) |
| Gradle | `9.5.1` | `fabric-example-mod/gradle/wrapper/gradle-wrapper.properties` |
| Java | `25` | `options.release = 25`, `VERSION_25` |

Plugin-ID ist `net.fabricmc.fabric-loom` (nicht `fabric-loom`).

### Mappings: Mojang-Mappings

Das 26.2-Template hat **keinen `mappings`-Block** mehr — der `dependencies`-Block enthält
nur `minecraft`, `fabric-loader`, `fabric-api`. Litematica 26.2 ebenso. Baritone setzt
explizit `mojmap()` (`build.gradle:96-97`, über Unimined).

Damit liegen unser Mod, Baritone und Litematica alle auf Mojmap — **kein Mapping-Mismatch**.

Umbenennung, die auffällt: `ResourceLocation` heißt in 26.2-Mojmap
`net.minecraft.resources.Identifier`
(Import in `litematica/data/DataManager.java:17` und in Fabric-API-Testmods).

---

## 2. MC 26.2 GUI: neue Render-Pipeline

**Das ist die größte Abweichung von älteren Versionen.** Der klassische
`render(GuiGraphics, int, int, float)`-Pfad existiert nicht mehr. 26.2 nutzt eine
Extraction-Pipeline über `net.minecraft.client.gui.GuiGraphicsExtractor`.

Verifiziert an `fabric-networking-api-v1/.../ChannelScreen.java` (echter `Screen`-Subclass
in 26.2) und `fabric-screen-api-v1/.../StopSoundButton.java` (echtes Widget):

```java
// Screen
protected void init()
public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta)
protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta)
// extractRenderStateWithTooltipAndSubtitles(GuiGraphicsExtractor, int, int, float) — vom Gui aufgerufen

// Widget (AbstractButton)
protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta)
public void onPress(InputWithModifiers ctx)          // net.minecraft.client.input.InputWithModifiers
protected void updateWidgetNarration(NarrationElementOutput out)
```

Weitere bestätigte Aufrufe:

```java
this.addRenderableWidget(...)   // Screen
this.addRenderableOnly(...)     // Screen
Button.builder(Component, onPress).pos(x, y).size(w, h).tooltip(Tooltip.create(...)).build()
graphics.setTooltipForNextFrame(this.font, Component, x, y)
graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier, x, y, w, h)
Component.literal(...) / Component.nullToEmpty(...)
```

`net.minecraft.client.gui.components.Renderable` deklariert
`extractRenderState(GuiGraphicsExtractor, int, int, float)`
(`fabric-screen-api-v1/.../ScreenEvents.java:94`).

> Konsequenz für Phase 3: „`DrawContext`" aus dem Briefing ist der Yarn-Name des alten
> `GuiGraphics`. In 26.2 zeichnen wir über `GuiGraphicsExtractor` im Extract-Pass.
> Der Look bleibt klassisch — nur der Aufhänger ist ein anderer.

---

## 3. Keybind (Fabric API 26.2)

Das Modul heißt jetzt `fabric-key-mapping-api-v1` (vorher `fabric-key-binding-api-v1`).

```java
package net.fabricmc.fabric.api.client.keymapping.v1;

public final class KeyMappingHelper {
    public static KeyMapping registerKeyMapping(KeyMapping keyMapping);
    public static InputConstants.Key getBoundKeyOf(KeyMapping keyMapping);
}
```

Typen: `net.minecraft.client.KeyMapping`, `com.mojang.blaze3d.platform.InputConstants`.

---

## 4. Baritone 26.2

Upstream hat einen echten `26.2`-Branch — der Fork `dysnasia/baritone-26.2` wird nicht
gebraucht.

`cabaletta/baritone@26.2`, `gradle.properties`: `mod_version=1.18.0`, `java_version=25`,
`minecraft_version=26.2`, `fabric_version=0.19.3`.

Baut mit **Unimined**, nicht mit Loom. Kein publiziertes Maven-Artefakt; `jitpack.yml`
liegt bei. Deshalb binden wir das JAR lokal über `libs/` ein (`compileOnly`).
Baritone ist LGPL-3.0 — **nur einbinden, keinen Quellcode vendorn**.

`fabric.mod.json`: id `baritone`, `environment: "*"`, keine Entrypoints,
`depends: { fabricloader: ">=0.19.3", minecraft: ["26.2"] }`.

### Einstieg

```java
// baritone/api/BaritoneAPI.java
public static IBaritoneProvider getProvider();
public static Settings getSettings();

// baritone/api/IBaritoneProvider.java
IBaritone getPrimaryBaritone();
List<IBaritone> getAllBaritones();
IBaritone getBaritoneForPlayer(LocalPlayer player);      // default
IBaritone getBaritoneForMinecraft(Minecraft minecraft);  // default
ICommandSystem getCommandSystem();
ISchematicSystem getSchematicSystem();
```

### IBuilderProcess (`baritone/api/process/IBuilderProcess.java`)

```java
void build(String name, ISchematic schematic, Vec3i origin);
boolean build(String name, File schematic, Vec3i origin);
void buildOpenSchematic();
void buildOpenLitematic(int i);          // <- 0-basiert, siehe LitematicaCommand
void pause();
boolean isPaused();
void resume();
void clearArea(BlockPos corner1, BlockPos corner2);
List<BlockState> getApproxPlaceable();   // nur waehrend der Prozess aktiv ist
Optional<Integer> getMinLayer();
Optional<Integer> getMaxLayer();
```

`getApproxPlaceable()` ist der Ansatzpunkt für den Material-Check in Phase 5.

`LitematicaCommand` ruft `buildOpenLitematic(args - 1)` — der Command ist 1-basiert,
die API 0-basiert.

### LitematicaHelper (`baritone/utils/schematic/litematica/LitematicaHelper.java`)

```java
public static boolean isLitematicaPresent();
public static boolean hasLoadedSchematic(int i);
public static Pair<IStaticSchematic, Vec3i> getSchematic(int i);
```

Der Index ist die Position in
`DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()` — Baritone und
unser Menü müssen also dieselbe Liste in derselben Reihenfolge lesen.

---

## 5. Litematica 26.2

`sakura-ryoko/litematica@26.2` ist die gepflegte Variante (`maruohon/litematica` hat auf
GitHub nur noch alte Branches, Default ist `ornithe/1.12.2`).

`mod_version = 0.28.5-sakura.11`, `malilib_version = 0.29.4-sakura.11`.

`fabric.mod.json`: id `litematica`, `environment: "client"`,
`depends: { minecraft: "~26.2-", malilib: ">=0.29.4- <0.30.0-" }`.

### Placements auslesen

```java
// fi.dy.masa.litematica.data.DataManager
public static SchematicPlacementManager getSchematicPlacementManager();   // DataManager.java:229

// fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager
public List<SchematicPlacement> getAllSchematicsPlacements();             // :417
public SchematicPlacement getSelectedSchematicPlacement();                // :609

// fi.dy.masa.litematica.schematic.placement.SchematicPlacement
public String getName();              // :275
public LitematicaSchematic getSchematic();  // :280
public BlockPos getOrigin();          // :313
public Rotation getRotation();        // :318
public Mirror getMirror();            // :323
public int getSubRegionCount();       // :333
public boolean isEnabled();           // :175
public boolean isRegionPlacementModified();  // :220
```

Für Größe/Blockanzahl (Phase 2) geht es über `getSchematic()`; Baritones
`LitematicaHelper` nutzt dort `placement.getSchematic().getAreaSize(regionName)` und
`placement.getEnabledRelativeSubRegionPlacements()` — beides beim Implementieren gegen
das echte JAR gegenprüfen.

---

## 6. Offene Punkte

Diese lassen sich erst mit dem echten Minecraft-JAR auf dem Classpath abschließen:

- Exakte Signatur von `Screen.extractRenderState` inklusive Sichtbarkeit
  (`public` laut `ChannelScreen`, aber die Oberklasse ist nicht gegengelesen).
- Scroll-Listen-Basisklasse in 26.2 (`ObjectSelectionList`-Äquivalent) — in
  `ChannelScreen` wird ein eigener `ChannelList` verwendet.
- `KeyMapping`-Konstruktor-Signatur in 26.2.
- `LitematicaSchematic.getAreaSize(...)` und Blockanzahl-Ermittlung.
