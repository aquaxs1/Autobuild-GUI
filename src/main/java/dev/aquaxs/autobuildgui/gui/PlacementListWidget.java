package dev.aquaxs.autobuildgui.gui;

import dev.aquaxs.autobuildgui.litematica.MaterialCheck;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The scrollable list of Litematica placements.
 *
 * <p>Every row shows a status on the right: "Ready", "N blocks missing" (the click is
 * locked then) or - for the placement currently being built - a running indicator with a
 * ✕ to cancel.
 */
public class PlacementListWidget extends ObjectSelectionList<PlacementListWidget.Entry> {
	/** No placement is currently being built. */
	public static final int NO_ACTIVE_BUILD = -1;

	private List<PlacementInfo> allPlacements = List.of();
	private final Map<Integer, MaterialCheck> materialChecks = new LinkedHashMap<>();
	private String filter = "";
	private int activeBuildIndex = NO_ACTIVE_BUILD;

	private Consumer<PlacementInfo> onPlacementClicked = info -> {
	};
	private IntConsumer onCancelClicked = index -> {
	};

	public PlacementListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
		super(minecraft, width, height, y, itemHeight);
	}

	public void setOnPlacementClicked(Consumer<PlacementInfo> listener) {
		this.onPlacementClicked = listener;
	}

	public void setOnCancelClicked(IntConsumer listener) {
		this.onCancelClicked = listener;
	}

	/**
	 * @param placements     the placements to show
	 * @param materialChecks the inventory check per placement index; entries may be
	 *                       absent, in which case the row counts as ready.
	 */
	public void setPlacements(List<PlacementInfo> placements, Map<Integer, MaterialCheck> materialChecks) {
		this.allPlacements = placements;
		this.materialChecks.clear();
		this.materialChecks.putAll(materialChecks);
		rebuild();
	}

	public void setActiveBuildIndex(int index) {
		this.activeBuildIndex = index;
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
		// By default rows are 220px wide and centred - for name, size, block count and
		// status badge side by side we need the full width, minus the scrollbar on the
		// right.
		return getWidth() - AbstractScrollArea.SCROLLBAR_WIDTH - 4;
	}

	public final class Entry extends ObjectSelectionList.Entry<Entry> {
		private static final int ICON_SIZE = 20;
		private static final int ICON_BORDER_COLOR = 0xFF8B8B8B;
		private static final int ICON_FILL_COLOR = 0xFF3F3F3F;
		private static final int TEXT_PADDING = 5;

		private static final int CANCEL_SIZE = 11;
		private static final int BAR_WIDTH = 60;
		private static final int BAR_HEIGHT = 5;
		private static final int BAR_GAP = 4;
		private static final int BAR_BACKGROUND_COLOR = 0xFF1E1E1E;
		private static final int BAR_BORDER_COLOR = 0xFF8B8B8B;
		private static final int BAR_FILL_COLOR = 0xFF4CAF50;
		private static final int CANCEL_COLOR = 0xFFFF5555;

		/** One full round of the running bar, in milliseconds. */
		private static final long BAR_CYCLE_MILLIS = 1400L;
		/** Width of the travelling segment, as a fraction of the bar width. */
		private static final float BAR_SEGMENT_FRACTION = 0.35f;

		private final PlacementInfo info;

		private Entry(PlacementInfo info) {
			this.info = info;
		}

		public PlacementInfo info() {
			return info;
		}

		public boolean isBuilding() {
			return activeBuildIndex == info.index();
		}

		/**
		 * @return the number of missing blocks, or 0 when nothing is missing or no check
		 * ran.
		 */
		public int missingBlocks() {
			MaterialCheck check = materialChecks.get(info.index());
			return check == null || check.isSatisfied() ? 0 : check.missingBlocks();
		}

		public boolean isBlocked() {
			return !isBuilding() && missingBlocks() > 0;
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				if (isBuilding()) {
					if (isOverCancel(event.x(), event.y())) {
						PlacementListWidget.this.onCancelClicked.accept(info.index());
					}
				} else {
					PlacementListWidget.this.onPlacementClicked.accept(info);
				}
			}

			return super.mouseClicked(event, doubleClick);
		}

		private int cancelX() {
			return getContentRight() - CANCEL_SIZE;
		}

		private int cancelY() {
			return getContentYMiddle() - CANCEL_SIZE / 2;
		}

		private boolean isOverCancel(double mouseX, double mouseY) {
			int x = cancelX();
			int y = cancelY();
			return mouseX >= x && mouseX < x + CANCEL_SIZE
					&& mouseY >= y && mouseY < y + CANCEL_SIZE;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			var font = PlacementListWidget.this.minecraft.font;

			int iconX = getContentX();
			int iconY = getContentYMiddle() - ICON_SIZE / 2;
			graphics.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, ICON_BORDER_COLOR);
			graphics.fill(iconX + 1, iconY + 1, iconX + ICON_SIZE - 1, iconY + ICON_SIZE - 1, ICON_FILL_COLOR);

			int textX = iconX + ICON_SIZE + TEXT_PADDING;
			int nameY = getContentY() + 1;
			int subtitleY = nameY + font.lineHeight + 1;

			int statusLeft = isBuilding()
					? extractRunningStatus(graphics, nameY)
					: extractBadge(graphics, font, nameY);

			int nameMaxWidth = Math.max(0, statusLeft - textX - TEXT_PADDING);
			String displayName = font.plainSubstrByWidth(info.name(), nameMaxWidth);
			Component subtitle = Component.translatable("gui.autobuildgui.placement_size",
					info.sizeX(), info.sizeY(), info.sizeZ(), info.totalBlocks());

			graphics.text(font, displayName, textX, nameY, 0xFFFFFFFF);
			graphics.text(font, subtitle.copy().withStyle(ChatFormatting.GRAY), textX, subtitleY, 0xFFFFFFFF);
		}

		/**
		 * Draws the status text on the right.
		 *
		 * @return the left edge of the status, so the name is truncated before it.
		 */
		private int extractBadge(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int nameY) {
			int missing = missingBlocks();
			Component badge = missing > 0
					? Component.translatable("gui.autobuildgui.blocks_missing", missing).withStyle(ChatFormatting.RED)
					: Component.translatable("gui.autobuildgui.status_ready").withStyle(ChatFormatting.GRAY);

			int badgeX = getContentRight() - font.width(badge);
			graphics.text(font, badge, badgeX, nameY, 0xFFFFFFFF);
			return badgeX;
		}

		/**
		 * Draws the running bar and the ✕ for the placement currently being built.
		 *
		 * <p>The bar is deliberately indeterminate (a travelling segment), not a
		 * percentage: Baritone's {@code IBuilderProcess} offers no progress information
		 * at all, and a made-up percentage would be worse than none.
		 *
		 * @return the left edge of the status area.
		 */
		private int extractRunningStatus(GuiGraphicsExtractor graphics, int nameY) {
			int cancelX = cancelX();
			int cancelY = cancelY();

			// The ✕ as two crossed bars - not a font glyph, so that the hit area matches
			// the drawn symbol exactly.
			for (int i = 0; i < CANCEL_SIZE; i++) {
				graphics.fill(cancelX + i, cancelY + i, cancelX + i + 1, cancelY + i + 1, CANCEL_COLOR);
				graphics.fill(cancelX + i, cancelY + CANCEL_SIZE - 1 - i,
						cancelX + i + 1, cancelY + CANCEL_SIZE - i, CANCEL_COLOR);
			}

			int barRight = cancelX - BAR_GAP;
			int barLeft = barRight - BAR_WIDTH;
			int barTop = getContentYMiddle() - BAR_HEIGHT / 2;
			int barBottom = barTop + BAR_HEIGHT;

			graphics.fill(barLeft, barTop, barRight, barBottom, BAR_BORDER_COLOR);
			graphics.fill(barLeft + 1, barTop + 1, barRight - 1, barBottom - 1, BAR_BACKGROUND_COLOR);

			int innerLeft = barLeft + 1;
			int innerWidth = Math.max(0, (barRight - 1) - innerLeft);
			int segmentWidth = Math.max(1, Math.round(innerWidth * BAR_SEGMENT_FRACTION));
			float phase = (System.currentTimeMillis() % BAR_CYCLE_MILLIS) / (float) BAR_CYCLE_MILLIS;
			int segmentLeft = innerLeft + Math.round(phase * (innerWidth + segmentWidth)) - segmentWidth;

			int clampedLeft = Math.max(innerLeft, segmentLeft);
			int clampedRight = Math.min(innerLeft + innerWidth, segmentLeft + segmentWidth);

			if (clampedRight > clampedLeft) {
				graphics.fill(clampedLeft, barTop + 1, clampedRight, barBottom - 1, BAR_FILL_COLOR);
			}

			return barLeft;
		}

		@Override
		public Component getNarration() {
			return Component.literal(info.name());
		}
	}
}
