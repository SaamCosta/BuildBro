package io.github.saamcosta.buildbro.client;

import io.github.saamcosta.buildbro.BuildBro;
import io.github.saamcosta.buildbro.BuildController;
import io.github.saamcosta.buildbro.config.BuildBroConfig;
import io.github.saamcosta.buildbro.usage.UsageLog;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class BuildBroClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		FabricLoader loader = FabricLoader.getInstance();
		String version = loader.getModContainer(BuildBro.MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
		BuildBroConfig config = BuildBroConfig.load(loader.getConfigDir().resolve(BuildBro.MOD_ID + ".json"));
		UsageLog usageLog = new UsageLog(loader.getGameDir().resolve(BuildBro.MOD_ID).resolve("usage.csv"));
		BuildController controller = new BuildController(config, version, usageLog);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> BroCommand.register(dispatcher, controller));
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> controller.onWorldClosed());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> controller.onWorldClosed());
	}
}
