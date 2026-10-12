package io.github.saamcosta.buildbro.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolRegistry<C> {
	private final Map<String, Tool<C>> tools = new LinkedHashMap<>();

	public ToolRegistry<C> register(Tool<C> tool) {
		if (tools.putIfAbsent(tool.name(), tool) != null) {
			throw new IllegalArgumentException("Duplicate tool " + tool.name());
		}
		return this;
	}

	public Collection<Tool<C>> all() {
		return Collections.unmodifiableCollection(tools.values());
	}

	public JsonElement call(String name, JsonObject args, C context) throws ToolException {
		Tool<C> tool = tools.get(name);
		if (tool == null) {
			throw new ToolException("Unknown tool: " + name);
		}
		return tool.call(args == null ? new JsonObject() : args, context);
	}
}
