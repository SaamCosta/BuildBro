package io.github.saamcosta.buildbro.tool.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.saamcosta.buildbro.tool.Args;
import io.github.saamcosta.buildbro.tool.Schema;
import io.github.saamcosta.buildbro.tool.Tool;
import io.github.saamcosta.buildbro.tool.ToolException;
import io.github.saamcosta.buildbro.world.BlockStates;
import io.github.saamcosta.buildbro.world.BuildSession;
import io.github.saamcosta.buildbro.world.Box;
import io.github.saamcosta.buildbro.world.WorldEditor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class FillTool implements Tool<BuildSession> {
	@Override
	public String name() {
		return "fill";
	}

	@Override
	public String description() {
		return "Fills a box (corners included) with one block. Modes: 'solid' fills everything; "
				+ "'hollow' builds the six faces and clears the inside to air (rooms); "
				+ "'outline' builds the six faces and leaves the inside untouched. "
				+ "Use air to dig or clear. Everything is recorded so the player can undo the request.";
	}

	@Override
	public JsonObject inputSchema() {
		return BoxArgs.schema(Schema.object())
				.string("block", "Block with optional state, as in /setblock: stone_bricks or oak_log[axis=x]", true)
				.enumeration("mode", "How to fill (default solid)", false, "solid", "hollow", "outline")
				.build();
	}

	@Override
	public JsonElement call(JsonObject args, BuildSession session) throws ToolException {
		Box box = BoxArgs.read(args);
		BlockState block = BlockStates.parse(Args.string(args, "block"));
		String mode = Args.string(args, "mode", "solid");
		if (!mode.equals("solid") && !mode.equals("hollow") && !mode.equals("outline")) {
			throw new ToolException("Mode must be solid, hollow or outline");
		}
		BlockState air = Blocks.AIR.defaultBlockState();

		return session.onServer(game -> {
			WorldEditor editor = game.editor();
			editor.checkFits(box);
			int changed = 0;
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				boolean shell = box.isShell(pos.getX(), pos.getY(), pos.getZ());
				BlockState target = shell || mode.equals("solid") ? block : mode.equals("hollow") ? air : null;
				if (target != null && editor.set(pos, target)) {
					changed++;
				}
			}
			JsonObject out = GameTools.box(box);
			out.addProperty("changed", changed);
			out.addProperty("blocks_left", editor.blocksLeft());
			return out;
		});
	}
}
