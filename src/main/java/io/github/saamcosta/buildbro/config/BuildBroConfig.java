package io.github.saamcosta.buildbro.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.saamcosta.buildbro.BuildBro;
import io.github.saamcosta.buildbro.agent.DetailLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Player settings, stored in {@code config/buildbro.json}.
 * Never put credentials here: this file ends up in modpacks, backups and screenshots.
 */
public final class BuildBroConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Model alias or full id passed to {@code claude --model}; the alias always resolves to the newest one. */
	public String model = "sonnet";
	public DetailLevel detail = DetailLevel.DETAILED;
	/** Most blocks a single request may change. */
	public int blockLimit = 100_000;
	/** Most blocks (horizontally) a build may reach from where the player stood when asking. */
	public int radius = 384;
	/** Full path to the {@code claude} executable; empty means search for it. */
	public String claudePath = "";

	private transient Path file;

	public static BuildBroConfig load(Path file) {
		BuildBroConfig config = null;
		if (Files.isRegularFile(file)) {
			try {
				config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), BuildBroConfig.class);
			} catch (IOException | JsonParseException e) {
				BuildBro.LOGGER.warn("Could not read {}, using defaults", file, e);
			}
		}
		if (config == null) {
			config = new BuildBroConfig();
		}
		config.file = file;
		config.sanitize();
		return config;
	}

	public void save() {
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			BuildBro.LOGGER.warn("Could not save {}", file, e);
		}
	}

	private void sanitize() {
		if (model == null || !ModelNames.isValid(model)) {
			model = "sonnet";
		}
		if (detail == null) {
			detail = DetailLevel.DETAILED;
		}
		blockLimit = Math.clamp(blockLimit, 1, 1_000_000);
		radius = Math.clamp(radius, 16, 4096);
		if (claudePath == null) {
			claudePath = "";
		}
	}
}
