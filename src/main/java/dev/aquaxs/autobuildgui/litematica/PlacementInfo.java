package dev.aquaxs.autobuildgui.litematica;

import net.minecraft.core.BlockPos;

/**
 * A Litematica-free view of a loaded schematic placement.
 *
 * @param index       position in Litematica's placement list. Baritone's
 *                    {@code buildOpenLitematic(int)} expects exactly this index.
 * @param name        the placement's display name
 * @param origin      the placement's origin coordinate in the world
 * @param sizeX       width of the schematic (unrotated, from the schematic metadata)
 * @param sizeY       height of the schematic
 * @param sizeZ       depth of the schematic
 * @param totalBlocks number of non-air blocks according to the schematic metadata
 */
public record PlacementInfo(
		int index,
		String name,
		BlockPos origin,
		int sizeX,
		int sizeY,
		int sizeZ,
		int totalBlocks
) {
}
