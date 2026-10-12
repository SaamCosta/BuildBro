package io.github.saamcosta.buildbro;

import io.github.saamcosta.buildbro.usage.BuildReport;

/** What the player sees of a request. Called from background threads. */
public interface BuildListener {
	void onText(String text);

	void onToolUse(String tool);

	/** Called exactly once, after the report was written to the usage log. */
	void onFinished(BuildReport report);
}
