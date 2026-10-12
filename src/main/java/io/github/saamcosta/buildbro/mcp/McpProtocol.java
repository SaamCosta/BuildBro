package io.github.saamcosta.buildbro.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.saamcosta.buildbro.BuildBro;
import io.github.saamcosta.buildbro.tool.Tool;
import io.github.saamcosta.buildbro.tool.ToolException;
import io.github.saamcosta.buildbro.tool.ToolRegistry;

import java.util.List;

/**
 * The JSON-RPC side of a minimal MCP server: lifecycle, ping and tools. No transport here,
 * so it can be tested without sockets.
 */
public final class McpProtocol<C> {
	static final String LATEST_VERSION = "2025-06-18";
	private static final List<String> SUPPORTED_VERSIONS = List.of("2025-03-26", "2025-06-18", "2025-11-25");
	private static final Gson GSON = new Gson();

	private final ToolRegistry<C> tools;
	private final C context;
	private final String serverVersion;
	private final ToolCallListener listener;

	public McpProtocol(ToolRegistry<C> tools, C context, String serverVersion, ToolCallListener listener) {
		this.tools = tools;
		this.context = context;
		this.serverVersion = serverVersion;
		this.listener = listener;
	}

	/** Returns the response, or null when the message is a notification or a response that needs no answer. */
	public JsonObject handle(JsonElement message) {
		if (!message.isJsonObject()) {
			return error(null, -32600, "Invalid request");
		}
		JsonObject request = message.getAsJsonObject();
		JsonElement id = request.get("id");
		JsonElement methodElement = request.get("method");
		if (methodElement == null || !methodElement.isJsonPrimitive()) {
			return id == null ? null : error(id, -32600, "Invalid request");
		}
		if (id == null) {
			return null;
		}
		JsonObject params = request.has("params") && request.get("params").isJsonObject()
				? request.getAsJsonObject("params") : new JsonObject();
		return switch (methodElement.getAsString()) {
			case "initialize" -> result(id, initialize(params));
			case "ping" -> result(id, new JsonObject());
			case "tools/list" -> result(id, listTools());
			case "tools/call" -> callTool(id, params);
			default -> error(id, -32601, "Method not found");
		};
	}

	private JsonObject initialize(JsonObject params) {
		String requested = params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : LATEST_VERSION;
		JsonObject result = new JsonObject();
		result.addProperty("protocolVersion", SUPPORTED_VERSIONS.contains(requested) ? requested : LATEST_VERSION);
		JsonObject capabilities = new JsonObject();
		JsonObject toolCapability = new JsonObject();
		toolCapability.addProperty("listChanged", false);
		capabilities.add("tools", toolCapability);
		result.add("capabilities", capabilities);
		JsonObject info = new JsonObject();
		info.addProperty("name", BuildBro.MOD_ID);
		info.addProperty("version", serverVersion);
		result.add("serverInfo", info);
		return result;
	}

	private JsonObject listTools() {
		JsonArray list = new JsonArray();
		for (Tool<C> tool : tools.all()) {
			JsonObject entry = new JsonObject();
			entry.addProperty("name", tool.name());
			entry.addProperty("description", tool.description());
			entry.add("inputSchema", tool.inputSchema());
			list.add(entry);
		}
		JsonObject result = new JsonObject();
		result.add("tools", list);
		return result;
	}

	private JsonObject callTool(JsonElement id, JsonObject params) {
		if (!params.has("name") || !params.get("name").isJsonPrimitive()) {
			return error(id, -32602, "Missing tool name");
		}
		String name = params.get("name").getAsString();
		JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject()
				? params.getAsJsonObject("arguments") : new JsonObject();
		listener.onToolCall(name);
		try {
			JsonElement output = tools.call(name, args, context);
			return result(id, toolResult(GSON.toJson(output), false));
		} catch (ToolException e) {
			return result(id, toolResult(e.getMessage(), true));
		} catch (RuntimeException e) {
			BuildBro.LOGGER.error("Tool {} failed", name, e);
			return result(id, toolResult("Internal error in " + name + ": " + e.getClass().getSimpleName(), true));
		}
	}

	private static JsonObject toolResult(String text, boolean isError) {
		JsonObject content = new JsonObject();
		content.addProperty("type", "text");
		content.addProperty("text", text);
		JsonArray contents = new JsonArray();
		contents.add(content);
		JsonObject result = new JsonObject();
		result.add("content", contents);
		result.addProperty("isError", isError);
		return result;
	}

	private static JsonObject result(JsonElement id, JsonObject result) {
		JsonObject response = new JsonObject();
		response.addProperty("jsonrpc", "2.0");
		response.add("id", id);
		response.add("result", result);
		return response;
	}

	static JsonObject error(JsonElement id, int code, String message) {
		JsonObject error = new JsonObject();
		error.addProperty("code", code);
		error.addProperty("message", message);
		JsonObject response = new JsonObject();
		response.addProperty("jsonrpc", "2.0");
		response.add("id", id);
		response.add("error", error);
		return response;
	}

	@FunctionalInterface
	public interface ToolCallListener {
		void onToolCall(String toolName);
	}
}
