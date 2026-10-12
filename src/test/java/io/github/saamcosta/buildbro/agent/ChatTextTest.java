package io.github.saamcosta.buildbro.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatTextTest {
	@Test
	void removesMarkdown() {
		assertEquals("Casa: base de pedra\n\n• Quarto 1\n• Quarto 2",
				ChatText.plain("**Casa:** base de pedra\n\n\n\n- Quarto 1\n* Quarto 2"));
		assertEquals("use oak_stairs", ChatText.plain("use `oak_stairs`"));
		assertEquals("Título\ntexto", ChatText.plain("# Título\ntexto"));
	}

	@Test
	void leavesPlainTextAlone() {
		assertEquals("Construí a casa em x=10, z=-4.", ChatText.plain("  Construí a casa em x=10, z=-4.  "));
		assertEquals("2 * 3 = 6", ChatText.plain("2 * 3 = 6"));
	}
}
