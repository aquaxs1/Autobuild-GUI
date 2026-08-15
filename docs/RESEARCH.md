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
| Fabric Loom | `1.17-SNAPSHOT` → löst auf zu **1.17.19** | dito (Litematica nutzt `1.17.+`) |
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

### Gegen das echte JAR verifiziert

Ab hier stammt alles aus `javap` auf
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/`.

```java
// net.minecraft.client.gui.screens.Screen
protected Screen(Component title);
protected void init();
public void extractRenderState(GuiGraphicsExtractor, int, int, float);   // Override-Punkt
public void extractBackground(GuiGraphicsExtractor, int, int, float);
public final void extractRenderStateWithTooltipAndSubtitles(GuiGraphicsExtractor, int, int, float);
public final void init(int, int);          // final - nicht ueberschreibbar
public boolean isPauseScreen();
public void onClose();
protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T);
protected <T extends Renderable> T addRenderableOnly(T);

// net.minecraft.client.gui.GuiGraphicsExtractor - Text heisst nicht mehr drawString
public void text(Font, Component, int x, int y, int color);
public void centeredText(Font, Component, int x, int y, int color);
public void fill(int, int, int, int, int);
public void fillGradient(int, int, int, int, int, int);
public void enableScissor(int, int, int, int);   // fuer die Scroll-Liste in Phase 3
public void disableScissor();
public void blitSprite(RenderPipeline, Identifier, int, int, int, int);
public void setTooltipForNextFrame(Font, List<Component>, Optional<TooltipComponent>, int, int);
public int guiWidth();
public int guiHeight();
```

**Zwei Umbenennungen, die man sonst falsch rät:**

- `Minecraft.setScreen(...)` existiert nicht mehr → `Minecraft.setScreenAndShow(Screen)`.
- `Minecraft.screen` existiert nicht mehr. Der aktuelle Screen hängt jetzt an
  `net.minecraft.client.gui.Gui`: `client.gui.screen()` (Getter) und
  `client.gui.setScreen(Screen)`. Das Feld `Minecraft.gui` ist `public final`.

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

`KeyMapping` selbst (aus dem JAR):

```java
public KeyMapping(String name, int keyCode, KeyMapping.Category category);
public KeyMapping(String name, InputConstants.Type type, int keyCode, KeyMapping.Category category);
public boolean consumeClick();
public boolean isDown();
```

Die Kategorie ist in 26.2 **kein String mehr**, sondern der Record
`KeyMapping.Category` mit den Konstanten `MOVEMENT`, `MISC`, `MULTIPLAYER`, `GAMEPLAY`,
`INVENTORY`, `CREATIVE`, `SPECTATOR`, `DEBUG` und einer Factory
`Category.register(Identifier)`.

> Wir nutzen vorerst `Category.MISC`. Eine eigene Kategorie wäre möglich, aber
> `Category.label()` baut den Übersetzungsschlüssel über
> `id.toLanguageKey("key.category")`, während die Vanilla-Sprachdatei
> `key.categories.*` (Plural) enthält. Welcher Schlüssel am Ende greift, ist ohne
> Test im Spiel nicht eindeutig — deshalb keine Rate-Lösung. Kandidat für Phase 6.

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

### Kein Release für 26.2

`cabaletta/baritone@26.2` existiert als Branch (HEAD `2991d92`, GitHub-Merge-Commit von
leijurv vom 11.08.2026, PR #5076 von ZacSharp). Es gibt aber **keinen Tag und keinen
Release** dafür — der neueste Tag im Repo ist `v1.15.0`. Es existiert also kein gebautes
Artefakt von upstream.

`dysnasia/baritone-26.2` hat dagegen `refs/tags/26.2` und eine Releases-Seite mit
`baritone-26.2.jar`. **Deshalb: dieses JAR nach `libs/`.**

---

## 6. Offene Punkte

- Scroll-Listen-Basisklasse für Phase 3 (`ObjectSelectionList`-Äquivalent) — noch nicht
  im JAR nachgeschlagen.
- `LitematicaSchematic.getAreaSize(...)` und die Ermittlung der Blockanzahl (Phase 2).
- Eigene Keybind-Kategorie statt `Category.MISC` (siehe Abschnitt 3).
- Der Keydruck selbst ist in dieser Umgebung nicht verifizierbar: der Client startet
  headless unter Xvfb bis ins Hauptmenü, aber es gibt keine Tastatureingabe.
