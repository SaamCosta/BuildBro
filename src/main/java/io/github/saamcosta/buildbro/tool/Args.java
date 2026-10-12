package io.github.saamcosta.buildbro.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Reads tool arguments with error messages the model can act on. */
public final class Args {
	private Args() {
	}

	public static int integer(JsonObject args, String name) throws ToolException {
		JsonElement value = args.get(name);
		if (value == null || value.isJsonNull()) {
			throw new ToolException("Missing argument '" + name + "'");
		}
		return asInt(value, name);
	}

	public static int integer(JsonObject args, String name, int fallback) throws ToolException {
		JsonElement value = args.get(name);
		return value == null || value.isJsonNull() ? fallback : asInt(value, name);
	}

	public static String string(JsonObject args, String name) throws ToolException {
		JsonElement value = args.get(name);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			throw new ToolException("Argument '" + name + "' must be a string");
		}
		return value.getAsString();
	}

	public static String string(JsonObject args, String name, String fallback) throws ToolException {
		JsonElement value = args.get(name);
		return value == null || value.isJsonNull() ? fallback : string(args, name);
	}

	public static boolean bool(JsonObject args, String name, boolean fallback) throws ToolException {
		JsonElement value = args.get(name);
		if (value == null || value.isJsonNull()) {
			return fallback;
		}
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
			throw new ToolException("Argument '" + name + "' must be true or false");
		}
		return value.getAsBoolean();
	}

	public static JsonArray array(JsonObject args, String name) throws ToolException {
		JsonElement value = args.get(name);
		if (value == null || !value.isJsonArray()) {
			throw new ToolException("Argument '" + name + "' must be an array");
		}
		return value.getAsJsonArray();
	}

	private static int asInt(JsonElement value, String name) throws ToolException {
		if (value.isJsonPrimitive()) {
			JsonPrimitive primitive = value.getAsJsonPrimitive();
			if (primitive.isNumber()) {
				double number = primitive.getAsDouble();
				if (number == Math.rint(number) && Math.abs(number) <= Integer.MAX_VALUE) {
					return (int) number;
				}
			}
		}
		throw new ToolException("Argument '" + name + "' must be a whole number");
	}
}
