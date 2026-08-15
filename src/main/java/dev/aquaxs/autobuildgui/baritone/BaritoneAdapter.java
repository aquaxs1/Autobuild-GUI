package dev.aquaxs.autobuildgui.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import dev.aquaxs.autobuildgui.AutobuildGui;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Einziger Berührungspunkt mit Baritones API im gesamten Mod.
 *
 * <p>Baritone ist eine Soft-Dependency: {@link #isAvailable()} prüft
 * {@code FabricLoader.isModLoaded(...)}, bevor irgendeine Baritone-Klasse
 * angefasst wird. Zwei Mod-IDs gelten als gültig: {@code baritone} (Upstream
 * {@code cabaletta/baritone}) und {@code baritone-meteor} (die von
 * MeteorDevelopment abstammende Linie, zu der auch der Fork
 * {@code dysnasia/baritone-26.2} gehört) - beide per javap gegen echte JARs
 * geprüft.
 *
 * <p>Zusätzlich abgesichert gegen Baritones {@code standalone}-Variante, in der
 * das {@code baritone.api.*}-Paket wegobfuskiert ist: siehe
 * {@link BuildRequestResult#BARITONE_WITHOUT_API}.
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
	 */
	public static BuildRequestResult buildPlacement(int placementIndex) {
		if (!isAvailable()) {
			return BuildRequestResult.BARITONE_MISSING;
		}

		try {
			IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
			baritone.getBuilderProcess().buildOpenLitematic(placementIndex);
			return BuildRequestResult.STARTED;
		} catch (LinkageError error) {
			// Passiert bei der standalone-Variante: die Klassen sind da, aber die
			// API-Methoden heissen dort a()/b()/c(). Deshalb LinkageError (u.a.
			// NoSuchMethodError) statt Exception - und deshalb hier abgefangen,
			// damit ein Klick nicht den Client mitreisst.
			AutobuildGui.LOGGER.error(
					"Baritone ist installiert, aber ohne das baritone.api-Paket "
							+ "(vermutlich die standalone-Variante). Bitte die api- oder "
							+ "unoptimized-Variante verwenden.", error);
			return BuildRequestResult.BARITONE_WITHOUT_API;
		}
	}
}
