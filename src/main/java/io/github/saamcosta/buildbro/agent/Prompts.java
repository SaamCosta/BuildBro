package io.github.saamcosta.buildbro.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The system prompt: the shared rules plus the part for the chosen level of detail. */
public final class Prompts {
	private static final String SYSTEM = "/assets/buildbro/prompts/system.md";

	private Prompts() {
	}

	public static String system(DetailLevel detail) {
		return read(SYSTEM) + "\n\n" + read(detail.promptResource());
	}

	private static String read(String resource) {
		try (InputStream in = Prompts.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("Missing resource " + resource);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
