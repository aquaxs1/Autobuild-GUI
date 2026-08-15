package dev.aquaxs.autobuildgui.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Einziger Berührungspunkt mit Baritones API im gesamten Mod.
 *
 * <p>Baritone ist eine Soft-Dependency: {@link #isAvailable()} prüft
 * {@code FabricLoader.isModLoaded(...)}, bevor irgendeine Baritone-Klasse
 * angefasst wird. Zwei Mod-IDs gelten als gültig: {@code baritone} (der
 * eigenständige Fabric-Mod, z.B. dysnasia/baritone-26.2) und
 * {@code baritone-meteor} (Baritone eingebettet in Meteor Client) - die
 * öffentliche {@code baritone.api.*}-Oberfläche ist bei beiden identisch,
 * per javap gegen echte JARs beider Herkünfte geprüft.
 */
public final class BaritoneAdapter {
	private static final String[] MOD_IDS = {"baritone", "baritone-meteor"};

	private BaritoneAdapter() {
	}

	public static boolean isAvailable() {
		FabricLoader loader = FabricLoader.getInstance();

		for (String modId : MOD_IDS) {
			if (loader.isModLoaded(modId)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Startet den Bau des Placements mit dem gegebenen Index - derselbe Index,
	 * unter dem Litematica es in seiner Placement-Liste führt (siehe
	 * {@link dev.aquaxs.autobuildgui.litematica.PlacementInfo#index()}).
	 * Ein bereits laufender Build wird von Baritone selbst durch den neuen
	 * ersetzt (Standardverhalten von {@code buildOpenLitematic}, wie es auch
	 * Baritones eigener {@code #litematica}-Befehl nutzt).
	 *
	 * @return {@code false}, wenn Baritone nicht geladen ist - dann wurde nichts
	 * ausgelöst.
	 */
	public static boolean buildPlacement(int placementIndex) {
		if (!isAvailable()) {
			return false;
		}

		IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
		baritone.getBuilderProcess().buildOpenLitematic(placementIndex);
		return true;
	}
}
