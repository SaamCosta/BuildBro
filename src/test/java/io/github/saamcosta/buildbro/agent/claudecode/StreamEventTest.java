package io.github.saamcosta.buildbro.agent.claudecode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamEventTest {
	@Test
	void readsMcpStatusFromInit() {
		StreamEvent event = StreamEvent.parse("{\"type\":\"system\",\"subtype\":\"init\",\"mcp_servers\":[{\"name\":\"other\",\"status\":\"failed\"},{\"name\":\"buildbro\",\"status\":\"connected\"}]}", "buildbro");
		assertEquals("connected", assertInstanceOf(StreamEvent.Init.class, event).mcpStatus());
		StreamEvent missing = StreamEvent.parse("{\"type\":\"system\",\"subtype\":\"init\",\"mcp_servers\":[]}", "buildbro");
		assertEquals("missing", assertInstanceOf(StreamEvent.Init.class, missing).mcpStatus());
	}

	@Test
	void joinsTextBlocksAndSkipsToolUse() {
		StreamEvent event = StreamEvent.parse("{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"Hello\"},"
				+ "{\"type\":\"tool_use\",\"name\":\"mcp__buildbro__fill\",\"input\":{}},{\"type\":\"text\",\"text\":\" world \"}]}}", "buildbro");
		assertEquals("Hello\nworld", assertInstanceOf(StreamEvent.Text.class, event).text());
		assertInstanceOf(StreamEvent.Other.class, StreamEvent.parse("{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"tool_use\"}]}}", "buildbro"));
	}

	@Test
	void readsResult() {
		StreamEvent.Result ok = assertInstanceOf(StreamEvent.Result.class,
				StreamEvent.parse("{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":false,\"result\":\"Built a hut\"}", "buildbro"));
		assertEquals("Built a hut", ok.text());
		StreamEvent.Result failed = assertInstanceOf(StreamEvent.Result.class,
				StreamEvent.parse("{\"type\":\"result\",\"subtype\":\"error_during_execution\",\"is_error\":true}", "buildbro"));
		assertTrue(failed.isError());
	}

	@Test
	void readsUsageFromResult() {
		StreamEvent.Result result = assertInstanceOf(StreamEvent.Result.class, StreamEvent.parse(
				"{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":false,\"result\":\"ok\",\"num_turns\":7,"
						+ "\"total_cost_usd\":0.4213,\"usage\":{\"input_tokens\":120,\"cache_creation_input_tokens\":3355,"
						+ "\"cache_read_input_tokens\":40000,\"output_tokens\":2100},\"modelUsage\":{\"claude-sonnet-5-5\":{}}}", "buildbro"));
		assertEquals("claude-sonnet-5-5", result.usage().model());
		assertEquals(43_475, result.usage().totalInputTokens());
		assertEquals(2100, result.usage().outputTokens());
		assertEquals(7, result.usage().turns());
		assertEquals(0.4213, result.usage().costUsd(), 1e-9);
		assertNull(assertInstanceOf(StreamEvent.Result.class,
				StreamEvent.parse("{\"type\":\"result\",\"is_error\":true}", "buildbro")).usage());
	}

	@Test
	void ignoresGarbage() {
		assertInstanceOf(StreamEvent.Other.class, StreamEvent.parse("not json", "buildbro"));
		assertInstanceOf(StreamEvent.Other.class, StreamEvent.parse("[1,2]", "buildbro"));
		assertInstanceOf(StreamEvent.Other.class, StreamEvent.parse("{\"type\":\"user\"}", "buildbro"));
	}
}
