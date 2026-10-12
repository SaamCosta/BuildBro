package io.github.saamcosta.buildbro.agent;

/**
 * What a request consumed, as reported by the backend at the end.
 *
 * @param model            the model that actually answered (an alias such as "sonnet" resolves to a full id)
 * @param inputTokens      input tokens that were neither read from nor written to the cache
 * @param cacheReadTokens  input tokens read from the prompt cache (much cheaper)
 * @param cacheWriteTokens input tokens written to the prompt cache
 * @param costUsd          what the request would cost at API list prices; on a subscription it is not billed,
 *                         but it is a fair measure of how much of the plan's limit was used
 * @param turns            model turns (each tool round trip is one)
 */
public record Usage(String model, long inputTokens, long cacheReadTokens, long cacheWriteTokens, long outputTokens,
		double costUsd, int turns) {
	public long totalInputTokens() {
		return inputTokens + cacheReadTokens + cacheWriteTokens;
	}
}
