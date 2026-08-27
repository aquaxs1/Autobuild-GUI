package dev.aquaxs.autobuildgui.baritone;

/**
 * The result of starting a build through {@link BaritoneAdapter#buildPlacement(int)}.
 */
public enum BuildRequestResult {
	/** Baritone has taken the build. */
	STARTED,

	/** No Baritone installed. */
	BARITONE_MISSING,

	/**
	 * Baritone is installed, but without the public {@code baritone.api.*} package.
	 *
	 * <p>Baritone ships in three variants; the {@code standalone} one deliberately drops
	 * the keep rule for {@code baritone.api.**} during its ProGuard run (see
	 * {@code scripts/proguard.pro} and {@code ProguardTask.generateConfigs()} in the
	 * Baritone source). There every API method is called just {@code a()}, {@code b()},
	 * {@code c()}, and the call fails at runtime with a {@link LinkageError}. Only the
	 * {@code api} and {@code unoptimized} variants are usable by other mods.
	 */
	BARITONE_WITHOUT_API
}
