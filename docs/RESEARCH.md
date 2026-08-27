# Research: verified versions and API signatures for Minecraft 26.2

Everything here was read from **real source code**, not reconstructed from memory.
Every line names its source. As of 2026-08-15.

Cloned references (all reachable through GitHub):

| Repo | Branch | Commit |
|---|---|---|
| `FabricMC/fabric-example-mod` | HEAD | — |
| `FabricMC/fabric` (Fabric API) | `26.2` | `adde6dd` |
| `cabaletta/baritone` | `26.2` | `2991d92` |
| `sakura-ryoko/litematica` | `26.2` | `d59910a` |

---

## 1. Toolchain

| Component | Version | Source |
|---|---|---|
| Minecraft | `26.2` | `fabric-example-mod/gradle.properties` |
| Fabric Loader | `0.19.3` | same; identical in Baritone + Litematica |
| Fabric API | `0.156.0+26.2` | same; identical in Litematica |
| Fabric Loom | `1.17-SNAPSHOT` → resolves to **1.17.19** | same (Litematica uses `1.17.+`) |
| Gradle | `9.5.1` | `fabric-example-mod/gradle/wrapper/gradle-wrapper.properties` |
| Java | `25` | `options.release = 25`, `VERSION_25` |

The plugin ID is `net.fabricmc.fabric-loom` (not `fabric-loom`).

### Mappings: Mojang mappings

The 26.2 template no longer has a **`mappings` block** — the `dependencies` block holds
only `minecraft`, `fabric-loader`, `fabric-api`. The same goes for Litematica 26.2.
Baritone sets `mojmap()` explicitly (`build.gradle:96-97`, through Unimined).

So our mod, Baritone and Litematica all sit on Mojmap — **no mapping mismatch**.

One rename worth noticing: in 26.2 Mojmap, `ResourceLocation` is called
`net.minecraft.resources.Identifier`
(imported in `litematica/data/DataManager.java:17` and in the Fabric API test mods).

---

## 2. The MC 26.2 GUI: a new render pipeline

**This is the biggest departure from older versions.** The classic
`render(GuiGraphics, int, int, float)` path no longer exists. 26.2 uses an extraction
pipeline through `net.minecraft.client.gui.GuiGraphicsExtractor`.

Verified against `fabric-networking-api-v1/.../ChannelScreen.java` (a real `Screen`
subclass in 26.2) and `fabric-screen-api-v1/.../StopSoundButton.java` (a real widget):

```java
// Screen
protected void init()
public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta)
protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta)
// extractRenderStateWithTooltipAndSubtitles(GuiGraphicsExtractor, int, int, float) — called by the Gui

// Widget (AbstractButton)
protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta)
public void onPress(InputWithModifiers ctx)          // net.minecraft.client.input.InputWithModifiers
protected void updateWidgetNarration(NarrationElementOutput out)
```

Further confirmed calls:

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

> The consequence for phase 3: "`DrawContext`" from the brief is the Yarn name of the
> old `GuiGraphics`. In 26.2 we draw through `GuiGraphicsExtractor` in the extract pass.
> The look stays classic — only the hook is different.

---

### Verified against the real JAR

From here on, everything comes from `javap` run against
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/`.

```java
// net.minecraft.client.gui.screens.Screen
protected Screen(Component title);
protected void init();
public void extractRenderState(GuiGraphicsExtractor, int, int, float);   // the override point
public void extractBackground(GuiGraphicsExtractor, int, int, float);
public final void extractRenderStateWithTooltipAndSubtitles(GuiGraphicsExtractor, int, int, float);
public final void init(int, int);          // final - cannot be overridden
public boolean isPauseScreen();
public void onClose();
protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T);
protected <T extends Renderable> T addRenderableOnly(T);

// net.minecraft.client.gui.GuiGraphicsExtractor - text is no longer drawString
public void text(Font, Component, int x, int y, int color);
public void centeredText(Font, Component, int x, int y, int color);
public void fill(int, int, int, int, int);
public void fillGradient(int, int, int, int, int, int);
public void enableScissor(int, int, int, int);   // for the scroll list in phase 3
public void disableScissor();
public void blitSprite(RenderPipeline, Identifier, int, int, int, int);
public void setTooltipForNextFrame(Font, List<Component>, Optional<TooltipComponent>, int, int);
public int guiWidth();
public int guiHeight();
```

**Two renames that are otherwise easy to guess wrong:**

- `Minecraft.setScreen(...)` no longer exists → `Minecraft.setScreenAndShow(Screen)`.
- `Minecraft.screen` no longer exists. The current screen now hangs off
  `net.minecraft.client.gui.Gui`: `client.gui.screen()` (the getter) and
  `client.gui.setScreen(Screen)`. The field `Minecraft.gui` is `public final`.

## 3. Keybind (Fabric API 26.2)

The module is now called `fabric-key-mapping-api-v1` (previously `fabric-key-binding-api-v1`).

```java
package net.fabricmc.fabric.api.client.keymapping.v1;

public final class KeyMappingHelper {
    public static KeyMapping registerKeyMapping(KeyMapping keyMapping);
    public static InputConstants.Key getBoundKeyOf(KeyMapping keyMapping);
}
```

The types: `net.minecraft.client.KeyMapping`, `com.mojang.blaze3d.platform.InputConstants`.

`KeyMapping` itself (from the JAR):

```java
public KeyMapping(String name, int keyCode, KeyMapping.Category category);
public KeyMapping(String name, InputConstants.Type type, int keyCode, KeyMapping.Category category);
public boolean consumeClick();
public boolean isDown();
```

In 26.2 the category is **no longer a string**; it is the record `KeyMapping.Category`,
with the constants `MOVEMENT`, `MISC`, `MULTIPLAYER`, `GAMEPLAY`, `INVENTORY`,
`CREATIVE`, `SPECTATOR`, `DEBUG` and a factory `Category.register(Identifier)`.

> We use `Category.MISC` for now. A category of our own would be possible, but
> `Category.label()` builds the translation key through
> `id.toLanguageKey("key.category")`, while the vanilla language file contains
> `key.categories.*` (plural). Which key ends up winning is not clear without a test
> in-game — so no guesswork here. A candidate for phase 6.

---

## 4. Baritone 26.2

Upstream has a real `26.2` branch — the `dysnasia/baritone-26.2` fork is not needed.

`cabaletta/baritone@26.2`, `gradle.properties`: `mod_version=1.18.0`, `java_version=25`,
`minecraft_version=26.2`, `fabric_version=0.19.3`.

It builds with **Unimined**, not Loom. There is no published Maven artefact; a
`jitpack.yml` ships with it. So we pull the JAR in locally through `libs/`
(`compileOnly`). Baritone is LGPL-3.0 — **link against it, never vendor the source**.

`fabric.mod.json`: id `baritone`, `environment: "*"`, no entrypoints,
`depends: { fabricloader: ">=0.19.3", minecraft: ["26.2"] }`.

### The entry points

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
void buildOpenLitematic(int i);          // <- 0-based, see LitematicaCommand
void pause();
boolean isPaused();
void resume();
void clearArea(BlockPos corner1, BlockPos corner2);
List<BlockState> getApproxPlaceable();   // only while the process is running
Optional<Integer> getMinLayer();
Optional<Integer> getMaxLayer();
```

`getApproxPlaceable()` is the hook for the material check in phase 5.

`LitematicaCommand` calls `buildOpenLitematic(args - 1)` — the command is 1-based, the
API 0-based.

### LitematicaHelper (`baritone/utils/schematic/litematica/LitematicaHelper.java`)

```java
public static boolean isLitematicaPresent();
public static boolean hasLoadedSchematic(int i);
public static Pair<IStaticSchematic, Vec3i> getSchematic(int i);
```

The index is the position in
`DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()` — so Baritone
and our menu have to read the same list in the same order.

---

## 5. Litematica 26.2

`sakura-ryoko/litematica@26.2` is the maintained variant (`maruohon/litematica` only has
old branches left on GitHub; its default is `ornithe/1.12.2`).

`mod_version = 0.28.5-sakura.11`, `malilib_version = 0.29.4-sakura.11`.

`fabric.mod.json`: id `litematica`, `environment: "client"`,
`depends: { minecraft: "~26.2-", malilib: ">=0.29.4- <0.30.0-" }`.

### Reading the placements

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

For the size and block count (phase 2), the way in is `getSchematic()`; Baritone's
`LitematicaHelper` uses `placement.getSchematic().getAreaSize(regionName)` and
`placement.getEnabledRelativeSubRegionPlacements()` there — check both against the real
JAR while implementing.

---

## 5b. Litematica/malilib als echte Compile-Dependency

Anders als Baritone hat Litematica ein publiziertes Maven-Artefakt auf
`masa.dy.fi/maven/sakura-ryoko`, deshalb `compileOnly` statt `libs/`-JAR.
Verifiziert per `curl` gegen die echte `maven-metadata.xml`:

| Artefakt | Veröffentlichte Version | Hinweis |
|---|---|---|
| `fi.dy.masa.litematica:litematica-fabric-26.2` | `0.28.4` | Quell-Branch (`26.2` auf GitHub) steht schon bei `0.28.5-sakura.11` — das ist unveröffentlichter Dev-Stand, nicht auf dem Maven. Wir bauen gegen die veröffentlichte `0.28.4`. |
| `fi.dy.masa.malilib:malilib-fabric-26.2` | `0.29.2` | Aus der POM von `litematica-fabric-26.2:0.28.4` selbst gelesen (dort als Dependency deklariert) — nicht die neuere `0.29.3`, die dort nicht referenziert wird. |

**`me.fallenbreath:conditional-mixin-fabric:0.6.4`** ist eine transitive Dependency von
Litematica, aber nur auf `maven.fallenbreath.me` erreichbar — nicht auf der
freigegebenen Host-Liste, und auf keinem erreichbaren Host gespiegelt (Maven Central:
404). Da unser Adapter-Code diese Klasse nie anfasst (nur Litematica selbst braucht sie
zur Laufzeit, die dann der separat installierte Litematica-Mod mitbringt), wird sie per
Gradle `exclude` aus der Compile-Dependency herausgenommen:

```groovy
compileOnly("fi.dy.masa.litematica:litematica-fabric-${minecraft_version}:${litematica_version}") {
    exclude group: 'me.fallenbreath', module: 'conditional-mixin-fabric'
}
```

Gegen das echte gemappte `litematica-fabric-26.2-0.28.4.jar` per `javap` geprüft, alle
Signaturen decken sich mit dem Quellcode-Klon:

```java
// fi.dy.masa.litematica.data.DataManager
public static SchematicPlacementManager getSchematicPlacementManager();

// fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager
public List<SchematicPlacement> getAllSchematicsPlacements();

// fi.dy.masa.litematica.schematic.placement.SchematicPlacement
public String getName();
public LitematicaSchematic getSchematic();
public BlockPos getOrigin();

// fi.dy.masa.litematica.schematic.LitematicaSchematic
public SchematicMetadata getMetadata();

// fi.dy.masa.litematica.schematic.SchematicMetadata
public String getName();
public int getTotalBlocks();
public Vec3i getEnclosingSize();
```

`SchematicMetadata` liefert Name, Größe und Blockanzahl in einem Aufruf — das ist die
kanonische, von Litematica selbst für Anzeigezwecke genutzte Quelle (nicht aus der
Geometrie neu berechnet).

## 6. Baritone: kein Release für 26.2 (relevant für Phase 4)

`cabaletta/baritone@26.2` exists as a branch (HEAD `2991d92`, a GitHub merge commit by
leijurv from 2026-08-11, PR #5076 by ZacSharp). But there is **no tag and no release**
for it — the newest tag in the repo is `v1.15.0`. So there is no built artefact from
upstream.

`dysnasia/baritone-26.2`, by contrast, has `refs/tags/26.2` and a releases page with
`baritone-26.2.jar`. **Hence: that JAR goes into `libs/`.**

---

## 6. Open questions

- The scroll-list base class for phase 3 (the `ObjectSelectionList` equivalent) — not yet
  looked up in the JAR.
- `LitematicaSchematic.getAreaSize(...)` and working out the block count (phase 2).
- A keybind category of our own instead of `Category.MISC` (see section 3).
- The key press itself cannot be verified in this environment: the client starts headless
  under Xvfb as far as the main menu, but there is no keyboard input.
