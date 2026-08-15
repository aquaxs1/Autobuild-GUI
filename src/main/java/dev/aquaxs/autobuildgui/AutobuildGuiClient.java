package dev.aquaxs.autobuildgui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aquaxs.autobuildgui.config.AutobuildConfig;
import dev.aquaxs.autobuildgui.gui.AutobuildScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class AutobuildGuiClient implements ClientModInitializer {
	private static KeyMapping openMenuKey;

	@Override
	public void onInitializeClient() {
		// Früh laden, damit config/autobuildgui.json schon nach dem ersten Start
		// existiert und nicht erst, wenn das Menü zum ersten Mal geöffnet wird.
		AutobuildConfig.get();

		openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key." + AutobuildGui.MOD_ID + ".open_menu",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_B,
				KeyMapping.Category.MISC
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// consumeClick() leert die Warteschlange, deshalb while statt if:
			// mehrfaches Druecken innerhalb eines Ticks soll das Menue nicht mehrfach oeffnen.
			boolean pressed = false;

			while (openMenuKey.consumeClick()) {
				pressed = true;
			}

			// Nur in einer Welt: ohne Spieler gäbe es weder Placements noch ein
			// Inventar zu prüfen, und der Material-Check bräuchte einen Spieler.
			if (pressed && client.gui.screen() == null && client.player != null && client.level != null) {
				client.setScreenAndShow(new AutobuildScreen());
			}
		});

		AutobuildCommands.register();

		AutobuildGui.LOGGER.info("Autobuild GUI initialisiert");
	}
}
