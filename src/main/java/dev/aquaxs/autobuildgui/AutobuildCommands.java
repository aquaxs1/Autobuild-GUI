package dev.aquaxs.autobuildgui;

import com.mojang.brigadier.CommandDispatcher;
import dev.aquaxs.autobuildgui.litematica.LitematicaAdapter;
import dev.aquaxs.autobuildgui.litematica.PlacementInfo;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Phase 2: prints the placement list read by {@link LitematicaAdapter} to chat and to
 * the log, so the adapter can be tested without a GUI. The command does not go away in
 * phase 3 - it stays useful as a diagnostic tool.
 */
public final class AutobuildCommands {
	private AutobuildCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register(AutobuildCommands::register);
	}

	private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(ClientCommands.literal("autobuildgui")
				.then(ClientCommands.literal("list").executes(context -> {
					listPlacements(context.getSource());
					return 0;
				})));
	}

	private static void listPlacements(FabricClientCommandSource source) {
		if (!LitematicaAdapter.isAvailable()) {
			source.sendError(Component.literal("Litematica not found."));
			AutobuildGui.LOGGER.warn("autobuildgui list: Litematica is not loaded");
			return;
		}

		List<PlacementInfo> placements = LitematicaAdapter.getPlacements();

		if (placements.isEmpty()) {
			source.sendFeedback(Component.literal("No schematic placements loaded."));
			AutobuildGui.LOGGER.info("autobuildgui list: no placements loaded");
			return;
		}

		source.sendFeedback(Component.literal(placements.size() + " placement(s) loaded:"));

		for (PlacementInfo placement : placements) {
			String line = "[%d] %s - %dx%dx%d, %d blocks, origin %s".formatted(
					placement.index(),
					placement.name(),
					placement.sizeX(), placement.sizeY(), placement.sizeZ(),
					placement.totalBlocks(),
					placement.origin().toShortString()
			);

			source.sendFeedback(Component.literal(line));
			AutobuildGui.LOGGER.info(line);
		}
	}
}
