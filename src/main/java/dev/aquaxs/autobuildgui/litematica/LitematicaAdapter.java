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
 * The only point of contact with Litematica's classes in the whole mod.
 *
 * <p>Litematica is a soft dependency: {@link #isAvailable()} checks
 * {@code FabricLoader.isModLoaded(...)} before any Litematica class is touched. Callers
 * outside this package only ever see {@link PlacementInfo} and Minecraft's own types,
 * never Litematica's API directly.
 */
public final class LitematicaAdapter {
	private static final String MOD_ID = "litematica";

	private LitematicaAdapter() {
	}

	public static boolean isAvailable() {
		return FabricLoader.getInstance().isModLoaded(MOD_ID);
	}

	/**
	 * @return every schematic placement currently loaded in Litematica, in the order
	 * Litematica's own placement list (and hence Baritone's
	 * {@code buildOpenLitematic(int)}) expects them. Empty when Litematica is not loaded.
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
	 * Checks whether the index recorded in {@code info} still points at the same
	 * placement in Litematica's placement list.
	 *
	 * <p>It matters because Baritone only gets the index: if the user changes the list
	 * while our menu is open (loading, deleting, renaming, moving a placement), the same
	 * index afterwards points at a different build. Without this check a click would
	 * happily build the wrong thing.
	 */
	public static boolean isStillCurrent(PlacementInfo info) {
		if (!isAvailable()) {
			return false;
		}

		List<SchematicPlacement> placements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();

		if (info.index() < 0 || info.index() >= placements.size()) {
			return false;
		}

		SchematicPlacement placement = placements.get(info.index());
		return placement.getName().equals(info.name()) && placement.getOrigin().equals(info.origin());
	}

	/**
	 * Checks synchronously whether the player's inventory covers a complete build.
	 *
	 * <p>Uses Litematica's own material accounting
	 * ({@code MaterialListUtils.createMaterialListFor} +
	 * {@code updateAvailableCounts}) rather than Baritone's {@code getApproxPlaceable()}
	 * - the latter, by its own javadoc, only returns data while the build is already
	 * running, and is no good for a check beforehand.
	 *
	 * <p>Careful: Litematica's list counts what a build from zero needs, without looking
	 * at the world. For a partially built placement the shortfall therefore comes out
	 * pessimistic. The cost is proportional to the schematic volume (a pure in-memory
	 * pass).
	 */
	public static MaterialCheck checkMaterials(int placementIndex) {
		if (!isAvailable()) {
			return MaterialCheck.SKIPPED;
		}

		LocalPlayer player = Minecraft.getInstance().player;

		// In creative mode blocks are unlimited, but Litematica has no special case for
		// that - it bluntly counts the inventory. Without this shortcut everything would
		// be marked "missing" in creative.
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
			// getCountMissing() equals getCountTotal() on this path (Litematica clones the
			// total map as the missing map, because no comparison against the world takes
			// place). The real shortfall only emerges against the inventory.
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
