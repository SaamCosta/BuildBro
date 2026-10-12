package io.github.saamcosta.buildbro.world;

import io.github.saamcosta.buildbro.tool.ToolException;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One player request: which world it builds in, its limits, and the transaction that collects its changes.
 * Tools get it as their context and do all game work through {@link #onServer}.
 */
public final class BuildSession {
	private static final long SERVER_TIMEOUT_SECONDS = 60;

	private final MinecraftServer server;
	private final UUID playerId;
	private final ResourceKey<Level> dimension;
	private final BlockPos origin;
	private final Limits limits;
	private final UndoHistory history;
	private Transaction transaction;
	private final AtomicInteger blocksChanged = new AtomicInteger();
	private volatile boolean stopped;

	public BuildSession(MinecraftServer server, UUID playerId, ResourceKey<Level> dimension, BlockPos origin, Limits limits, UndoHistory history) {
		this.server = server;
		this.playerId = playerId;
		this.dimension = dimension;
		this.origin = origin.immutable();
		this.limits = limits;
		this.history = history;
		this.transaction = new Transaction(dimension);
	}

	public BlockPos origin() {
		return origin;
	}

	public Limits limits() {
		return limits;
	}

	/** Block writes made by this request, counting a position again each time it changes. */
	public int blocksChanged() {
		return blocksChanged.get();
	}

	/** Runs game work on the server thread and waits for it. */
	public <T> T onServer(ServerTask<T> task) throws ToolException {
		if (stopped) {
			throw new ToolException("The player stopped this build");
		}
		try {
			return server.submit(() -> {
				ServerLevel level = server.getLevel(dimension);
				ServerPlayer player = server.getPlayerList().getPlayer(playerId);
				if (level == null || player == null) {
					throw new CompletionException(new ToolException("The player is no longer in the world"));
				}
				try {
					return task.run(new Game(level, player, new WorldEditor(level, transaction, limits, origin, blocksChanged)));
				} catch (ToolException e) {
					throw new CompletionException(e);
				}
			}).get(SERVER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (ExecutionException | CompletionException e) {
			if (e.getCause() instanceof ToolException toolException) {
				throw toolException;
			}
			throw new IllegalStateException(e.getCause());
		} catch (TimeoutException e) {
			throw new ToolException("The game did not answer in time");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ToolException("Interrupted");
		}
	}

	/** Moves what was built so far into the undo history and starts a new transaction. Server thread only. */
	public void commit() {
		history.push(transaction);
		transaction = new Transaction(dimension);
	}

	/** Closes the current build and reverts up to {@code steps} builds, newest first. Server thread only. */
	public int undo(int steps) {
		commit();
		return history.undo(server, steps);
	}

	/** Rejects every later tool call; called when the player stops the build. */
	public void stop() {
		stopped = true;
	}

	/** Ends the request: the changes become one undo entry. */
	public void finish() {
		server.execute(this::commit);
	}

	public record Game(ServerLevel level, ServerPlayer player, WorldEditor editor) {
	}

	@FunctionalInterface
	public interface ServerTask<T> {
		T run(Game game) throws ToolException;
	}
}
