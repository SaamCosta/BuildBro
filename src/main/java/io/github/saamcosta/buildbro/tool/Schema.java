package io.github.saamcosta.buildbro.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Small builder for the JSON Schemas of tool arguments. */
public final class Schema {
	private final JsonObject properties = new JsonObject();
	private final JsonArray required = new JsonArray();

	public static Schema object() {
		return new Schema();
	}

	public Schema integer(String name, String description, boolean isRequired) {
		return property(name, type("integer", description), isRequired);
	}

	public Schema string(String name, String description, boolean isRequired) {
		return property(name, type("string", description), isRequired);
	}

	public Schema bool(String name, String description, boolean isRequired) {
		return property(name, type("boolean", description), isRequired);
	}

	public Schema enumeration(String name, String description, boolean isRequired, String... values) {
		JsonObject schema = type("string", description);
		JsonArray allowed = new JsonArray();
		for (String value : values) {
			allowed.add(value);
		}
		schema.add("enum", allowed);
		return property(name, schema, isRequired);
	}

	public Schema property(String name, JsonObject schema, boolean isRequired) {
		properties.add(name, schema);
		if (isRequired) {
			required.add(name);
		}
		return this;
	}

	public JsonObject build() {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		schema.add("properties", properties.deepCopy());
		schema.add("required", required.deepCopy());
		schema.addProperty("additionalProperties", false);
		return schema;
	}

	public static JsonObject type(String type, String description) {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", type);
		schema.addProperty("description", description);
		return schema;
	}
}
