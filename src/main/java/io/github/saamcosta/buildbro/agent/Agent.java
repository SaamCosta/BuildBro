package io.github.saamcosta.buildbro.agent;

/**
 * The "brain" of one request. Today only Claude Code; a direct API backend will implement the same
 * interface over the same tool registry.
 */
public interface Agent {
	/** Starts working on the player's request; events arrive on a background thread. */
	void start(String request, AgentListener listener);

	/** Stops as soon as possible; {@link AgentListener#onFinished} still arrives once. */
	void stop();
}
