package dev.aquaxs.autobuildgui.baritone;

/**
 * Ergebnis eines Build-Starts über {@link BaritoneAdapter#buildPlacement(int)}.
 */
public enum BuildRequestResult {
	/** Baritone hat den Build übernommen. */
	STARTED,

	/** Kein Baritone installiert. */
	BARITONE_MISSING,

	/**
	 * Baritone ist installiert, aber ohne das öffentliche {@code baritone.api.*}-Paket.
	 *
	 * <p>Baritone wird in drei Varianten ausgeliefert; die {@code standalone}-Variante
	 * entfernt beim ProGuard-Lauf gezielt die Keep-Regel für {@code baritone.api.**}
	 * (siehe {@code scripts/proguard.pro} und {@code ProguardTask.generateConfigs()}
	 * im Baritone-Quellcode). Dort heißen alle API-Methoden nur noch {@code a()},
	 * {@code b()}, {@code c()}, und der Aufruf schlägt zur Laufzeit mit einem
	 * {@link LinkageError} fehl. Nur die {@code api}- und die
	 * {@code unoptimized}-Variante sind für andere Mods nutzbar.
	 */
	BARITONE_WITHOUT_API
}
