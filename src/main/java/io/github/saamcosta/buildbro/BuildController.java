package io.github.saamcosta.buildbro;

import io.github.saamcosta.buildbro.agent.Agent;
import io.github.saamcosta.buildbro.agent.AgentListener;
import io.github.saamcosta.buildbro.agent.AgentResult;
import io.github.saamcosta.buildbro.agent.claudecode.ClaudeCli;
import io.github.saamcosta.buildbro.agent.claudecode.ClaudeCodeAgent;
import io.github.saamcosta.buildbro.config.BuildBroConfig;
import io.github.saamcosta.buildbro.mcp.McpHttpServer;
import io.github.saamcosta.buildbro.mcp.McpProtocol;
import io.github.saamcosta.buildbro.tool.ToolRegistry;
import io.github.saamcosta.buildbro.tool.game.GameTools;
import io.github.saamcosta.buildbro.usage.BuildReport;
import io.github.saamcosta.buildbro.usage.UsageLog;
import io.github.saamcosta.buildbro.world.BuildSession;
import io.github.saamcosta.buildbro.world.Limits;
import io.github.saamcosta.buildbro.world.UndoHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** Runs one request at a time: its session, the MCP server for it, and the agent. */
public final class BuildController {
	private final BuildBroConfig config;
	private final String version;
	private final UndoHistory history = new UndoHistory();
	private final ToolRegistry<BuildSession> tools = GameTools.all();
	private final UsageLog usageLog;
	private Running running;

	public BuildController(BuildBroConfig config, String version, UsageLog usageLog) {
		this.config = config;
		this.version = version;
		this.usageLog = usageLog;
	}

	public BuildBroConfig config() {
		return config;
	}

	public UsageLog usageLog() {
		return usageLog;
	}

	public synchronized boolean isRunning() {
		return running != null;
	}

	public synchronized StartResult start(String request, MinecraftServer server, UUID playerId, ResourceKey<Level> dimension,
			BlockPos origin, BuildListener listener) {
		if (running != null) {
			return StartResult.BUSY;
		}
		Optional<Path> claude = ClaudeCli.find(config.claudePath);
		if (claude.isEmpty()) {
			return StartResult.CLAUDE_NOT_FOUND;
		}

		BuildSession session = new BuildSession(server, playerId, dimension, origin,
				new Limits(config.blockLimit, config.radius), history);
		int maxToolCalls = config.detail.maxToolCalls();
		AtomicInteger toolCalls = new AtomicInteger();
		AtomicBoolean tooManyCalls = new AtomicBoolean();
		McpProtocol<BuildSession> protocol = new McpProtocol<>(tools, session, version, tool -> {
			listener.onToolUse(tool);
			if (toolCalls.incrementAndGet() > maxToolCalls && tooManyCalls.compareAndSet(false, true)) {
				stop();
			}
		});

		McpHttpServer mcp;
		try {
			mcp = McpHttpServer.start(protocol);
		} catch (IOException e) {
			BuildBro.LOGGER.error("Could not start the MCP server", e);
			return StartResult.FAILED;
		}

		LocalDateTime startedAt = LocalDateTime.now();
		long startNanos = System.nanoTime();
		String model = config.model;
		var detail = config.detail;
		Agent agent = new ClaudeCodeAgent(claude.get(), model, detail, mcp.url(), mcp.token());
		Running current = new Running(session, agent);
		running = current;
		agent.start(request, new AgentListener() {
			@Override
			public void onText(String text) {
				listener.onText(text);
			}

			@Override
			public void onToolUse(String tool) {
				listener.onToolUse(tool);
			}

			@Override
			public void onFinished(AgentResult result) {
				session.finish();
				mcp.close();
				synchronized (BuildController.this) {
					if (running == current) {
						running = null;
					}
				}
				AgentResult finalResult = tooManyCalls.get()
						? AgentResult.stopped("Stopped after " + maxToolCalls + " tool calls (limit for this level of detail)").withUsage(result.usage())
						: result;
				BuildReport report = new BuildReport(startedAt, request, model, detail, finalResult,
						(System.nanoTime() - startNanos) / 1_000_000, toolCalls.get(), session.blocksChanged());
				try {
					usageLog.append(report);
				} catch (IOException e) {
					BuildBro.LOGGER.warn("Could not write {}", usageLog.file(), e);
				}
				listener.onFinished(report);
			}
		});
		return StartResult.STARTED;
	}

	/** Returns false when nothing was running. */
	public synchronized boolean stop() {
		if (running == null) {
			return false;
		}
		running.session().stop();
		running.agent().stop();
		return true;
	}

	/** Reverts builds on the server thread; {@code done} gets the number of blocks restored. */
	public void undo(MinecraftServer server, int steps, IntConsumer done) {
		server.execute(() -> done.accept(history.undo(server, steps)));
	}

	/** The world closed: stop any build and forget its history. */
	public void onWorldClosed() {
		stop();
		history.clear();
	}

	public enum StartResult {
		STARTED,
		BUSY,
		CLAUDE_NOT_FOUND,
		FAILED
	}

	private record Running(BuildSession session, Agent agent) {
	}
}
