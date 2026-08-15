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
 * Phase 2: gibt die von {@link LitematicaAdapter} gelesene Placement-Liste per
 * Chat und Log aus, damit der Adapter ohne GUI testbar ist. Der Befehl entfällt
 * nicht in Phase 3 - er bleibt als Diagnose-Werkzeug nützlich.
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
			source.sendError(Component.literal("Litematica nicht gefunden."));
			AutobuildGui.LOGGER.warn("autobuildgui list: Litematica ist nicht geladen");
			return;
		}

		List<PlacementInfo> placements = LitematicaAdapter.getPlacements();

		if (placements.isEmpty()) {
			source.sendFeedback(Component.literal("Keine Schematic-Placements geladen."));
			AutobuildGui.LOGGER.info("autobuildgui list: keine Placements geladen");
			return;
		}

		source.sendFeedback(Component.literal(placements.size() + " Placement(s) geladen:"));

		for (PlacementInfo placement : placements) {
			String line = "[%d] %s - %dx%dx%d, %d Blöcke, Ursprung %s".formatted(
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
