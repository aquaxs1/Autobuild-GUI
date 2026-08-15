package dev.aquaxs.autobuildgui.gui;

import dev.aquaxs.autobuildgui.litematica.PlacementInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Scrollbare Liste der Litematica-Placements. Klick auf eine Zeile selektiert
 * sie (Standardverhalten von {@link ObjectSelectionList}, über
 * {@code setFocused} -&gt; {@code setSelected}) und meldet den Klick zusätzlich
 * an {@link #setOnPlacementClicked(Consumer)}, damit der Screen den Build
 * auslösen kann.
 */
public class PlacementListWidget extends ObjectSelectionList<PlacementListWidget.Entry> {
	private List<PlacementInfo> allPlacements = List.of();
	private String filter = "";
	private Consumer<PlacementInfo> onPlacementClicked = info -> {
	};

	public PlacementListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
		super(minecraft, width, height, y, itemHeight);
	}

	public void setOnPlacementClicked(Consumer<PlacementInfo> listener) {
		this.onPlacementClicked = listener;
	}

	public void setPlacements(List<PlacementInfo> placements) {
		this.allPlacements = placements;
		rebuild();
	}

	public void setFilter(String filter) {
		this.filter = filter.toLowerCase(Locale.ROOT);
		rebuild();
	}

	private void rebuild() {
		PlacementInfo previouslySelected = getSelected() != null ? getSelected().info : null;

		clearEntries();

		for (PlacementInfo placement : allPlacements) {
			if (filter.isEmpty() || placement.name().toLowerCase(Locale.ROOT).contains(filter)) {
				Entry entry = new Entry(placement);
				addEntry(entry);

				if (placement.equals(previouslySelected)) {
					setSelected(entry);
				}
			}
		}
	}

	@Override
	public int getRowWidth() {
		// Standardmäßig sind Zeilen 220px breit und zentriert - für Name, Größe,
		// Blockanzahl und Status-Badge nebeneinander brauchen wir die volle Breite,
		// abzüglich der Scrollbar rechts.
		return getWidth() - AbstractScrollArea.SCROLLBAR_WIDTH - 4;
	}

	public final class Entry extends ObjectSelectionList.Entry<Entry> {
		private static final int ICON_SIZE = 20;
		private static final int ICON_BORDER_COLOR = 0xFF8B8B8B;
		private static final int ICON_FILL_COLOR = 0xFF3F3F3F;
		private static final int TEXT_PADDING = 5;
		private static final String STATUS_UNKNOWN = "Bereit";

		private final PlacementInfo info;

		private Entry(PlacementInfo info) {
			this.info = info;
		}

		public PlacementInfo info() {
			return info;
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				PlacementListWidget.this.onPlacementClicked.accept(info);
			}

			return super.mouseClicked(event, doubleClick);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			var font = PlacementListWidget.this.minecraft.font;

			int iconX = getContentX();
			int iconY = getContentYMiddle() - ICON_SIZE / 2;
			graphics.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, ICON_BORDER_COLOR);
			graphics.fill(iconX + 1, iconY + 1, iconX + ICON_SIZE - 1, iconY + ICON_SIZE - 1, ICON_FILL_COLOR);

			Component badge = Component.literal(STATUS_UNKNOWN).withStyle(ChatFormatting.GRAY);
			int badgeX = getContentRight() - font.width(badge);

			int textX = iconX + ICON_SIZE + TEXT_PADDING;
			int nameY = getContentY() + 1;
			int subtitleY = nameY + font.lineHeight + 1;

			int nameMaxWidth = Math.max(0, badgeX - textX - TEXT_PADDING);
			String displayName = font.plainSubstrByWidth(info.name(), nameMaxWidth);
			String subtitle = "%dx%dx%d - %d Blöcke".formatted(info.sizeX(), info.sizeY(), info.sizeZ(), info.totalBlocks());

			graphics.text(font, displayName, textX, nameY, 0xFFFFFFFF);
			graphics.text(font, Component.literal(subtitle).withStyle(ChatFormatting.GRAY), textX, subtitleY, 0xFFFFFFFF);
			graphics.text(font, badge, badgeX, nameY, 0xFFFFFFFF);
		}

		@Override
		public Component getNarration() {
			return Component.literal(info.name());
		}
	}
}
