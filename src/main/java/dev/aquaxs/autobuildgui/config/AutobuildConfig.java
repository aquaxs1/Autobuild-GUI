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
 * Einstellungen des Mods, abgelegt als JSON in {@code config/autobuildgui.json}.
 *
 * <p>Der Keybind steht bewusst <em>nicht</em> hier: Minecraft verwaltet Tastenbelegungen
 * bereits selbst und speichert sie in {@code options.txt}. Eine zweite Quelle in dieser
 * Datei würde beim nächsten Start die Änderung aus dem Steuerungs-Menü überschreiben.
 * Der Keybind wird über Optionen &rarr; Steuerung geändert.
 */
public final class AutobuildConfig {
	private static final String FILE_NAME = "autobuildgui.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static AutobuildConfig instance;

	/** Ob sich das Menü schließt, sobald ein Build gestartet wurde. */
	private boolean closeScreenOnBuildStart = true;

	/**
	 * Ob vor dem Baustart geprüft wird, ob das Inventar reicht.
	 *
	 * <p>Die Prüfung durchläuft das komplette Schematic-Volumen im Speicher. Bei sehr
	 * großen Schematics ist das ein spürbarer Einzel-Hitch beim Öffnen des Menüs - wer
	 * das nicht will, schaltet sie hier ab und baut auf eigenes Risiko.
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
				AutobuildGui.LOGGER.warn("{} ist leer, es gelten die Standardwerte.", path);
				return new AutobuildConfig();
			}

			return loaded;
		} catch (IOException | JsonSyntaxException e) {
			// Kaputte Config darf den Mod nicht lahmlegen: Standardwerte nehmen und die
			// Datei in Ruhe lassen, damit der Nutzer sie selbst reparieren kann.
			AutobuildGui.LOGGER.error("{} konnte nicht gelesen werden, es gelten die Standardwerte.", path, e);
			return new AutobuildConfig();
		}
	}

	public void save() {
		Path path = path();

		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			AutobuildGui.LOGGER.error("{} konnte nicht geschrieben werden.", path, e);
		}
	}
}
