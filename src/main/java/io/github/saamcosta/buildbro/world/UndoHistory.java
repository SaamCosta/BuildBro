package io.github.saamcosta.buildbro.world;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Finished builds, newest first. Lives as long as the world is open.
 * TODO: persist per world so undo survives a restart.
 */
public final class UndoHistory {
	private static final int MAX_ENTRIES = 32;

	private final Deque<Transaction> entries = new ArrayDeque<>();

	public synchronized void push(Transaction transaction) {
		if (transaction.isEmpty()) {
			return;
		}
		entries.push(transaction);
		while (entries.size() > MAX_ENTRIES) {
			entries.removeLast();
		}
	}

	/** Reverts up to {@code steps} builds, newest first. Server thread only. Returns the number of blocks restored. */
	public int undo(MinecraftServer server, int steps) {
		int restored = 0;
		for (int i = 0; i < steps; i++) {
			Transaction newest;
			synchronized (this) {
				newest = entries.poll();
			}
			if (newest == null) {
				break;
			}
			restored += newest.revert(server);
		}
		return restored;
	}

	public synchronized int size() {
		return entries.size();
	}

	public synchronized void clear() {
		entries.clear();
	}
}
