package dev.aquaxs.autobuildgui.gui;

import dev.aquaxs.autobuildgui.litematica.LitematicaAdapter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Das Autobuild-Menü: Suchfeld oben, darunter die scrollbare Liste der
 * geladenen Litematica-Placements. Klick auf eine Zeile selektiert sie nur -
 * der Build-Start kommt in Phase 4.
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
		this.placementList.setPlacements(LitematicaAdapter.getPlacements());
		this.addRenderableWidget(this.placementList);
	}

	private void onSearchChanged(String query) {
		this.placementList.setFilter(query);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, TITLE_TOP_MARGIN, 0xFFFFFFFF);

		if (!LitematicaAdapter.isAvailable()) {
			Component message = Component.translatable("gui.autobuildgui.litematica_missing").withStyle(ChatFormatting.RED);
			graphics.centeredText(this.font, message, this.width / 2, this.height / 2, 0xFFFFFFFF);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
