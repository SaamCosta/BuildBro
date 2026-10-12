package io.github.saamcosta.buildbro.tool.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.saamcosta.buildbro.tool.Args;
import io.github.saamcosta.buildbro.tool.Schema;
import io.github.saamcosta.buildbro.tool.Tool;
import io.github.saamcosta.buildbro.tool.ToolException;
import io.github.saamcosta.buildbro.world.BuildSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class FindBlockTool implements Tool<BuildSession> {
	private static final int MAX_RESULTS = 50;

	@Override
	public String name() {
		return "find_block";
	}

	@Override
	public String description() {
		return "Searches the block ids that exist in this game (including blocks added by other mods). "
				+ "Every word must appear in the id: 'oak stairs' finds minecraft:oak_stairs. "
				+ "Use it whenever you are not sure a block id exists in this version.";
	}

	@Override
	public JsonObject inputSchema() {
		return Schema.object()
				.string("query", "Words to look for, such as 'deepslate tile' or 'lantern'", true)
				.build();
	}

	@Override
	public JsonElement call(JsonObject args, BuildSession session) throws ToolException {
		String[] words = Args.string(args, "query").toLowerCase(Locale.ROOT).split("[\\s_:]+");
		List<String> terms = Arrays.stream(words).filter(word -> !word.isEmpty()).toList();
		if (terms.isEmpty()) {
			throw new ToolException("Give at least one word to search for");
		}
		List<String> matches = BuiltInRegistries.BLOCK.keySet().stream()
				.map(Identifier::toString)
				.filter(id -> terms.stream().allMatch(id::contains))
				.sorted(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
				.toList();

		JsonArray list = new JsonArray();
		matches.stream().limit(MAX_RESULTS).forEach(list::add);
		JsonObject out = new JsonObject();
		out.add("blocks", list);
		out.addProperty("total", matches.size());
		if (matches.size() > MAX_RESULTS) {
			out.addProperty("note", "Only the first " + MAX_RESULTS + " are listed; add words to narrow the search");
		}
		return out;
	}
}
