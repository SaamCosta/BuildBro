package io.github.saamcosta.buildbro.agent;

import java.util.Locale;
import java.util.Optional;

/** How much care a build gets. Only the reasoning effort and a short prompt change; the model stays the same. */
public enum DetailLevel {
	QUICK("quick", "low", 40),
	DETAILED("detailed", "medium", 120),
	MASTERPIECE("masterpiece", "high", 300);

	private final String id;
	private final String effort;
	private final int maxToolCalls;

	DetailLevel(String id, String effort, int maxToolCalls) {
		this.id = id;
		this.effort = effort;
		this.maxToolCalls = maxToolCalls;
	}

	public String id() {
		return id;
	}

	/** Value for {@code claude --effort}. */
	public String effort() {
		return effort;
	}

	/** Claude Code has no turn limit flag any more, so the mod stops the build after this many tool calls. */
	public int maxToolCalls() {
		return maxToolCalls;
	}

	public String promptResource() {
		return "/assets/buildbro/prompts/detail-" + id + ".md";
	}

	/** Accepts the English ids and the Portuguese names the commands suggest. */
	public static Optional<DetailLevel> parse(String text) {
		return switch (text.toLowerCase(Locale.ROOT)) {
			case "quick", "rapido", "rápido" -> Optional.of(QUICK);
			case "detailed", "detalhado" -> Optional.of(DETAILED);
			case "masterpiece", "caprichado" -> Optional.of(MASTERPIECE);
			default -> Optional.empty();
		};
	}
}
