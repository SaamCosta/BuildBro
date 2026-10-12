package io.github.saamcosta.buildbro.config;

import java.util.regex.Pattern;

/** The model name ends up on a command line, so only plain ids and aliases are accepted. */
public final class ModelNames {
	private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9.\\-]{0,63}");

	private ModelNames() {
	}

	public static boolean isValid(String name) {
		return VALID.matcher(name).matches();
	}
}
