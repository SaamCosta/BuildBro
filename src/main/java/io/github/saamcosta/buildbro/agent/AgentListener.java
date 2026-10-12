package io.github.saamcosta.buildbro.agent;

/** Progress of a request. Called from background threads. */
public interface AgentListener {
	/** Something the model wrote for the player. */
	void onText(String text);

	/** The model called one of the game tools (name without any prefix). */
	void onToolUse(String tool);

	/** Called exactly once, at the end. */
	void onFinished(AgentResult result);
}
