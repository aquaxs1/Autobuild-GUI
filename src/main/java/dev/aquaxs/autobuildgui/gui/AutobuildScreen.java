package dev.aquaxs.autobuildgui.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Das Autobuild-Menü.
 *
 * <p>Phase 1: nur Titel und Hintergrund. Die Placement-Liste kommt in Phase 3.
 */
public class AutobuildScreen extends Screen {
	private static final int TITLE_TOP_MARGIN = 15;

	public AutobuildScreen() {
		super(Component.translatable("gui.autobuildgui.title"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, TITLE_TOP_MARGIN, 0xFFFFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
