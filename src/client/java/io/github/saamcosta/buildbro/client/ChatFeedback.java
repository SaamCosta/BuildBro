package io.github.saamcosta.buildbro.client;

import io.github.saamcosta.buildbro.BuildListener;
import io.github.saamcosta.buildbro.agent.AgentResult;
import io.github.saamcosta.buildbro.agent.ChatText;
import io.github.saamcosta.buildbro.agent.Usage;
import io.github.saamcosta.buildbro.usage.BuildReport;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/** Shows a request's progress: the model's words in chat, tool calls in the action bar, usage at the end. */
final class ChatFeedback implements BuildListener {
	private final Minecraft client;

	ChatFeedback(Minecraft client) {
		this.client = client;
	}

	@Override
	public void onText(String text) {
		String plain = ChatText.plain(text);
		if (!plain.isEmpty()) {
			client.execute(() -> say(client, Component.literal(plain).withStyle(ChatFormatting.GRAY)));
		}
	}

	@Override
	public void onToolUse(String tool) {
		client.execute(() -> {
			LocalPlayer player = client.player;
			if (player != null) {
				player.sendOverlayMessage(Component.translatable("buildbro.tool." + tool).withStyle(ChatFormatting.AQUA));
			}
		});
	}

	@Override
	public void onFinished(BuildReport report) {
		AgentResult result = report.result();
		Component message = switch (result.outcome()) {
			case DONE -> Component.translatable("buildbro.done").withStyle(ChatFormatting.GREEN);
			case STOPPED -> result.message().isBlank()
					? Component.translatable("buildbro.stopped").withStyle(ChatFormatting.YELLOW)
					: Component.literal(result.message()).withStyle(ChatFormatting.YELLOW);
			case FAILED -> Component.translatable("buildbro.failed", result.message()).withStyle(ChatFormatting.RED);
			case NOT_SIGNED_IN -> Component.translatable("buildbro.not_signed_in").withStyle(ChatFormatting.RED);
		};
		Component usage = result.outcome() == AgentResult.Outcome.NOT_SIGNED_IN ? null : usageLine(report);
		client.execute(() -> {
			say(client, message);
			if (usage != null) {
				say(client, usage);
			}
		});
	}

	private Component usageLine(BuildReport report) {
		UsageText text = UsageText.of(client);
		Usage usage = report.result().usage();
		Component line = usage == null
				? Component.translatable("buildbro.usage.partial",
						text.duration(report.durationMs()), report.toolCalls(), text.count(report.blocksChanged()))
				: Component.translatable("buildbro.usage.line",
						text.duration(report.durationMs()), usage.turns(), report.toolCalls(), text.count(report.blocksChanged()),
						text.tokens(usage.totalInputTokens()), text.tokens(usage.cacheReadTokens()), text.tokens(usage.outputTokens()),
						text.dollars(usage.costUsd()));
		Component hover = Component.translatable("buildbro.usage.hover", usage == null ? report.model() : usage.model());
		return line.copy().withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY).withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	/** Client thread only. */
	static void say(Minecraft client, Component message) {
		LocalPlayer player = client.player;
		if (player != null) {
			player.sendSystemMessage(prefixed(message));
		}
	}

	static Component prefixed(Component message) {
		return Component.literal("[BuildBro] ").withStyle(ChatFormatting.GOLD).append(message);
	}
}
