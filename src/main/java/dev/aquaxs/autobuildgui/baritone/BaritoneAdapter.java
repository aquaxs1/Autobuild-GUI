package dev.aquaxs.autobuildgui.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import dev.aquaxs.autobuildgui.AutobuildGui;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The only point of contact with Baritone's API in the whole mod.
 *
 * <p>Baritone is a soft dependency: {@link #isAvailable()} checks
 * {@code FabricLoader.isModLoaded(...)} before any Baritone class is touched. Two mod
 * IDs count as valid: {@code baritone} (upstream {@code cabaletta/baritone}) and
 * {@code baritone-meteor} (the line descending from MeteorDevelopment, which the fork
 * {@code dysnasia/baritone-26.2} belongs to as well) - both checked with javap against
 * real JARs.
 *
 * <p>Guarded on top of that against Baritone's {@code standalone} variant, in which the
 * {@code baritone.api.*} package is obfuscated away: see
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
	 * Starts building the placement with the given index - the same index Litematica
	 * keeps it under in its placement list (see
	 * {@link dev.aquaxs.autobuildgui.litematica.PlacementInfo#index()}). A build already
	 * running is replaced by the new one by Baritone itself (the default behaviour of
	 * {@code buildOpenLitematic}, which Baritone's own {@code #litematica} command uses
	 * as well).
	 */
	public static BuildRequestResult buildPlacement(int placementIndex) {
		if (!isAvailable()) {
			return BuildRequestResult.BARITONE_MISSING;
		}

		try {
			IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

			// One build at a time: cancel one already running cleanly before the new one
			// starts. buildOpenLitematic would take over the builder process anyway, but
			// a path already being walked would otherwise stay active.
			baritone.getPathingBehavior().cancelEverything();

			baritone.getBuilderProcess().buildOpenLitematic(placementIndex);
			return BuildRequestResult.STARTED;
		} catch (LinkageError error) {
			// Happens with the standalone variant: the classes are there, but the API
			// methods are called a()/b()/c() in it. Hence LinkageError (NoSuchMethodError
			// among others) rather than an exception - and hence caught here, so that a
			// click does not take the client down with it.
			AutobuildGui.LOGGER.error(
					"Baritone is installed, but without the baritone.api package "
							+ "(probably the standalone variant). Please use the api or "
							+ "unoptimized variant.", error);
			return BuildRequestResult.BARITONE_WITHOUT_API;
		}
	}

	/**
	 * Cancels a running build. Takes the same route as Baritone's own {@code #stop}
	 * command.
	 *
	 * @return {@code false} when Baritone is missing or has no usable API.
	 */
	public static boolean cancelBuild() {
		if (!isAvailable()) {
			return false;
		}

		try {
			BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
			return true;
		} catch (LinkageError error) {
			AutobuildGui.LOGGER.error("Cancel failed: Baritone without the baritone.api package.", error);
			return false;
		}
	}

	/**
	 * @return whether Baritone's builder process is currently active. {@code false} when
	 * Baritone is missing or has no usable API.
	 */
	public static boolean isBuildActive() {
		if (!isAvailable()) {
			return false;
		}

		try {
			return BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().isActive();
		} catch (LinkageError error) {
			return false;
		}
	}
}
