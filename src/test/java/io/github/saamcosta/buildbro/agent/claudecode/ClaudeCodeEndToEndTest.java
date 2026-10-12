package io.github.saamcosta.buildbro.agent.claudecode;

import io.github.saamcosta.buildbro.agent.AgentListener;
import io.github.saamcosta.buildbro.agent.AgentResult;
import io.github.saamcosta.buildbro.agent.DetailLevel;
import io.github.saamcosta.buildbro.mcp.EchoTool;
import io.github.saamcosta.buildbro.mcp.McpHttpServer;
import io.github.saamcosta.buildbro.mcp.McpProtocol;
import io.github.saamcosta.buildbro.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real Claude Code against our MCP server, outside the game. Uses the player's subscription,
 * so it only runs when asked: {@code ./gradlew test -Dbuildbro.e2e=true}.
 */
@EnabledIfSystemProperty(named = "buildbro.e2e", matches = "true")
class ClaudeCodeEndToEndTest {
	@Test
	void claudeCallsOurToolAndNothingElse() throws Exception {
		Path claude = ClaudeCli.find(System.getProperty("buildbro.claude", "")).orElseThrow(() -> new AssertionError("claude not found"));
		List<String> toolCalls = new CopyOnWriteArrayList<>();
		CompletableFuture<AgentResult> finished = new CompletableFuture<>();
		try (McpHttpServer server = McpHttpServer.start(new McpProtocol<>(new ToolRegistry<String>().register(new EchoTool()), "game", "test", toolCalls::add))) {
			ClaudeCodeAgent agent = new ClaudeCodeAgent(claude, "haiku", DetailLevel.QUICK, server.url(), server.token());
			agent.start("This is a connection test, not a build. Call the echo tool once with the word 'ping', "
					+ "then reply with exactly what it returned and nothing else.", new AgentListener() {
				@Override
				public void onText(String text) {
					System.out.println("claude: " + text);
				}

				@Override
				public void onToolUse(String tool) {
				}

				@Override
				public void onFinished(AgentResult result) {
					finished.complete(result);
				}
			});
			AgentResult result = finished.get(3, TimeUnit.MINUTES);
			System.out.println("result: " + result);
			assertEquals(AgentResult.Outcome.DONE, result.outcome(), result.message());
			assertEquals(List.of("echo"), toolCalls);
			assertTrue(result.message().contains("game:ping"), result.message());
			assertTrue(result.usage() != null && result.usage().outputTokens() > 0, "usage: " + result.usage());
		}
	}
}
