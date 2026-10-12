package io.github.saamcosta.buildbro.agent.claudecode;

import com.google.gson.JsonObject;
import io.github.saamcosta.buildbro.BuildBro;
import io.github.saamcosta.buildbro.agent.Agent;
import io.github.saamcosta.buildbro.agent.AgentListener;
import io.github.saamcosta.buildbro.agent.AgentResult;
import io.github.saamcosta.buildbro.agent.DetailLevel;
import io.github.saamcosta.buildbro.agent.Prompts;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Runs {@code claude -p} against our MCP server. Claude Code is locked to the game tools:
 * no built-in tools, no other MCP servers, no user or project settings, hooks or slash commands,
 * and it runs in an empty temporary folder.
 */
public final class ClaudeCodeAgent implements Agent {
	public static final String SERVER_NAME = "buildbro";

	/** Removed so Claude Code uses the player's subscription login and the official endpoint. */
	private static final List<String> STRIPPED_ENVIRONMENT = List.of(
			"ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_BASE_URL", "ANTHROPIC_MODEL",
			"CLAUDE_CODE_USE_BEDROCK", "CLAUDE_CODE_USE_VERTEX", "CLAUDE_CODE_USE_FOUNDRY");
	private static final int STDERR_LINES = 20;

	private final Path executable;
	private final String model;
	private final DetailLevel detail;
	private final String mcpUrl;
	private final String mcpToken;
	private final AtomicBoolean finished = new AtomicBoolean();
	private volatile Process process;
	private volatile boolean stopRequested;

	public ClaudeCodeAgent(Path executable, String model, DetailLevel detail, String mcpUrl, String mcpToken) {
		this.executable = executable;
		this.model = model;
		this.detail = detail;
		this.mcpUrl = mcpUrl;
		this.mcpToken = mcpToken;
	}

	@Override
	public void start(String request, AgentListener listener) {
		Thread thread = new Thread(() -> run(request, listener), "BuildBro Claude Code");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public void stop() {
		stopRequested = true;
		Process running = process;
		if (running == null) {
			return;
		}
		running.descendants().forEach(ProcessHandle::destroy);
		running.destroy();
		Thread killer = new Thread(() -> {
			try {
				if (!running.waitFor(3, TimeUnit.SECONDS)) {
					running.descendants().forEach(ProcessHandle::destroyForcibly);
					running.destroyForcibly();
				}
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
		}, "BuildBro stop");
		killer.setDaemon(true);
		killer.start();
	}

	private void run(String request, AgentListener listener) {
		Path workDir = null;
		AgentResult result;
		try {
			// Java creates this folder readable by the current user only; it holds the MCP token.
			workDir = Files.createTempDirectory("buildbro-");
			Path mcpConfig = workDir.resolve("mcp.json");
			Files.writeString(mcpConfig, mcpConfig().toString(), StandardCharsets.UTF_8);
			Path systemPrompt = workDir.resolve("system-prompt.md");
			Files.writeString(systemPrompt, Prompts.system(detail), StandardCharsets.UTF_8);

			ProcessBuilder builder = new ProcessBuilder(command(mcpConfig, systemPrompt)).directory(workDir.toFile());
			Map<String, String> environment = builder.environment();
			STRIPPED_ENVIRONMENT.forEach(environment::remove);
			process = builder.start();
			if (stopRequested) {
				stop();
			}
			result = talk(process, request, listener);
		} catch (IOException e) {
			result = AgentResult.failed("Could not start Claude Code: " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			result = AgentResult.stopped("");
		} finally {
			deleteQuietly(workDir);
		}
		if (finished.compareAndSet(false, true)) {
			listener.onFinished(result);
		}
	}

	private AgentResult talk(Process running, String request, AgentListener listener) throws IOException, InterruptedException {
		Deque<String> stderr = new ArrayDeque<>();
		Thread stderrReader = new Thread(() -> drain(running, stderr), "BuildBro Claude Code stderr");
		stderrReader.setDaemon(true);
		stderrReader.start();

		// The request goes through stdin, never the command line.
		try (OutputStream in = running.getOutputStream()) {
			in.write(request.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			// the process may already have exited; its output says why
		}

		StreamEvent.Result last = null;
		String mcpProblem = null;
		try (BufferedReader out = new BufferedReader(new InputStreamReader(running.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = out.readLine()) != null) {
				switch (StreamEvent.parse(line, SERVER_NAME)) {
					case StreamEvent.Init init when !"connected".equals(init.mcpStatus()) -> {
						mcpProblem = init.mcpStatus();
						stop();
					}
					case StreamEvent.Text text -> listener.onText(text.text());
					case StreamEvent.Result end -> last = end;
					default -> {
					}
				}
			}
		}
		int exitCode = running.waitFor();
		stderrReader.join(1000);

		if (stopRequested && mcpProblem == null) {
			return AgentResult.stopped("");
		}
		if (mcpProblem != null) {
			return AgentResult.failed("Claude Code could not reach the game (MCP status: " + mcpProblem + ")");
		}
		if (last != null && last.text().contains("Not logged in")) {
			return AgentResult.notSignedIn(last.text());
		}
		if (last != null) {
			AgentResult result = last.isError()
					? AgentResult.failed(last.text().isBlank() ? last.subtype() : last.text())
					: AgentResult.done(last.text());
			return result.withUsage(last.usage());
		}
		String detailText;
		synchronized (stderr) {
			detailText = String.join("\n", stderr);
		}
		return AgentResult.failed("Claude Code exited with code " + exitCode + (detailText.isBlank() ? "" : ": " + detailText));
	}

	List<String> command(Path mcpConfig, Path systemPrompt) {
		List<String> command = new ArrayList<>();
		String exe = executable.toString();
		if (ClaudeCli.isWindows() && exe.toLowerCase(java.util.Locale.ROOT).endsWith(".cmd")) {
			command.add("cmd.exe");
			command.add("/c");
		}
		command.add(exe);
		command.addAll(List.of(
				"-p",
				"--output-format", "stream-json",
				"--verbose",
				"--model", model,
				"--effort", detail.effort(),
				"--mcp-config", mcpConfig.toString(),
				"--strict-mcp-config",
				"--tools", "",
				"--allowedTools", "mcp__" + SERVER_NAME,
				"--setting-sources", "",
				"--disable-slash-commands",
				"--no-session-persistence",
				"--system-prompt-file", systemPrompt.toString()));
		return command;
	}

	private JsonObject mcpConfig() {
		JsonObject headers = new JsonObject();
		headers.addProperty("Authorization", "Bearer " + mcpToken);
		JsonObject server = new JsonObject();
		server.addProperty("type", "http");
		server.addProperty("url", mcpUrl);
		server.add("headers", headers);
		JsonObject servers = new JsonObject();
		servers.add(SERVER_NAME, server);
		JsonObject config = new JsonObject();
		config.add("mcpServers", servers);
		return config;
	}

	private static void drain(Process running, Deque<String> lines) {
		try (BufferedReader err = new BufferedReader(new InputStreamReader(running.getErrorStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = err.readLine()) != null) {
				BuildBro.LOGGER.debug("claude: {}", line);
				synchronized (lines) {
					lines.addLast(line);
					if (lines.size() > STDERR_LINES) {
						lines.removeFirst();
					}
				}
			}
		} catch (IOException ignored) {
			// process ended
		}
	}

	private static void deleteQuietly(Path dir) {
		if (dir == null) {
			return;
		}
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
					// left for the OS to clean
				}
			});
		} catch (IOException ignored) {
			// left for the OS to clean
		}
	}
}
