package io.github.saamcosta.buildbro.tool.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.saamcosta.buildbro.tool.Schema;
import io.github.saamcosta.buildbro.tool.Tool;
import io.github.saamcosta.buildbro.tool.ToolException;
import io.github.saamcosta.buildbro.world.BlockStates;
import io.github.saamcosta.buildbro.world.BuildSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

final class PlayerInfoTool implements Tool<BuildSession> {
	private static final double REACH = 64;

	@Override
	public String name() {
		return "player_info";
	}

	@Override
	public String description() {
		return "Where the player is: block position, facing direction, dimension, biome, ground height under them, "
				+ "the block they are looking at (up to 64 blocks away) and the limits of this request. "
				+ "Call it first: all other tools use absolute world coordinates.";
	}

	@Override
	public JsonObject inputSchema() {
		return Schema.object().build();
	}

	@Override
	public JsonElement call(JsonObject args, BuildSession session) throws ToolException {
		return session.onServer(game -> {
			ServerPlayer player = game.player();
			ServerLevel level = game.level();
			BlockPos pos = player.blockPosition();
			JsonObject out = new JsonObject();
			out.add("position", GameTools.position(pos));
			out.addProperty("facing", player.getDirection().getSerializedName());
			out.addProperty("yaw", Math.round(player.getYRot()));
			out.addProperty("pitch", Math.round(player.getXRot()));
			out.addProperty("dimension", level.dimension().identifier().toString());
			out.addProperty("biome", level.getBiome(pos).getRegisteredName());
			out.addProperty("ground_y", level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1);

			HitResult hit = player.pick(REACH, 1.0F, false);
			if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
				JsonObject target = GameTools.position(blockHit.getBlockPos());
				target.addProperty("block", BlockStates.describe(level.getBlockState(blockHit.getBlockPos())));
				target.addProperty("face", blockHit.getDirection().getSerializedName());
				out.add("looking_at", target);
			}

			JsonObject limits = new JsonObject();
			limits.add("origin", GameTools.position(session.origin()));
			limits.addProperty("radius", session.limits().radius());
			limits.addProperty("blocks_left", game.editor().blocksLeft());
			limits.addProperty("min_y", level.getMinY());
			limits.addProperty("max_y", level.getMaxY());
			out.add("limits", limits);
			return out;
		});
	}
}
