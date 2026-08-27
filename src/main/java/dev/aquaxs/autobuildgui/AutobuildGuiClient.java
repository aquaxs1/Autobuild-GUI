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
		// Load early, so that config/autobuildgui.json exists right after the first
		// start rather than only once the menu is opened for the first time.
		AutobuildConfig.get();

		openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key." + AutobuildGui.MOD_ID + ".open_menu",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_B,
				KeyMapping.Category.MISC
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// consumeClick() drains the queue, hence while rather than if:
			// several presses within one tick must not open the menu several times.
			boolean pressed = false;

			while (openMenuKey.consumeClick()) {
				pressed = true;
			}

			// Only inside a world: without a player there would be neither placements nor
			// an inventory to check, and the material check needs a player.
			if (pressed && client.gui.screen() == null && client.player != null && client.level != null) {
				client.setScreenAndShow(new AutobuildScreen());
			}
		});

		AutobuildCommands.register();

		AutobuildGui.LOGGER.info("Autobuild GUI initialised");
	}
}
