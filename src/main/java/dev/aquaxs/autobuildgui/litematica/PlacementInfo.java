package dev.aquaxs.autobuildgui.litematica;

import net.minecraft.core.BlockPos;

/**
 * Modfremde, Litematica-freie Sicht auf ein geladenes Schematic-Placement.
 *
 * @param index       Position in Litematicas Placement-Liste. Baritones
 *                    {@code buildOpenLitematic(int)} erwartet genau diesen Index.
 * @param name        Anzeigename des Placements
 * @param origin      Ursprungskoordinate des Placements in der Welt
 * @param sizeX       Breite der Schematic (unrotiert, aus den Schematic-Metadaten)
 * @param sizeY       Höhe der Schematic
 * @param sizeZ       Tiefe der Schematic
 * @param totalBlocks Anzahl der nicht-luftigen Blöcke laut Schematic-Metadaten
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
