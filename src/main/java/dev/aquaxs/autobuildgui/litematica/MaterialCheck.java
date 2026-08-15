package dev.aquaxs.autobuildgui.litematica;

/**
 * Ergebnis der Inventar-Prüfung für ein Placement.
 *
 * @param missingBlocks Anzahl Blöcke, die dem Spieler-Inventar für einen kompletten
 *                      Bau fehlen. {@code 0}, wenn alles da ist.
 * @param skipped       {@code true}, wenn nicht geprüft wurde - im Creative-Modus, wo
 *                      Blöcke unbegrenzt verfügbar sind, oder wenn kein Spieler da ist.
 *                      Dann ist {@code missingBlocks} bedeutungslos und der Build darf
 *                      starten.
 */
public record MaterialCheck(int missingBlocks, boolean skipped) {
	public static final MaterialCheck SKIPPED = new MaterialCheck(0, true);

	/**
	 * @return ob der Build starten darf.
	 */
	public boolean isSatisfied() {
		return skipped || missingBlocks == 0;
	}
}
