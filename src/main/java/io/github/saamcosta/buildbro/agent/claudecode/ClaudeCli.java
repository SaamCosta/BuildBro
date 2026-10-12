package io.github.saamcosta.buildbro.agent.claudecode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** Finds the {@code claude} executable. */
public final class ClaudeCli {
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

	private ClaudeCli() {
	}

	public static boolean isWindows() {
		return WINDOWS;
	}

	/**
	 * Looks, in order, at the path from the settings, the PATH, the native installer's folder,
	 * and the copy bundled with the Claude desktop app.
	 */
	public static Optional<Path> find(String configuredPath) {
		if (configuredPath != null && !configuredPath.isBlank()) {
			Path path = Path.of(configuredPath.strip());
			return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
		}
		for (Path candidate : candidates()) {
			if (Files.isRegularFile(candidate)) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}

	private static List<Path> candidates() {
		List<String> names = WINDOWS ? List.of("claude.exe", "claude.cmd") : List.of("claude");
		List<Path> candidates = new ArrayList<>();
		String pathVariable = System.getenv("PATH");
		if (pathVariable != null) {
			for (String dir : pathVariable.split(java.io.File.pathSeparator)) {
				if (dir.isBlank()) {
					continue;
				}
				try {
					for (String name : names) {
						candidates.add(Path.of(dir.strip(), name));
					}
				} catch (RuntimeException ignored) {
					// a malformed PATH entry
				}
			}
		}
		Path home = Path.of(System.getProperty("user.home"));
		for (String name : names) {
			candidates.add(home.resolve(".local").resolve("bin").resolve(name));
			candidates.add(home.resolve(".claude").resolve("local").resolve(name));
		}
		desktopAppBundle(home).ifPresent(candidates::add);
		return candidates;
	}

	/** The desktop app keeps Claude Code in a folder per version; take the newest. */
	private static Optional<Path> desktopAppBundle(Path home) {
		Path root;
		String exe;
		if (WINDOWS) {
			String appData = System.getenv("APPDATA");
			root = (appData != null ? Path.of(appData) : home.resolve("AppData").resolve("Roaming")).resolve("Claude").resolve("claude-code");
			exe = "claude.exe";
		} else {
			root = home.resolve("Library").resolve("Application Support").resolve("Claude").resolve("claude-code");
			exe = "claude";
		}
		if (!Files.isDirectory(root)) {
			return Optional.empty();
		}
		try (Stream<Path> versions = Files.list(root)) {
			return versions
					.filter(dir -> Files.isRegularFile(dir.resolve(exe)))
					.max(Comparator.comparing(dir -> Version.parse(dir.getFileName().toString())))
					.map(dir -> dir.resolve(exe));
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	record Version(int[] parts) implements Comparable<Version> {
		static Version parse(String text) {
			String[] pieces = text.split("[.\\-]");
			int[] parts = new int[pieces.length];
			for (int i = 0; i < pieces.length; i++) {
				try {
					parts[i] = Integer.parseInt(pieces[i]);
				} catch (NumberFormatException e) {
					parts[i] = -1;
				}
			}
			return new Version(parts);
		}

		@Override
		public int compareTo(Version other) {
			return java.util.Arrays.compare(parts, other.parts);
		}
	}
}
