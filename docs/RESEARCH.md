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

## 5b. Litematica/malilib as a real compile dependency

Unlike Baritone, Litematica has a published Maven artefact on
`masa.dy.fi/maven/sakura-ryoko`, hence `compileOnly` rather than a JAR in `libs/`.
Verified with `curl` against the real `maven-metadata.xml`:

| Artefact | Published version | Note |
|---|---|---|
| `fi.dy.masa.litematica:litematica-fabric-26.2` | `0.28.4` | The source branch (`26.2` on GitHub) is already at `0.28.5-sakura.11` — that is an unpublished dev state, not on the Maven. We build against the published `0.28.4`. |
| `fi.dy.masa.malilib:malilib-fabric-26.2` | `0.29.2` | Read out of the POM of `litematica-fabric-26.2:0.28.4` itself (declared as a dependency there) — not the newer `0.29.3`, which it does not reference. |

**`me.fallenbreath:conditional-mixin-fabric:0.6.4`** is a transitive dependency of
Litematica, but reachable only on `maven.fallenbreath.me` — not on the allowed host
list, and mirrored on no reachable host (Maven Central: 404). Since our adapter code
never touches that class (only Litematica itself needs it at runtime, and the separately
installed Litematica mod brings it along), it is taken out of the compile dependency with
a Gradle `exclude`:

```groovy
compileOnly("fi.dy.masa.litematica:litematica-fabric-${minecraft_version}:${litematica_version}") {
    exclude group: 'me.fallenbreath', module: 'conditional-mixin-fabric'
}
```

Checked with `javap` against the real mapped `litematica-fabric-26.2-0.28.4.jar`; every
signature matches the source clone:

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

`SchematicMetadata` hands over name, size and block count in a single call — that is the
canonical source, the one Litematica itself uses for display purposes (not recomputed
from the geometry).

## 6. Baritone: no release for 26.2 (relevant to phase 4)

`cabaletta/baritone@26.2` exists as a branch (HEAD `2991d92`, a GitHub merge commit by
leijurv from 2026-08-11, PR #5076 by ZacSharp). But there is **no tag and no release**
for it — the newest tag in the repo is `v1.15.0`. So there is no built artefact from
upstream.

`dysnasia/baritone-26.2`, by contrast, has `refs/tags/26.2` and a releases page with
`baritone-26.2.jar`. **Hence: that JAR goes into `libs/`.**

---

## 6b. Scroll list and widgets (phase 3, verified against the real JAR)

The earlier "open" item is settled: `ObjectSelectionList<E>` exists in 26.2 unchanged in
principle, only with the new extract pipeline. Verified with `javap` **and** against a
real 26.2 example from Fabric API itself (`ChannelList`/`ChannelScreen`,
`fabric-networking-api-v1/src/testmodClient/.../channeltest/`):

```java
// net.minecraft.client.gui.components.AbstractSelectionList<E extends Entry<E>>
//   extends AbstractContainerWidget extends AbstractScrollArea
protected AbstractSelectionList(Minecraft, int width, int height, int y, int itemHeight);
// x is ALWAYS 0 in the constructor (super(0, y, width, height, ...)) - position it
// yourself afterwards through the inherited AbstractWidget.setX(int).

public int getRowWidth();      // default: a fixed 220, centred - override for wider
                                // rows (icon+name+badge needs more)
public int getRowLeft();       // = getX() + width/2 - getRowWidth()/2
public static final int AbstractScrollArea.SCROLLBAR_WIDTH;  // = 6 (ConstantValue checked)

// net.minecraft.client.gui.components.AbstractSelectionList.Entry<E>
public abstract void extractContent(GuiGraphicsExtractor, int mouseX, int mouseY, boolean hovered, float tickDelta);
public int getContentX(); getContentY(); getContentRight(); getContentYMiddle();  // padding already accounted for

// net.minecraft.client.gui.components.ObjectSelectionList.Entry<E> (in addition)
public abstract Component getNarration();
public boolean mouseClicked(MouseButtonEvent, boolean);   // default: just "return true", NO select call of its own
```

**A correction to an earlier assumption here:** `ObjectSelectionList.Entry.mouseClicked`
does NOT select anything itself (`iconst_1; ireturn` in the bytecode - a pure no-op return
value). Selection runs down a different path: `ContainerEventHandler.mouseClicked` (a
default method, inherited by the list) finds the clicked entry through `getChildAt(x,y)`,
calls `entry.mouseClicked(...)`, and if that returns `true` plus
`entry.shouldTakeFocusAfterInteraction()`, the list calls `this.setFocused(entry)` on
itself. `AbstractSelectionList.setFocused(...)` is overridden and calls `setSelected(entry)`
in there. For phase 4 that means: an `Entry.mouseClicked` override of our own that passes
`super.mouseClicked(...)` through (still returning `true`) does not break selection - the
build trigger simply comes on top.

`ChannelList` (a Fabric API test mod, compiled against 26.2) confirms the exact parameter
order of `extractContent` one to one:

```java
public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta)
```

### The new input model

`GuiEventListener.mouseClicked` is now called `mouseClicked(MouseButtonEvent, boolean doubleClick)`
instead of the old `(double x, double y, int button)` - `MouseButtonEvent` is a record with
`x()`, `y()`, `button()`. Forwarding clicks to entries happens automatically through
`ContainerEventHandler.mouseClicked(...)` (a default method), as long as the entry
implements `GuiEventListener` - no dispatch code of our own needed.

## 8. Baritone: where to get it, and verifying the JAR (phase 4)

### Recommended source: meteorclient.com

**The JAR used in `libs/` comes from the official download page `meteorclient.com`,
button "\*Baritone [26.2]".** That is the standalone MeteorDevelopment fork of Baritone —
**not** a part of the full Meteor Client; the utility mod does not have to be installed.

| | |
|---|---|
| Source | `meteorclient.com`, download button "\*Baritone [26.2]" |
| Uploaded into this session | 2026-08-15, 07:44 UTC |
| SHA-256 | `8a2e7b71229005fd451e5f012f8b9c715e41531631a4a0a53c900a47cd852423` |
| Size | 4 852 460 bytes |
| Mod ID / version | `baritone-meteor` / `26.2-SNAPSHOT` |
| Embedded | `META-INF/jars/nether-pathfinder-1.6.jar` |

The exact download date cannot be verified independently here: `meteorclient.com` is not
reachable from this build environment (egress policy), the JAR arrived by upload. The
provenance is the project owner's word; what is checkable is the SHA-256 above — the file
in `libs/baritone-26.2.jar` is byte-identical to the upload.

This source is the **primary recommendation for the README** (phase 6), because it ships
an API-preserving variant (see below).

`dysnasia/baritone-26.2` remains worth mentioning as an alternative, **but**: its current
release (tag `26.2`) is the `standalone` variant and has no usable API — neither for
compiling nor at runtime. Details below.

### Verifying the JAR

Building Baritone from source at the `26.2` tag failed on a growing chain of legacy hosts
(Unimined itself needed `maven.wagyourtail.xyz` + `launchermeta.mojang.com`, and after that
`files.betacraft.uk` plus six more candidate repos for `dev.babbaj:nether-pathfinder` - see
the conversation). Rather than inflating the allow list indefinitely, the user uploaded a
real `baritone-26.2.jar` out of their own Meteor Client installation.

**Important: this JAR comes from `MeteorDevelopment/baritone` (embedded in Meteor), not from
`dysnasia/baritone-26.2`.** From the manifest/`fabric.mod.json` check:

```
Fabric-Minecraft-Version: 26.2
Fabric-Loader-Version: 0.19.3
mod id: "baritone-meteor"  (not "baritone")
version: "26.2-SNAPSHOT"
entrypoints: {}            (empty - does not initialise itself through Fabric)
jars: [ "META-INF/jars/nether-pathfinder-1.6.jar" ]   (a real Fabric jar-in-jar, built with Loom)
```

Verified with `javap` against the real `.class` files in this JAR - every signature matches
the `cabaletta/baritone@26.2` source exactly:

```java
// baritone.api.BaritoneAPI
public static IBaritoneProvider getProvider();
public static Settings getSettings();

// baritone.api.IBaritoneProvider
IBaritone getPrimaryBaritone();

// baritone.api.IBaritone
IBuilderProcess getBuilderProcess();

// baritone.api.process.IBuilderProcess extends IBaritoneProcess
void buildOpenLitematic(int);
List<BlockState> getApproxPlaceable();   // candidate for the phase 5 material check
Optional<Integer> getMinLayer(); getMaxLayer();

// baritone.api.process.IBaritoneProcess (base interface, relevant to phase 5)
boolean isActive();
```

`LitematicaCommand.execute(...)` was additionally traced through the bytecode (not just the
source): internally it still calls `baritone.getBuilderProcess().buildOpenLitematic(index - 1)`
- the 0-based index semantics are confirmed, not merely assumed.

### Obfuscation outside `baritone.api.*`

`baritone.utils.schematic.litematica.LitematicaHelper` is ProGuard-minified in this JAR:
`isLitematicaPresent()` → `a()`, `hasLoadedSchematic(int)` → `a(int)`, `getSchematic(int)` →
`a(int)` (two overloads named `a` with an identical parameter type, differing only in return
type - not callable from Java source, only through reflection). It does not affect us: our own
`LitematicaAdapter` (phase 2) reads Litematica directly, without ever going through Baritone's
`LitematicaHelper`. In this (Meteor) JAR the public `baritone.api.*` package stays readable -
it is an `api` variant.

> **Addendum:** an earlier version of this section concluded from that, that `baritone.api.*`
> is preserved *in general*. That is wrong and was disproved by a second, real JAR - see the
> following section. Whether the API survives depends solely on the distribution variant
> chosen.

### Baritone ships THREE variants - only two of them are usable by other mods

**This is the most important finding for this project.** From `BaritoneGradleTask.java`:

```java
ARTIFACT_UNOPTIMIZED = "%s-unoptimized-%s.jar";
ARTIFACT_API         = "%s-api-%s.jar";
ARTIFACT_STANDALONE  = "%s-standalone-%s.jar";
```

For a Fabric build the files are therefore called:

| File | `baritone.api.*` | Usable for us |
|---|---|---|
| `baritone-api-fabric-26.2.jar` | preserved | **yes** |
| `baritone-unoptimized-fabric-26.2.jar` | preserved (no ProGuard) | yes |
| `baritone-standalone-fabric-26.2.jar` | **obfuscated away** | **no** |

The difference is made in `scripts/proguard.pro` line 31:

```
-keep class baritone.api.** { *; } # this is the keep api
```

and `ProguardTask.generateConfigs()`:

```java
// For the Standalone config, don't keep the API package
standalone.removeIf(s -> s.contains("# this is the keep api"));
```

In the `standalone` variant every API method is called just `a()`, `b()`, `c()`. Confirmed
with `javap` against a real `standalone` JAR (dysnasia/baritone-26.2, tag `26.2`):

```java
// baritone.api.BaritoneAPI - standalone
public static IBaritoneProvider a();   // was getProvider()
public static Settings a();            // was getSettings()

// baritone.api.IBaritone - standalone: 17 methods, ALL of them "a()"
public abstract baritone.process.BuilderProcess a();   // was getBuilderProcess()
public abstract baritone.behavior.PathingBehavior a(); // was getPathingBehavior()
// ... 15 more
```

Two consequences, both hard:

1. **Compiling against it is impossible.** Methods that differ only in return type are valid
   bytecode, but not callable from Java source.
2. **Incompatible at runtime, too.** A mod compiled against the `api` variant throws a
   `NoSuchMethodError` against an installed `standalone` JAR. So the user has to *install*
   the `api` or `unoptimized` variant as well.

On top of that, ProGuard shrinks unused methods away entirely in the `standalone` variant -
`IBuilderProcess` loses `getApproxPlaceable()`, `getMinLayer()`, `getMaxLayer()` and
`isPaused()` among others. For phase 5 (the material check through `getApproxPlaceable()`)
the `api` variant is therefore mandatory, not merely convenient.

That is why `BaritoneAdapter` catches `LinkageError` and reports
`BuildRequestResult.BARITONE_WITHOUT_API` instead of taking the client down with it.

### Two valid mod IDs

Since both `baritone` (the dysnasia standalone, verified from the source) and
`baritone-meteor` (this JAR) are genuinely in circulation, `BaritoneAdapter.isAvailable()`
checks for both IDs. `BaritoneAPI`'s static initialiser instantiates `BaritoneProvider` on
first class access (not through Fabric's entrypoint mechanism) - which explains why the Meteor
JAR works despite its empty `"entrypoints": {}`, as soon as any code (ours included) calls
`BaritoneAPI.getProvider()`.

### EditBox / Button (phase 3, verified)

```java
public EditBox(Font, int x, int y, int width, int height, Component narrationMessage);
public void setResponder(Consumer<String>);
public void setHint(Component);

public static Button.Builder Button.builder(Component, Button.OnPress);  // OnPress.onPress(Button)
```

## 8b. Material check, cancelling and progress (phase 5)

### The material check: Litematica rather than Baritone

Baritone's `IBuilderProcess.getApproxPlaceable()` is, by its own javadoc,
*"updated every tick, but only while the builder process is active"* — so it only returns
data **after** the build is running, which makes it useless for a check *before* the start.
For phase 5 it is unusable.

Litematica, on the other hand, has a synchronous path. Important: the obvious route through
`SchematicPlacement.getMaterialList()` is **not** it — its `reCreateMaterialList()` merely
schedules a `TaskCountBlocksPlacement` in the `TaskScheduler` (asynchronous, across many
ticks, with a chat message of its own,
`litematica.message.scheduled_task_added`). Instead:

```java
// fi.dy.masa.litematica.materials.MaterialListUtils - all static and synchronous
public static List<MaterialListEntry> createMaterialListFor(LitematicaSchematic);
public static void updateAvailableCounts(List<MaterialListEntry>, Player);

// fi.dy.masa.litematica.materials.MaterialListEntry
public ItemStack getStack();
public int getCountTotal();
public int getCountMissing();
public int getCountAvailable();
```

The semantics, from the bytecode of `createMaterialListFor(schematic, regionNames)`: it
iterates every sub-region container (`getSubRegionContainer(name).get(x,y,z)` across the full
volume), counts each `BlockState` into a `countsTotal` map, then passes `countsTotal.clone()`
as *countsMissing* and an **empty** map as *countsMismatched*.

From which it follows: **`countMissing == countTotal`** on this path. It is a build-from-scratch
list without any comparison against the world - "missing" here does *not* mean "missing from
the inventory". The actual shortfall is:

```java
shortfall = max(0, entry.getCountTotal() - entry.getCountAvailable())
```

`updateAvailableCounts(...)` reads nothing but `Player.getInventory()` and sets
`countAvailable`. Two consequences:

- **No special case for creative.** A creative player with an empty inventory would be told
  "everything is missing". The check has to be skipped in creative mode.
- The cost is proportional to the schematic volume (a pure in-memory pass, no world access).
  Not critical for the usual schematics; for very large ones, a noticeable one-off hitch in
  the client tick.

### Cancelling

```java
// baritone.api.behavior.IPathingBehavior
public abstract boolean cancelEverything();
public abstract void forceCancel();
public abstract boolean isPathing();

// baritone.api.process.IBaritoneProcess (inherited by IBuilderProcess)
public abstract boolean isActive();
```

`cancelEverything()` is the route Baritone's own `#stop` command takes as well.

### Progress: no source for it in Baritone

`IBuilderProcess` has **no** progress method. The complete list (from the API-preserving JAR):

```
build×3, buildOpenSchematic, buildOpenLitematic, pause, isPaused, resume,
clearArea, getApproxPlaceable, getMinLayer, getMaxLayer
```

A real percentage ("X of Y blocks placed") cannot be derived from that. The only clean source
would be Litematica's `SchematicVerifier`
(`SchematicPlacement.getSchematicVerifier()` / `hasVerifier()`), which likewise runs
asynchronously through the `TaskScheduler`. See the open questions.

## 9. Open questions

- A keybind category of our own instead of `Category.MISC` (see section 3).
- The key press itself cannot be verified in this environment: the client starts headless
  under Xvfb as far as the main menu, but there is no keyboard input. The
  `/autobuildgui list` command (phase 2) goes untested in real chat for the same reason,
  but it is Brigadier code following the same pattern as Fabric API's own
  `ClientCommandTest`.
- Phase 3 (`AutobuildScreen`, `PlacementListWidget`) is compiled against the real 26.2 API
  and matches a real Fabric API 26.2 test mod (`ChannelList`/`ChannelScreen`), but is
  **not visually checked**: there is no `xdotool`/screenshot tooling in this sandbox to
  actually open the screen by a key press and see the layout (row height, scrollbar gap,
  text truncation) on screen. Please check it in the real game, especially with long
  placement names.
- **The progress bar shows no percentage** (phase 5). Baritone's `IBuilderProcess` offers
  no progress information, so the bar is indeterminate (a travelling segment = "running").
  A real percentage would need Litematica's `SchematicVerifier`
  (`SchematicPlacement.getSchematicVerifier()`), which runs asynchronously through the
  `TaskScheduler` and produces chat messages of its own.
  **Decided: it stays indeterminate for now.** Whether the async complexity and the foreign
  chat messages are bearable can only be judged in the game — noted down for *after* the
  first working release, explicitly not for phase 6.
- The material check runs **when the menu is opened**, not continuously. If the inventory
  changes while the menu is open, the row badges are stale; the click checks again before
  it builds, though, so it cannot build on stale data.
- For a partially built placement the shortfall is pessimistic: Litematica's synchronous
  path counts what a build from zero needs, without looking at the world (see section 8b).
- Whether the click on ✕ has the right hit area cannot be checked headless.
- The keybind deliberately does not live in `config/autobuildgui.json`: Minecraft stores key
  bindings itself in `options.txt`, and a second source would overwrite the change made in
  the controls menu on the next start. So it is bound through Options &rarr; Controls. That
  makes the phase 6 item "config (keybind, ...)" deliberately solved differently from its
  literal description.
