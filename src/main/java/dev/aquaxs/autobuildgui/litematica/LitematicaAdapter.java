package dev.aquaxs.autobuildgui.litematica;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.loader.api.FabricLoader;
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
