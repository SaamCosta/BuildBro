package io.github.saamcosta.buildbro.agent;

import java.util.regex.Pattern;

/** Turns what the model writes into plain chat text: the Minecraft chat shows markdown as raw symbols. */
public final class ChatText {
	private static final Pattern EMPHASIS = Pattern.compile("(\\*\\*|__|`)");
	private static final Pattern HEADING = Pattern.compile("(?m)^[ \\t]{0,3}#{1,6}[ \\t]+");
	private static final Pattern BULLET = Pattern.compile("(?m)^[ \\t]*[-*+][ \\t]+");
	private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

	private ChatText() {
	}

	public static String plain(String text) {
		String plain = EMPHASIS.matcher(text).replaceAll("");
		plain = HEADING.matcher(plain).replaceAll("");
		plain = BULLET.matcher(plain).replaceAll("• ");
		plain = BLANK_LINES.matcher(plain).replaceAll("\n\n");
		return plain.strip();
	}
}
