package dev.aquaxs.autobuildgui.litematica;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * Einziger Berührungspunkt mit Litematicas Klassen im gesamten Mod.
 *
 * <p>Litematica ist eine Soft-Dependency: {@link #isAvailable()} prüft
 * {@code FabricLoader.isModLoaded(...)}, bevor irgendeine Litematica-Klasse
 * angefasst wird. Aufrufer außerhalb dieses Pakets kennen nur {@link PlacementInfo}
 * und Minecraft-eigene Typen, nie Litematicas API direkt.
 */
public final class LitematicaAdapter {
	private static final String MOD_ID = "litematica";

	private LitematicaAdapter() {
	}

	public static boolean isAvailable() {
		return FabricLoader.getInstance().isModLoaded(MOD_ID);
	}

	/**
	 * @return alle aktuell in Litematica geladenen Schematic-Placements, in der
	 * Reihenfolge, in der Litematicas eigene Placement-Liste (und damit Baritones
	 * {@code buildOpenLitematic(int)}) sie erwartet. Leer, falls Litematica nicht
	 * geladen ist.
	 */
	public static List<PlacementInfo> getPlacements() {
		if (!isAvailable()) {
			return List.of();
		}

		List<SchematicPlacement> placements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();
		List<PlacementInfo> result = new ArrayList<>(placements.size());

		for (int i = 0; i < placements.size(); i++) {
			result.add(toPlacementInfo(i, placements.get(i)));
		}

		return result;
	}

	/**
	 * Prüft synchron, ob das Inventar des Spielers für einen kompletten Bau reicht.
	 *
	 * <p>Nutzt Litematicas eigene Material-Ermittlung
	 * ({@code MaterialListUtils.createMaterialListFor} +
	 * {@code updateAvailableCounts}) und nicht Baritones
	 * {@code getApproxPlaceable()} - letzteres liefert laut eigenem Javadoc erst
	 * Daten, während der Build schon läuft, und taugt für einen Vorab-Check nicht.
	 *
	 * <p>Achtung: Litematicas Liste zählt den Bedarf für einen Bau von Null aus, ohne
	 * die Welt zu betrachten. Bei einem teilweise gebauten Placement fällt der
	 * Fehlbestand daher pessimistisch aus. Die Kosten sind proportional zum
	 * Schematic-Volumen (reiner In-Memory-Durchlauf).
	 */
	public static MaterialCheck checkMaterials(int placementIndex) {
		if (!isAvailable()) {
			return MaterialCheck.SKIPPED;
		}

		LocalPlayer player = Minecraft.getInstance().player;

		// Im Creative-Modus sind Blöcke unbegrenzt verfügbar, Litematica hat dafür
		// aber keine Sonderbehandlung - es zählt stumpf das Inventar. Ohne diese
		// Abkürzung wäre im Creative alles als "fehlt" markiert.
		if (player == null || player.isCreative()) {
			return MaterialCheck.SKIPPED;
		}

		List<SchematicPlacement> placements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();

		if (placementIndex < 0 || placementIndex >= placements.size()) {
			return MaterialCheck.SKIPPED;
		}

		List<MaterialListEntry> entries =
				MaterialListUtils.createMaterialListFor(placements.get(placementIndex).getSchematic());
		MaterialListUtils.updateAvailableCounts(entries, player);

		int missing = 0;

		for (MaterialListEntry entry : entries) {
			// getCountMissing() ist auf diesem Pfad gleich getCountTotal() (Litematica
			// klont die Total-Map als Missing-Map, weil kein Weltvergleich stattfindet).
			// Der echte Fehlbestand ergibt sich erst gegen das Inventar.
			missing += Math.max(0, entry.getCountTotal() - entry.getCountAvailable());
		}

		return new MaterialCheck(missing, false);
	}

	private static PlacementInfo toPlacementInfo(int index, SchematicPlacement placement) {
		LitematicaSchematic schematic = placement.getSchematic();
		SchematicMetadata metadata = schematic.getMetadata();
		Vec3i size = metadata.getEnclosingSize();

		return new PlacementInfo(
				index,
				placement.getName(),
				placement.getOrigin(),
				size.getX(),
				size.getY(),
				size.getZ(),
				metadata.getTotalBlocks()
		);
	}
}
