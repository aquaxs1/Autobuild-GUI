package dev.aquaxs.autobuildgui.litematica;

/**
 * The result of checking the inventory for one placement.
 *
 * @param missingBlocks how many blocks the player's inventory is short of a complete
 *                      build. {@code 0} when everything is there.
 * @param skipped       {@code true} when no check ran - in creative mode, where blocks
 *                      are unlimited, or when there is no player. {@code missingBlocks}
 *                      is then meaningless and the build may start.
 */
public record MaterialCheck(int missingBlocks, boolean skipped) {
	public static final MaterialCheck SKIPPED = new MaterialCheck(0, true);

	/**
	 * @return whether the build may start.
	 */
	public boolean isSatisfied() {
		return skipped || missingBlocks == 0;
	}
}
