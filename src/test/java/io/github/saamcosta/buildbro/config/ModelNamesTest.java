package io.github.saamcosta.buildbro.config;

import io.github.saamcosta.buildbro.agent.DetailLevel;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelNamesTest {
	@Test
	void acceptsAliasesAndIds() {
		assertTrue(ModelNames.isValid("sonnet"));
		assertTrue(ModelNames.isValid("claude-opus-5-5"));
		assertTrue(ModelNames.isValid("claude-haiku-4-5-20251001"));
	}

	@Test
	void rejectsAnythingThatCouldBecomeAnotherArgument() {
		assertFalse(ModelNames.isValid(""));
		assertFalse(ModelNames.isValid("--tools"));
		assertFalse(ModelNames.isValid("sonnet --tools default"));
		assertFalse(ModelNames.isValid("sonnet&calc"));
		assertFalse(ModelNames.isValid("\"sonnet\""));
	}

	@Test
	void detailLevelsInBothLanguages() {
		assertEquals(Optional.of(DetailLevel.QUICK), DetailLevel.parse("Rapido"));
		assertEquals(Optional.of(DetailLevel.MASTERPIECE), DetailLevel.parse("caprichado"));
		assertEquals(Optional.of(DetailLevel.DETAILED), DetailLevel.parse("detailed"));
		assertEquals(Optional.empty(), DetailLevel.parse("ultra"));
		assertEquals("high", DetailLevel.MASTERPIECE.effort());
	}
}
