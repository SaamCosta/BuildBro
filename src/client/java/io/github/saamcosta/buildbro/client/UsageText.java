package io.github.saamcosta.buildbro.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Numbers for the chat, in the game's language: "45,2 mil", "2min 14s", "0,42". */
record UsageText(Locale locale) {
	static UsageText of(Minecraft client) {
		return new UsageText(Locale.forLanguageTag(client.getLanguageManager().getSelected().replace('_', '-')));
	}

	String duration(long millis) {
		long seconds = Math.round(millis / 1000.0);
		return seconds < 60 ? seconds + "s" : (seconds / 60) + "min " + (seconds % 60) + "s";
	}

	String count(long value) {
		return String.format(locale, "%,d", value);
	}

	Component tokens(long value) {
		if (value >= 1_000_000) {
			return Component.translatable("buildbro.unit.million", String.format(locale, "%.1f", value / 1_000_000.0));
		}
		if (value >= 10_000) {
			return Component.translatable("buildbro.unit.thousand", String.format(locale, "%.0f", value / 1000.0));
		}
		if (value >= 1_000) {
			return Component.translatable("buildbro.unit.thousand", String.format(locale, "%.1f", value / 1000.0));
		}
		return Component.literal(Long.toString(value));
	}

	String dollars(double value) {
		return String.format(locale, value < 0.1 ? "%.3f" : "%.2f", value);
	}
}
