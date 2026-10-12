package io.github.saamcosta.buildbro.agent;

/**
 * @param outcome how the request ended
 * @param message final answer from the model, or what went wrong
 * @param usage   what the request consumed, or null when the backend did not say (for example when it was stopped)
 */
public record AgentResult(Outcome outcome, String message, Usage usage) {
	public enum Outcome {
		DONE,
		STOPPED,
		FAILED,
		/** Claude Code is installed but nobody signed in to it. */
		NOT_SIGNED_IN
	}

	public static AgentResult done(String message) {
		return new AgentResult(Outcome.DONE, message, null);
	}

	public static AgentResult stopped(String message) {
		return new AgentResult(Outcome.STOPPED, message, null);
	}

	public static AgentResult notSignedIn(String message) {
		return new AgentResult(Outcome.NOT_SIGNED_IN, message, null);
	}

	public static AgentResult failed(String message) {
		return new AgentResult(Outcome.FAILED, message, null);
	}

	public AgentResult withUsage(Usage usage) {
		return new AgentResult(outcome, message, usage);
	}
}
