package io.github.saamcosta.buildbro.agent.claudecode;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.saamcosta.buildbro.agent.Usage;

import java.util.ArrayList;
import java.util.List;

/** One line of {@code claude -p --output-format stream-json}, reduced to what the mod uses. */
sealed interface StreamEvent {
	/** First event: whether our MCP server connected. */
	record Init(String mcpStatus) implements StreamEvent {
	}

	/** Text the model wrote in one assistant message. */
	record Text(String text) implements StreamEvent {
	}

	/** Last event. {@code usage} is null when the event carries none. */
	record Result(boolean isError, String subtype, String text, Usage usage) implements StreamEvent {
	}

	record Other() implements StreamEvent {
	}

	static StreamEvent parse(String line, String serverName) {
		JsonObject event;
		try {
			JsonElement element = JsonParser.parseString(line);
			if (!element.isJsonObject()) {
				return new Other();
			}
			event = element.getAsJsonObject();
		} catch (JsonParseException e) {
			return new Other();
		}
		String type = string(event, "type");
		return switch (type) {
			case "system" -> "init".equals(string(event, "subtype")) ? new Init(mcpStatus(event, serverName)) : new Other();
			case "assistant" -> text(event);
			case "result" -> new Result(
					event.has("is_error") && event.get("is_error").getAsBoolean(),
					string(event, "subtype"),
					string(event, "result"),
					usage(event));
			default -> new Other();
		};
	}

	private static StreamEvent text(JsonObject event) {
		if (!event.has("message") || !event.get("message").isJsonObject()) {
			return new Other();
		}
		JsonElement content = event.getAsJsonObject("message").get("content");
		if (content == null || !content.isJsonArray()) {
			return new Other();
		}
		List<String> parts = new ArrayList<>();
		for (JsonElement block : (JsonArray) content) {
			if (block.isJsonObject() && "text".equals(string(block.getAsJsonObject(), "type"))) {
				String text = string(block.getAsJsonObject(), "text").strip();
				if (!text.isEmpty()) {
					parts.add(text);
				}
			}
		}
		return parts.isEmpty() ? new Other() : new Text(String.join("\n", parts));
	}

	private static Usage usage(JsonObject event) {
		if (!event.has("usage") || !event.get("usage").isJsonObject()) {
			return null;
		}
		JsonObject usage = event.getAsJsonObject("usage");
		String model = "";
		if (event.has("modelUsage") && event.get("modelUsage").isJsonObject()) {
			model = String.join("+", event.getAsJsonObject("modelUsage").keySet());
		}
		return new Usage(model,
				number(usage, "input_tokens"),
				number(usage, "cache_read_input_tokens"),
				number(usage, "cache_creation_input_tokens"),
				number(usage, "output_tokens"),
				event.has("total_cost_usd") && event.get("total_cost_usd").isJsonPrimitive() ? event.get("total_cost_usd").getAsDouble() : 0,
				(int) number(event, "num_turns"));
	}

	private static long number(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsLong() : 0;
	}

	private static String mcpStatus(JsonObject event, String serverName) {
		JsonElement servers = event.get("mcp_servers");
		if (servers != null && servers.isJsonArray()) {
			for (JsonElement server : servers.getAsJsonArray()) {
				if (server.isJsonObject() && serverName.equals(string(server.getAsJsonObject(), "name"))) {
					return string(server.getAsJsonObject(), "status");
				}
			}
		}
		return "missing";
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
	}
}
