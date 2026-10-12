package io.github.saamcosta.buildbro.usage;

import io.github.saamcosta.buildbro.agent.AgentResult;
import io.github.saamcosta.buildbro.agent.DetailLevel;

import java.time.LocalDateTime;

/**
 * Everything worth knowing about one finished request.
 *
 * @param model         the model setting used for the request ({@link AgentResult#usage()} has the resolved id)
 * @param durationMs    wall-clock time from the command to the end
 * @param toolCalls     game tool calls the model made
 * @param blocksChanged block writes in the world
 */
public record BuildReport(LocalDateTime startedAt, String request, String model, DetailLevel detail, AgentResult result,
		long durationMs, int toolCalls, int blocksChanged) {
}
