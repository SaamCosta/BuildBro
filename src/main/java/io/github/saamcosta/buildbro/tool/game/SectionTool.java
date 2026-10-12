package io.github.saamcosta.buildbro.tool.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.saamcosta.buildbro.tool.Schema;
import io.github.saamcosta.buildbro.tool.Tool;
import io.github.saamcosta.buildbro.tool.ToolException;
import io.github.saamcosta.buildbro.world.BlockStates;
import io.github.saamcosta.buildbro.world.BuildSession;
import io.github.saamcosta.buildbro.world.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lets the model see what it built: one slice of the world drawn as text. */
final class SectionTool implements Tool<BuildSession> {
	private static final int MAX_SIDE = 64;
	private static final String SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789#$%&*+=?@";

	@Override
	public String name() {
		return "section";
	}

	@Override
	public String description() {
		return "Draws one slice of the world as text so you can see what you built. The box must be one block thick "
				+ "on one axis: same y1 and y2 gives a floor plan seen from above (rows go from z1 to z2, columns from x1 to x2); "
				+ "same x or same z gives a vertical cut (rows from the top down). '.' is air, every other block gets a letter "
				+ "listed in 'legend'. At most 64 by 64. Use it after each storey and before finishing: check that floors are "
				+ "single, doors have open space on both sides, and roofs have no gaps.";
	}

	@Override
	public JsonObject inputSchema() {
		return BoxArgs.schema(Schema.object()).build();
	}

	@Override
	public JsonElement call(JsonObject args, BuildSession session) throws ToolException {
		Box box = BoxArgs.read(args);
		boolean plan = box.sizeY() == 1;
		boolean cutAlongZ = !plan && box.sizeX() == 1;
		boolean cutAlongX = !plan && !cutAlongZ && box.sizeZ() == 1;
		if (!plan && !cutAlongZ && !cutAlongX) {
			throw new ToolException("The box must be one block thick on one axis (same y, same x or same z)");
		}
		int columns = plan || cutAlongX ? box.sizeX() : box.sizeZ();
		int rows = plan ? box.sizeZ() : box.sizeY();
		if (columns > MAX_SIDE || rows > MAX_SIDE) {
			throw new ToolException("The slice is " + columns + " by " + rows + "; draw at most " + MAX_SIDE + " by " + MAX_SIDE);
		}

		return session.onServer(game -> {
			ServerLevel level = game.level();
			Map<String, Character> symbols = new LinkedHashMap<>();
			JsonArray lines = new JsonArray();
			for (int row = 0; row < rows; row++) {
				StringBuilder line = new StringBuilder(columns);
				for (int column = 0; column < columns; column++) {
					BlockPos pos;
					if (plan) {
						pos = new BlockPos(box.minX() + column, box.minY(), box.minZ() + row);
					} else if (cutAlongX) {
						pos = new BlockPos(box.minX() + column, box.maxY() - row, box.minZ());
					} else {
						pos = new BlockPos(box.minX(), box.maxY() - row, box.minZ() + column);
					}
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) {
						line.append('.');
						continue;
					}
					String id = BlockStates.id(state);
					Character symbol = symbols.get(id);
					if (symbol == null) {
						symbol = symbols.size() < SYMBOLS.length() ? SYMBOLS.charAt(symbols.size()) : '~';
						symbols.put(id, symbol);
					}
					line.append(symbol);
				}
				lines.add(line.toString());
			}

			JsonObject out = new JsonObject();
			if (plan) {
				out.addProperty("view", "from above at y=" + box.minY() + "; row 0 is z=" + box.minZ() + " (north), column 0 is x=" + box.minX() + " (west)");
			} else if (cutAlongX) {
				out.addProperty("view", "vertical cut at z=" + box.minZ() + " seen from the south; row 0 is y=" + box.maxY() + ", column 0 is x=" + box.minX() + " (west)");
			} else {
				out.addProperty("view", "vertical cut at x=" + box.minX() + " seen from the east; row 0 is y=" + box.maxY() + ", column 0 is z=" + box.minZ() + " (north)");
			}
			out.add("rows", lines);
			JsonObject legend = new JsonObject();
			symbols.forEach((id, symbol) -> legend.addProperty(symbol.toString(), id));
			out.add("legend", legend);
			return out;
		});
	}
}
