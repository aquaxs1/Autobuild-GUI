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

`cabaletta/baritone@26.2` existiert als Branch (HEAD `2991d92`, GitHub-Merge-Commit von
leijurv vom 11.08.2026, PR #5076 von ZacSharp). Es gibt aber **keinen Tag und keinen
Release** dafür — der neueste Tag im Repo ist `v1.15.0`. Es existiert also kein gebautes
Artefakt von upstream.

`dysnasia/baritone-26.2` hat dagegen `refs/tags/26.2` und eine Releases-Seite mit
`baritone-26.2.jar`. **Deshalb: dieses JAR nach `libs/`.**

---

## 6b. Scroll-Liste und Widgets (Phase 3, gegen echtes JAR verifiziert)

Das frühere "offen"-Item ist geklärt: `ObjectSelectionList<E>` existiert in 26.2 unverändert
im Prinzip, nur mit neuer Extract-Pipeline. Verifiziert per `javap` **und** an einem
echten 26.2-Beispiel aus Fabric API selbst (`ChannelList`/`ChannelScreen`,
`fabric-networking-api-v1/src/testmodClient/.../channeltest/`):

```java
// net.minecraft.client.gui.components.AbstractSelectionList<E extends Entry<E>>
//   extends AbstractContainerWidget extends AbstractScrollArea
protected AbstractSelectionList(Minecraft, int width, int height, int y, int itemHeight);
// x ist IMMER 0 im Konstruktor (super(0, y, width, height, ...)) - eigene
// Positionierung nur nachträglich über das geerbte AbstractWidget.setX(int).

public int getRowWidth();      // default: fest 220, zentriert - für breitere
                                // Zeilen überschreiben (Icon+Name+Badge braucht mehr)
public int getRowLeft();       // = getX() + width/2 - getRowWidth()/2
public static final int AbstractScrollArea.SCROLLBAR_WIDTH;  // = 6 (ConstantValue geprüft)

// net.minecraft.client.gui.components.AbstractSelectionList.Entry<E>
public abstract void extractContent(GuiGraphicsExtractor, int mouseX, int mouseY, boolean hovered, float tickDelta);
public int getContentX(); getContentY(); getContentRight(); getContentYMiddle();  // schon Padding-bereinigt

// net.minecraft.client.gui.components.ObjectSelectionList.Entry<E> (zusätzlich)
public abstract Component getNarration();
public boolean mouseClicked(MouseButtonEvent, boolean);   // Default: nur "return true", KEIN eigener Select-Aufruf
```

**Korrektur zu einer früheren Annahme hier:** `ObjectSelectionList.Entry.mouseClicked` selektiert
NICHT selbst (`iconst_1; ireturn` laut Bytecode - reiner No-Op-Rückgabewert). Die Selektion
läuft über einen anderen Pfad: `ContainerEventHandler.mouseClicked` (default-Methode, von der
Liste geerbt) findet die angeklickte Entry über `getChildAt(x,y)`, ruft `entry.mouseClicked(...)`
auf, und wenn das `true` liefert plus `entry.shouldTakeFocusAfterInteraction()`, ruft die Liste
`this.setFocused(entry)` auf sich selbst auf. `AbstractSelectionList.setFocused(...)` ist
überschrieben und ruft darin `setSelected(entry)` auf. Für Phase 4 heißt das: ein eigenes
`Entry.mouseClicked`-Override, das `super.mouseClicked(...)` durchreicht (liefert weiterhin
`true`), bricht die Selektion nicht - der Build-Trigger kommt einfach zusätzlich dazu.

`ChannelList` (Fabric-API-Testmod, kompiliert gegen 26.2) bestätigt die exakte
Parameterreihenfolge von `extractContent` 1:1:

```java
public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta)
```

### Neues Input-Modell

`GuiEventListener.mouseClicked` heißt jetzt `mouseClicked(MouseButtonEvent, boolean doubleClick)`
statt der alten `(double x, double y, int button)` - `MouseButtonEvent` ist ein Record mit
`x()`, `y()`, `button()`. Klick-Weiterleitung an Entries läuft automatisch über
`ContainerEventHandler.mouseClicked(...)` (default-Methode), solange die Entry
`GuiEventListener` implementiert - kein eigener Dispatch-Code nötig.

## 8. Baritone: Bezugsquelle und JAR-Verifikation (Phase 4)

### Empfohlene Quelle: meteorclient.com

**Das in `libs/` verwendete JAR stammt von der offiziellen Downloadseite
`meteorclient.com`, Button „\*Baritone [26.2]".** Das ist der eigenständige
MeteorDevelopment-Fork von Baritone — **kein** Bestandteil des vollen Meteor Clients;
der Utility-Mod muss nicht installiert werden.

| | |
|---|---|
| Quelle | `meteorclient.com`, Download-Button „\*Baritone [26.2]" |
| In diese Session hochgeladen | 2026-08-15, 07:44 UTC |
| SHA-256 | `8a2e7b71229005fd451e5f012f8b9c715e41531631a4a0a53c900a47cd852423` |
| Größe | 4 852 460 Byte |
| Mod-ID / Version | `baritone-meteor` / `26.2-SNAPSHOT` |
| Eingebettet | `META-INF/jars/nether-pathfinder-1.6.jar` |

Das genaue Download-Datum ist hier nicht unabhängig überprüfbar: `meteorclient.com` ist
aus dieser Build-Umgebung nicht erreichbar (Egress-Policy), das JAR kam per Upload.
Die Herkunftsangabe stammt vom Projekt-Owner; nachprüfbar ist der SHA-256 oben — die
Datei in `libs/baritone-26.2.jar` ist byte-identisch mit dem Upload.

Diese Quelle ist die **primäre Empfehlung für die README** (Phase 6), weil sie eine
API-erhaltende Variante liefert (siehe unten).

`dysnasia/baritone-26.2` bleibt als Alternative erwähnenswert, **aber**: dessen aktueller
Release (Tag `26.2`) ist die `standalone`-Variante und hat keine nutzbare API — weder zum
Kompilieren noch zur Laufzeit. Details unten.

### JAR-Verifikation

Baritone-Quellbau aus dem `26.2`-Tag scheiterte an einer wachsenden Kette von Legacy-Hosts
(Unimined selbst brauchte `maven.wagyourtail.xyz` + `launchermeta.mojang.com`, danach zusätzlich
`files.betacraft.uk` und sechs weitere Kandidaten-Repos für `dev.babbaj:nether-pathfinder` -
siehe Konversation). Statt die Freigabeliste beliebig weiter aufzublähen, hat der Nutzer ein
reales `baritone-26.2.jar` aus seiner eigenen Meteor-Client-Installation hochgeladen.

**Wichtig: dieses JAR stammt von `MeteorDevelopment/baritone` (in Meteor eingebettet), nicht von
`dysnasia/baritone-26.2`.** Aus der Manifest-/`fabric.mod.json`-Prüfung:

```
Fabric-Minecraft-Version: 26.2
Fabric-Loader-Version: 0.19.3
mod id: "baritone-meteor"  (nicht "baritone")
version: "26.2-SNAPSHOT"
entrypoints: {}            (leer - initialisiert sich nicht selbst über Fabric)
jars: [ "META-INF/jars/nether-pathfinder-1.6.jar" ]   (echtes Fabric-Jar-in-Jar, mit Loom gebaut)
```

Per `javap` gegen die echten `.class`-Dateien in diesem JAR verifiziert - alle Signaturen decken
sich exakt mit dem `cabaletta/baritone@26.2`-Quellcode:

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
List<BlockState> getApproxPlaceable();   // Kandidat für Phase 5 Material-Check
Optional<Integer> getMinLayer(); getMaxLayer();

// baritone.api.process.IBaritoneProcess (Basisinterface, für Phase 5 relevant)
boolean isActive();
```

`LitematicaCommand.execute(...)` wurde zusätzlich per Bytecode nachvollzogen (nicht nur der
Quellcode): ruft intern weiterhin `baritone.getBuilderProcess().buildOpenLitematic(index - 1)`
auf - die 0-basierte Index-Semantik ist bestätigt, nicht nur angenommen.

### Obfuskierung außerhalb von `baritone.api.*`

`baritone.utils.schematic.litematica.LitematicaHelper` ist in diesem JAR ProGuard-minifiziert:
`isLitematicaPresent()` → `a()`, `hasLoadedSchematic(int)` → `a(int)`, `getSchematic(int)` →
`a(int)` (zwei Overloads namens `a` mit identischem Parametertyp, nur unterschiedlichem
Rückgabetyp - in Java-Quellcode nicht direkt aufrufbar, nur per Reflection). Betrifft uns nicht:
unser eigener `LitematicaAdapter` (Phase 2) liest Litematica direkt, ohne je durch Baritones
`LitematicaHelper` zu gehen. In diesem (Meteor-)JAR bleibt das öffentliche
`baritone.api.*`-Paket unverändert lesbar - es ist eine `api`-Variante.

> **Nachtrag:** Eine frühere Fassung dieses Abschnitts schloss daraus, `baritone.api.*`
> bleibe *generell* erhalten. Das ist falsch und wurde durch ein zweites, echtes JAR
> widerlegt - siehe den folgenden Abschnitt. Ob die API erhalten ist, hängt allein an der
> gewählten Distributionsvariante.

### Baritone liefert DREI Varianten - nur zwei davon sind für andere Mods nutzbar

**Das ist der wichtigste Fund für dieses Projekt.** Aus `BaritoneGradleTask.java`:

```java
ARTIFACT_UNOPTIMIZED = "%s-unoptimized-%s.jar";
ARTIFACT_API         = "%s-api-%s.jar";
ARTIFACT_STANDALONE  = "%s-standalone-%s.jar";
```

Für einen Fabric-Build heißen die Dateien also:

| Datei | `baritone.api.*` | Für uns nutzbar |
|---|---|---|
| `baritone-api-fabric-26.2.jar` | erhalten | **ja** |
| `baritone-unoptimized-fabric-26.2.jar` | erhalten (kein ProGuard) | ja |
| `baritone-standalone-fabric-26.2.jar` | **wegobfuskiert** | **nein** |

Der Unterschied entsteht in `scripts/proguard.pro` Zeile 31:

```
-keep class baritone.api.** { *; } # this is the keep api
```

und `ProguardTask.generateConfigs()`:

```java
// For the Standalone config, don't keep the API package
standalone.removeIf(s -> s.contains("# this is the keep api"));
```

In der `standalone`-Variante heißen alle API-Methoden nur noch `a()`, `b()`, `c()`.
Per `javap` gegen ein echtes `standalone`-JAR (dysnasia/baritone-26.2, Tag `26.2`) bestätigt:

```java
// baritone.api.BaritoneAPI - standalone
public static IBaritoneProvider a();   // war getProvider()
public static Settings a();            // war getSettings()

// baritone.api.IBaritone - standalone: 17 Methoden, ALLE "a()"
public abstract baritone.process.BuilderProcess a();   // war getBuilderProcess()
public abstract baritone.behavior.PathingBehavior a(); // war getPathingBehavior()
// ... 15 weitere
```

Zwei Konsequenzen, beide hart:

1. **Dagegen kompilieren ist unmöglich.** Methoden, die sich nur im Rückgabetyp
   unterscheiden, sind gültiger Bytecode, aber in Java-Quellcode nicht aufrufbar.
2. **Auch zur Laufzeit inkompatibel.** Ein gegen die `api`-Variante kompilierter Mod
   wirft gegen ein installiertes `standalone`-JAR einen `NoSuchMethodError`. Der Nutzer
   muss also ebenfalls die `api`- oder `unoptimized`-Variante *installieren*.

Zusätzlich schrumpft ProGuard in der `standalone`-Variante ungenutzte Methoden ganz weg -
`IBuilderProcess` verliert dort u.a. `getApproxPlaceable()`, `getMinLayer()`,
`getMaxLayer()` und `isPaused()`. Für Phase 5 (Material-Check über `getApproxPlaceable()`)
ist die `api`-Variante damit zwingend, nicht nur bequem.

`BaritoneAdapter` fängt deshalb `LinkageError` ab und meldet
`BuildRequestResult.BARITONE_WITHOUT_API`, statt den Client mitzureißen.

### Zwei gültige Mod-IDs

Da real im Umlauf sowohl `baritone` (dysnasia-Standalone, aus dem Quellcode verifiziert) als auch
`baritone-meteor` (dieses JAR) vorkommen, prüft `BaritoneAdapter.isAvailable()` auf beide IDs.
`BaritoneAPI`s statischer Initialisierer instanziiert `BaritoneProvider` beim ersten
Klassenzugriff (nicht über Fabrics Entrypoint-Mechanismus) - das erklärt, warum das Meteor-JAR
trotz leerem `"entrypoints": {}` funktioniert, sobald irgendein Code (auch unserer)
`BaritoneAPI.getProvider()` aufruft.

### EditBox / Button (Phase 3, verifiziert)

```java
public EditBox(Font, int x, int y, int width, int height, Component narrationMessage);
public void setResponder(Consumer<String>);
public void setHint(Component);

public static Button.Builder Button.builder(Component, Button.OnPress);  // OnPress.onPress(Button)
```

## 8b. Material-Check, Abbruch und Fortschritt (Phase 5)

### Material-Check: Litematica statt Baritone

Baritones `IBuilderProcess.getApproxPlaceable()` ist laut eigenem Javadoc
*"updated every tick, but only while the builder process is active"* — es liefert also
erst Daten, **nachdem** der Build läuft, und taugt damit nicht für eine Prüfung *vor*
dem Start. Für Phase 5 ist es unbrauchbar.

Litematica hat dagegen einen synchronen Pfad. Wichtig: der offensichtliche Weg über
`SchematicPlacement.getMaterialList()` ist es **nicht** — dessen
`reCreateMaterialList()` plant nur einen `TaskCountBlocksPlacement` im `TaskScheduler`
ein (asynchron über viele Ticks, mit eigener Chat-Meldung
`litematica.message.scheduled_task_added`). Stattdessen:

```java
// fi.dy.masa.litematica.materials.MaterialListUtils - alles static und synchron
public static List<MaterialListEntry> createMaterialListFor(LitematicaSchematic);
public static void updateAvailableCounts(List<MaterialListEntry>, Player);

// fi.dy.masa.litematica.materials.MaterialListEntry
public ItemStack getStack();
public int getCountTotal();
public int getCountMissing();
public int getCountAvailable();
```

Semantik aus dem Bytecode von `createMaterialListFor(schematic, regionNames)`:
Es iteriert alle Sub-Region-Container (`getSubRegionContainer(name).get(x,y,z)` über das
volle Volumen), zählt jeden `BlockState` in eine `countsTotal`-Map, übergibt dann
`countsTotal.clone()` als *countsMissing* und eine **leere** Map als *countsMismatched*.

Daraus folgt: **`countMissing == countTotal`** auf diesem Pfad. Es ist eine
Frisch-Bau-Liste ohne Weltvergleich - „missing" heißt hier *nicht* „fehlt im Inventar".
Der tatsächliche Fehlbestand ist:

```java
shortfall = max(0, entry.getCountTotal() - entry.getCountAvailable())
```

`updateAvailableCounts(...)` liest ausschließlich `Player.getInventory()` und setzt
`countAvailable`. Zwei Konsequenzen:

- **Keine Creative-Sonderbehandlung.** Ein Creative-Spieler mit leerem Inventar bekäme
  „alles fehlt". Der Check muss im Creative-Modus übersprungen werden.
- Kosten sind proportional zum Schematic-Volumen (reiner In-Memory-Durchlauf, kein
  Weltzugriff). Für übliche Schematics unkritisch, bei sehr großen ein spürbarer
  Einzel-Hitch im Client-Tick.

### Abbruch

```java
// baritone.api.behavior.IPathingBehavior
public abstract boolean cancelEverything();
public abstract void forceCancel();
public abstract boolean isPathing();

// baritone.api.process.IBaritoneProcess (von IBuilderProcess geerbt)
public abstract boolean isActive();
```

`cancelEverything()` ist der Weg, den auch Baritones eigener `#stop`-Befehl nutzt.

### Fortschritt: keine Quelle in Baritone

`IBuilderProcess` hat **keine** Fortschritts-Methode. Die vollständige Liste (aus dem
API-erhaltenden JAR):

```
build×3, buildOpenSchematic, buildOpenLitematic, pause, isPaused, resume,
clearArea, getApproxPlaceable, getMinLayer, getMaxLayer
```

Eine echte Prozentzahl („X von Y Blöcken gesetzt") ist daraus nicht ableitbar. Die
einzige saubere Quelle wäre Litematicas `SchematicVerifier`
(`SchematicPlacement.getSchematicVerifier()` / `hasVerifier()`), der aber ebenfalls über
den `TaskScheduler` asynchron läuft. Siehe offene Punkte.

## 9. Offene Punkte

- Eigene Keybind-Kategorie statt `Category.MISC` (siehe Abschnitt 3).
- Der Keydruck selbst ist in dieser Umgebung nicht verifizierbar: der Client startet
  headless unter Xvfb bis ins Hauptmenü, aber es gibt keine Tastatureingabe. Der
  `/autobuildgui list`-Command (Phase 2) läuft aus demselben Grund ungetestet im
  echten Chat, ist aber Brigadier-Code nach demselben Muster wie Fabric APIs eigener
  `ClientCommandTest`.
- Phase 3 (`AutobuildScreen`, `PlacementListWidget`) ist gegen die echte 26.2-API
  kompiliert und deckungsgleich mit einem echten Fabric-API-26.2-Testmod
  (`ChannelList`/`ChannelScreen`), aber **visuell nicht geprüft**: kein `xdotool`/
  Screenshot-Tooling in dieser Sandbox, um den Screen tatsächlich per Tastendruck zu
  öffnen und das Layout (Zeilenhöhe, Scrollbar-Abstand, Textkürzung) am Bildschirm zu
  sehen. Bitte im echten Spiel gegenprüfen, insbesondere mit langen Placement-Namen.
- **Fortschrittsbalken zeigt keine Prozentzahl** (Phase 5). Baritones
  `IBuilderProcess` bietet keine Fortschritts-Auskunft, deshalb ist der Balken
  unbestimmt (wanderndes Segment = „läuft"). Eine echte Prozentzahl bräuchte
  Litematicas `SchematicVerifier` (`SchematicPlacement.getSchematicVerifier()`),
  der über den `TaskScheduler` asynchron läuft und eigene Chat-Meldungen erzeugt.
  **Entschieden: bleibt vorerst unbestimmt.** Ob die Async-Komplexität und die
  fremden Chat-Meldungen tragbar sind, lässt sich erst im Spiel beurteilen —
  vorgemerkt für *nach* dem ersten funktionierenden Release, ausdrücklich nicht
  für Phase 6.
- Der Material-Check läuft **beim Öffnen des Menüs**, nicht laufend. Ändert sich das
  Inventar, während das Menü offen ist, sind die Zeilen-Badges veraltet; der Klick
  prüft aber erneut, bevor er baut, kann also nicht auf veralteten Daten bauen.
- Bei einem teilweise gebauten Placement ist der Fehlbestand pessimistisch: Litematicas
  synchroner Pfad zählt den Bedarf für einen Bau von Null aus, ohne die Welt zu
  betrachten (siehe Abschnitt 8b).
- Ob der Klick auf ✕ die richtige Trefferfläche hat, ist headless nicht prüfbar.
- Der Keybind steht bewusst nicht in `config/autobuildgui.json`: Minecraft speichert
  Tastenbelegungen selbst in `options.txt`, eine zweite Quelle würde die Änderung aus
  dem Steuerungs-Menü beim nächsten Start überschreiben. Belegung daher über
  Optionen &rarr; Steuerung. Damit ist der Phase-6-Punkt „Config (Keybind, ...)"
  bewusst anders gelöst als wörtlich beschrieben.
