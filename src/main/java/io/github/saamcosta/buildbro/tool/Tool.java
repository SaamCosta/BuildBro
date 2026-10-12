package io.github.saamcosta.buildbro.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * One action the model can take in the game. Tools know nothing about MCP or any model API:
 * each backend (Claude Code over MCP, or a direct API loop later) adapts the same registry.
 */
public interface Tool<C> {
	String name();

	/** What the model reads to decide when and how to call the tool. */
	String description();

	/** JSON Schema of the arguments object. */
	JsonObject inputSchema();

	/** Runs the tool. Called from a backend thread, never from the game threads. */
	JsonElement call(JsonObject args, C context) throws ToolException;
}
