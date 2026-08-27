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
 * The autobuild menu.
 *
 * <p>Phase 1: the title and the background only. The placement list arrives in phase 3.
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
	 * Index des Placements, dessen Build wir gestartet haben. Baritone kennt selbst
	 * nur "es baut gerade etwas", nicht welches Placement - deshalb merken wir uns das
	 * hier. Statisch, damit es ein erneutes Öffnen des Screens überlebt.
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
		// Ist der Build inzwischen durch (oder von aussen abgebrochen), soll die Zeile
		// nicht weiter als "läuft" erscheinen.
		if (activeBuildIndex != PlacementListWidget.NO_ACTIVE_BUILD && !BaritoneAdapter.isBuildActive()) {
			activeBuildIndex = PlacementListWidget.NO_ACTIVE_BUILD;
		}

		List<PlacementInfo> placements = LitematicaAdapter.getPlacements();
		Map<Integer, MaterialCheck> checks = new LinkedHashMap<>();

		if (AutobuildConfig.get().materialCheckEnabled()) {
			for (PlacementInfo placement : placements) {
				// Synchron und proportional zum Schematic-Volumen - vertretbar, weil es
				// nur beim Öffnen des Menüs passiert, nicht pro Frame.
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
		// Baritone bekommt nur den Index. Hat sich Litematicas Liste seit dem Öffnen
		// geändert, zeigt derselbe Index auf ein anderes Bauwerk - lieber neu laden
		// als das Falsche bauen.
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
			case STARTED -> throw new IllegalStateException("bereits oben behandelt");
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
