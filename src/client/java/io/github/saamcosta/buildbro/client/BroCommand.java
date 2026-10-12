package io.github.saamcosta.buildbro.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.saamcosta.buildbro.BuildController;
import io.github.saamcosta.buildbro.agent.DetailLevel;
import io.github.saamcosta.buildbro.config.BuildBroConfig;
import io.github.saamcosta.buildbro.config.ModelNames;
import io.github.saamcosta.buildbro.usage.UsageLog;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * {@code /bro <request>} plus stop, undo, detail, model, limit and status.
 * A client command: nothing is sent to the server, so it works with nothing installed there.
 */
final class BroCommand {
	private static final int MAX_UNDO_STEPS = 10;

	private BroCommand() {
	}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, BuildController controller) {
		dispatcher.register(literal("bro")
				.then(literal("stop").executes(context -> stop(context, controller)))
				.then(literal("undo")
						.executes(context -> undo(context, controller, 1))
						.then(argument("steps", IntegerArgumentType.integer(1, MAX_UNDO_STEPS))
								.executes(context -> undo(context, controller, IntegerArgumentType.getInteger(context, "steps")))))
				.then(literal("detail")
						.executes(context -> showDetail(context, controller.config()))
						.then(argument("level", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(
										Stream.of("rapido", "detalhado", "caprichado", "quick", "detailed", "masterpiece"), builder))
								.executes(context -> setDetail(context, controller.config(), StringArgumentType.getString(context, "level")))))
				.then(literal("model")
						.executes(context -> feedback(context, Component.translatable("buildbro.model", controller.config().model)))
						.then(argument("name", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(Stream.of("sonnet", "opus", "haiku", "fable"), builder))
								.executes(context -> setModel(context, controller.config(), StringArgumentType.getString(context, "name")))))
				.then(literal("limit")
						.executes(context -> feedback(context, Component.translatable("buildbro.limit", controller.config().blockLimit)))
						.then(argument("blocks", IntegerArgumentType.integer(1, 1_000_000))
								.executes(context -> setLimit(context, controller.config(), IntegerArgumentType.getInteger(context, "blocks")))))
				.then(literal("status").executes(context -> status(context, controller)))
				.then(literal("usage").executes(context -> usage(context, controller.usageLog())))
				.then(argument("request", StringArgumentType.greedyString())
						.executes(context -> build(context, controller, StringArgumentType.getString(context, "request")))));
	}

	private static int build(CommandContext<FabricClientCommandSource> context, BuildController controller, String request) {
		Minecraft client = context.getSource().getClient();
		IntegratedServer server = client.getSingleplayerServer();
		if (!client.hasSingleplayerServer() || server == null) {
			return error(context, Component.translatable("buildbro.singleplayer_only"));
		}
		LocalPlayer player = context.getSource().getPlayer();
		BuildBroConfig config = controller.config();
		BuildController.StartResult result = controller.start(request, server, player.getUUID(), player.level().dimension(),
				player.blockPosition(), new ChatFeedback(client));
		return switch (result) {
			case STARTED -> feedback(context, Component.translatable("buildbro.started",
					Component.translatable("buildbro.detail." + config.detail.id()), config.model));
			case BUSY -> error(context, Component.translatable("buildbro.busy"));
			case CLAUDE_NOT_FOUND -> error(context, Component.translatable("buildbro.claude_not_found"));
			case FAILED -> error(context, Component.translatable("buildbro.failed_to_start"));
		};
	}

	private static int stop(CommandContext<FabricClientCommandSource> context, BuildController controller) {
		return controller.stop()
				? feedback(context, Component.translatable("buildbro.stopping"))
				: error(context, Component.translatable("buildbro.nothing_running"));
	}

	private static int undo(CommandContext<FabricClientCommandSource> context, BuildController controller, int steps) {
		Minecraft client = context.getSource().getClient();
		IntegratedServer server = client.getSingleplayerServer();
		if (server == null) {
			return error(context, Component.translatable("buildbro.singleplayer_only"));
		}
		if (controller.isRunning()) {
			return error(context, Component.translatable("buildbro.undo_busy"));
		}
		controller.undo(server, steps, restored -> client.execute(() -> ChatFeedback.say(client, restored == 0
				? Component.translatable("buildbro.nothing_to_undo")
				: Component.translatable("buildbro.undone", restored))));
		return 1;
	}

	private static int showDetail(CommandContext<FabricClientCommandSource> context, BuildBroConfig config) {
		return feedback(context, Component.translatable("buildbro.detail", Component.translatable("buildbro.detail." + config.detail.id())));
	}

	private static int setDetail(CommandContext<FabricClientCommandSource> context, BuildBroConfig config, String text) {
		return DetailLevel.parse(text).map(level -> {
			config.detail = level;
			config.save();
			return showDetail(context, config);
		}).orElseGet(() -> error(context, Component.translatable("buildbro.detail_unknown")));
	}

	private static int setModel(CommandContext<FabricClientCommandSource> context, BuildBroConfig config, String name) {
		String model = name.toLowerCase(Locale.ROOT);
		if (!ModelNames.isValid(model)) {
			return error(context, Component.translatable("buildbro.model_invalid"));
		}
		config.model = model;
		config.save();
		return feedback(context, Component.translatable("buildbro.model_next", model));
	}

	private static int setLimit(CommandContext<FabricClientCommandSource> context, BuildBroConfig config, int blocks) {
		config.blockLimit = blocks;
		config.save();
		return feedback(context, Component.translatable("buildbro.limit", blocks));
	}

	private static int status(CommandContext<FabricClientCommandSource> context, BuildController controller) {
		BuildBroConfig config = controller.config();
		return feedback(context, Component.translatable(controller.isRunning() ? "buildbro.status.running" : "buildbro.status.idle",
				Component.translatable("buildbro.detail." + config.detail.id()), config.model, config.blockLimit, config.radius));
	}

	private static int usage(CommandContext<FabricClientCommandSource> context, UsageLog log) {
		Minecraft client = context.getSource().getClient();
		CompletableFuture.runAsync(() -> {
			Component message;
			try {
				UsageLog.Summary summary = log.summary(LocalDate.now());
				UsageText text = UsageText.of(client);
				message = Component.translatable("buildbro.usage.summary",
						summary.today().builds(), text.tokens(summary.today().inputTokens()), text.tokens(summary.today().outputTokens()),
						text.dollars(summary.today().costUsd()),
						summary.all().builds(), text.dollars(summary.all().costUsd()))
						.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
								Component.translatable("buildbro.usage.file", log.file().toAbsolutePath().toString()))));
			} catch (IOException e) {
				message = Component.translatable("buildbro.usage.unreadable", e.getMessage()).withStyle(ChatFormatting.RED);
			}
			Component result = message;
			client.execute(() -> ChatFeedback.say(client, result));
		});
		return 1;
	}

	private static int feedback(CommandContext<FabricClientCommandSource> context, Component message) {
		context.getSource().sendFeedback(ChatFeedback.prefixed(message));
		return 1;
	}

	private static int error(CommandContext<FabricClientCommandSource> context, Component message) {
		context.getSource().sendError(ChatFeedback.prefixed(message));
		return 0;
	}
}
