package dev.aquaxs.autobuildgui.gui;

import dev.aquaxs.autobuildgui.baritone.BaritoneAdapter;
import dev.aquaxs.autobuildgui.baritone.BuildRequestResult;
import dev.aquaxs.autobuildgui.config.AutobuildConfig;
import dev.aquaxs.autobuildgui.litematica.LitematicaAdapter;
import dev.aquaxs.autobuildgui.litematica.MaterialCheck;
import dev.aquaxs.autobuildgui.litematica.PlacementInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The autobuild menu: the search box at the top, below it the scrollable list of loaded
 * Litematica placements.
 *
 * <p>On opening, each placement is checked for whether the inventory covers a complete
 * build. Rows that fall short are locked. A click on a free row starts the build through
 * {@link BaritoneAdapter} and closes the screen; the placement currently being built
 * shows a running indicator with a ✕ to cancel when the screen is opened again.
 */
public class AutobuildScreen extends Screen {
	private static final int TITLE_TOP_MARGIN = 15;
	private static final int SIDE_MARGIN = 20;
	private static final int SEARCH_TOP = 30;
	private static final int SEARCH_HEIGHT = 20;
	private static final int LIST_GAP = 6;
	private static final int BOTTOM_MARGIN = 20;
	private static final int ROW_HEIGHT = 28;

	private EditBox searchBox;
	private PlacementListWidget placementList;
	private int placementCount;

	/**
	 * Index of the placement whose build we started. Baritone itself only knows "it is
	 * building something", not which placement - hence we remember it here. Static, so
	 * that it survives the screen being opened again.
	 */
	private static int activeBuildIndex = PlacementListWidget.NO_ACTIVE_BUILD;

	public AutobuildScreen() {
		super(Component.translatable("gui.autobuildgui.title"));
	}

	@Override
	protected void init() {
		if (!LitematicaAdapter.isAvailable()) {
			return;
		}

		int contentWidth = this.width - 2 * SIDE_MARGIN;
		int listTop = SEARCH_TOP + SEARCH_HEIGHT + LIST_GAP;
		int listHeight = this.height - listTop - BOTTOM_MARGIN;

		this.searchBox = new EditBox(this.font, SIDE_MARGIN, SEARCH_TOP, contentWidth, SEARCH_HEIGHT,
				Component.translatable("gui.autobuildgui.search"));
		this.searchBox.setHint(Component.translatable("gui.autobuildgui.search"));
		this.searchBox.setResponder(this::onSearchChanged);
		this.addRenderableWidget(this.searchBox);

		this.placementList = new PlacementListWidget(this.minecraft, contentWidth, listHeight, listTop, ROW_HEIGHT);
		this.placementList.setX(SIDE_MARGIN);
		this.placementList.setOnPlacementClicked(this::onPlacementClicked);
		this.placementList.setOnCancelClicked(this::onCancelClicked);
		this.addRenderableWidget(this.placementList);

		refreshPlacements();
	}

	private void refreshPlacements() {
		// If the build has finished in the meantime (or was cancelled from outside), the
		// row should no longer appear as "running".
		if (activeBuildIndex != PlacementListWidget.NO_ACTIVE_BUILD && !BaritoneAdapter.isBuildActive()) {
			activeBuildIndex = PlacementListWidget.NO_ACTIVE_BUILD;
		}

		List<PlacementInfo> placements = LitematicaAdapter.getPlacements();
		Map<Integer, MaterialCheck> checks = new LinkedHashMap<>();

		if (AutobuildConfig.get().materialCheckEnabled()) {
			for (PlacementInfo placement : placements) {
				// Synchronous and proportional to the schematic volume - acceptable
				// because it only happens when the menu opens, not per frame.
				checks.put(placement.index(), LitematicaAdapter.checkMaterials(placement.index()));
			}
		}

		this.placementCount = placements.size();
		this.placementList.setActiveBuildIndex(activeBuildIndex);
		this.placementList.setPlacements(placements, checks);
	}

	private void onSearchChanged(String query) {
		this.placementList.setFilter(query);
	}

	private void onPlacementClicked(PlacementInfo placement) {
		// Baritone only gets the index. If Litematica's list has changed since the menu
		// opened, the same index points at a different build - better to reload than to
		// build the wrong thing.
		if (!LitematicaAdapter.isStillCurrent(placement)) {
			sendError(Component.translatable("gui.autobuildgui.placement_changed"));
			refreshPlacements();
			return;
		}

		if (AutobuildConfig.get().materialCheckEnabled()) {
			MaterialCheck check = LitematicaAdapter.checkMaterials(placement.index());

			if (!check.isSatisfied()) {
				sendError(Component.translatable("gui.autobuildgui.blocked_missing", check.missingBlocks()));
				return;
			}
		}

		BuildRequestResult result = BaritoneAdapter.buildPlacement(placement.index());

		if (result == BuildRequestResult.STARTED) {
			activeBuildIndex = placement.index();

			if (AutobuildConfig.get().closeScreenOnBuildStart()) {
				this.onClose();
			} else {
				refreshPlacements();
			}

			return;
		}

		String translationKey = switch (result) {
			case BARITONE_MISSING -> "gui.autobuildgui.baritone_missing";
			case BARITONE_WITHOUT_API -> "gui.autobuildgui.baritone_without_api";
			case STARTED -> throw new IllegalStateException("already handled above");
		};

		sendError(Component.translatable(translationKey));
	}

	private void onCancelClicked(int placementIndex) {
		if (BaritoneAdapter.cancelBuild()) {
			activeBuildIndex = PlacementListWidget.NO_ACTIVE_BUILD;
			refreshPlacements();
		} else {
			sendError(Component.translatable("gui.autobuildgui.cancel_failed"));
		}
	}

	private void sendError(Component message) {
		if (this.minecraft.player != null) {
			this.minecraft.player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, TITLE_TOP_MARGIN, 0xFFFFFFFF);

		Component notice = null;

		if (!LitematicaAdapter.isAvailable()) {
			notice = Component.translatable("gui.autobuildgui.litematica_missing").withStyle(ChatFormatting.RED);
		} else if (placementCount == 0) {
			notice = Component.translatable("gui.autobuildgui.no_placements").withStyle(ChatFormatting.GRAY);
		}

		if (notice != null) {
			graphics.centeredText(this.font, notice, this.width / 2, this.height / 2, 0xFFFFFFFF);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
