package io.github.saamcosta.buildbro.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.saamcosta.buildbro.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpProtocolTest {
	private final List<String> calls = new ArrayList<>();
	private final McpProtocol<String> protocol = new McpProtocol<>(new ToolRegistry<String>().register(new EchoTool()), "ctx", "test", calls::add);

	private JsonObject send(String json) {
		return protocol.handle(JsonParser.parseString(json));
	}

	@Test
	void initializeAgreesOnAKnownVersion() {
		JsonObject known = send("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\"}}");
		assertEquals("2025-03-26", known.getAsJsonObject("result").get("protocolVersion").getAsString());
		JsonObject unknown = send("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1999-01-01\"}}");
		assertEquals(McpProtocol.LATEST_VERSION, unknown.getAsJsonObject("result").get("protocolVersion").getAsString());
		assertTrue(unknown.getAsJsonObject("result").getAsJsonObject("capabilities").has("tools"));
	}

	@Test
	void notificationsGetNoAnswer() {
		assertNull(send("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
	}

	@Test
	void listsTools() {
		JsonObject response = send("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}");
		JsonObject tool = response.getAsJsonObject("result").getAsJsonArray("tools").get(0).getAsJsonObject();
		assertEquals("echo", tool.get("name").getAsString());
		assertEquals("object", tool.getAsJsonObject("inputSchema").get("type").getAsString());
	}

	@Test
	void callsToolWithContext() {
		JsonObject result = send("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"word\":\"hi\"}}}")
				.getAsJsonObject("result");
		assertFalse(result.get("isError").getAsBoolean());
		assertEquals("\"ctx:hi\"", result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString());
		assertEquals(List.of("echo"), calls);
	}

	@Test
	void toolErrorsGoBackToTheModel() {
		JsonObject failed = send("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"word\":\"fail\"}}}")
				.getAsJsonObject("result");
		assertTrue(failed.get("isError").getAsBoolean());
		JsonObject unknown = send("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"nope\"}}")
				.getAsJsonObject("result");
		assertTrue(unknown.get("isError").getAsBoolean());
	}

	@Test
	void unknownMethod() {
		assertEquals(-32601, send("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"resources/list\"}").getAsJsonObject("error").get("code").getAsInt());
	}
}
