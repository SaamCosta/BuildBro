package io.github.saamcosta.buildbro.tool;

/** A failure the model should read and recover from (bad argument, limit reached, unknown block). */
public class ToolException extends Exception {
	public ToolException(String message) {
		super(message);
	}
}
