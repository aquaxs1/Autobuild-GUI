package dev.aquaxs.autobuildgui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.aquaxs.autobuildgui.AutobuildGui;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The mod's settings, stored as JSON in {@code config/autobuildgui.json}.
 *
 * <p>The keybind deliberately does <em>not</em> live here: Minecraft already manages key
 * bindings itself and stores them in {@code options.txt}. A second source in this file
 * would overwrite the change made in the controls menu on the next start. The keybind is
 * changed through Options &rarr; Controls.
 */
public final class AutobuildConfig {
	private static final String FILE_NAME = "autobuildgui.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static AutobuildConfig instance;

	/** Whether the menu closes as soon as a build has been started. */
	private boolean closeScreenOnBuildStart = true;

	/**
	 * Whether the inventory is checked before a build starts.
	 *
	 * <p>The check walks the complete schematic volume in memory. For very large
	 * schematics that is a noticeable one-off hitch when the menu opens - anyone who does
	 * not want it turns it off here and builds at their own risk.
	 */
	private boolean materialCheckEnabled = true;

	public boolean closeScreenOnBuildStart() {
		return closeScreenOnBuildStart;
	}

	public boolean materialCheckEnabled() {
		return materialCheckEnabled;
	}

	public static AutobuildConfig get() {
		if (instance == null) {
			instance = load();
		}

		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	private static AutobuildConfig load() {
		Path path = path();

		if (!Files.exists(path)) {
			AutobuildConfig fresh = new AutobuildConfig();
			fresh.save();
			return fresh;
		}

		try {
			String json = Files.readString(path, StandardCharsets.UTF_8);
			AutobuildConfig loaded = GSON.fromJson(json, AutobuildConfig.class);

			if (loaded == null) {
				AutobuildGui.LOGGER.warn("{} is empty, the default values apply.", path);
				return new AutobuildConfig();
			}

			return loaded;
		} catch (IOException | JsonSyntaxException e) {
			// A broken config must not cripple the mod: take the defaults and leave the
			// file alone, so the user can repair it themselves.
			AutobuildGui.LOGGER.error("{} could not be read, the default values apply.", path, e);
			return new AutobuildConfig();
		}
	}

	public void save() {
		Path path = path();

		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			AutobuildGui.LOGGER.error("{} could not be written.", path, e);
		}
	}
}
