package io.github.saamcosta.buildbro.tool.game;

import com.google.gson.JsonObject;

import java.util.Map;

final class Counts {
	private Counts() {
	}

	/** The {@code limit} largest counts, largest first, as {"id": count}. */
	static JsonObject top(Map<String, Integer> counts, int limit) {
		JsonObject out = new JsonObject();
		counts.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.limit(limit)
				.forEach(entry -> out.addProperty(entry.getKey(), entry.getValue()));
		return out;
	}
}
