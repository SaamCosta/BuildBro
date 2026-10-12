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
import io.github.saamcosta.buildbro.world.RoofPlan;
import io.github.saamcosta.buildbro.world.WorldEditor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.List;
import java.util.Locale;

/** A whole roof in one call, so it never comes out with gaps. */
final class RoofTool implements Tool<BuildSession> {
	private static final int MAX_SIDE = 64;

	@Override
	public String name() {
		return "roof";
	}

	@Override
	public String description() {
		return "Builds a complete roof with no gaps over a rectangle. x1,z1,x2,z2 are the OUTER edge of the walls; y is the "
				+ "first roof layer, normally one above the top of the walls. Styles: 'gable' (two slopes; the triangles at the "
				+ "ends are closed with gable_block), 'hip' (four slopes), 'flat' (one layer of 'block'). For gable and hip, "
				+ "'block' must be a stairs block; the ridge gets the matching slab unless ridge_block is given. The roof "
				+ "encloses an attic: light it or give it a ceiling, because dark closed spaces spawn monsters.";
	}

	@Override
	public JsonObject inputSchema() {
		return Schema.object()
				.integer("x1", "Wall corner x", true)
				.integer("z1", "Wall corner z", true)
				.integer("x2", "Opposite wall corner x", true)
				.integer("z2", "Opposite wall corner z", true)
				.integer("y", "First roof layer (top of the walls + 1)", true)
				.enumeration("style", "Roof shape", true, "gable", "hip", "flat")
				.string("block", "Stairs block for gable and hip (dark_oak_stairs), any block for flat", true)
				.enumeration("ridge_axis", "Gable only: direction the ridge runs (default: along the longer side)", false, "x", "z")
				.integer("overhang", "How far the eaves stick out past the walls, 0 to 3 (default 1)", false)
				.string("ridge_block", "Block for the ridge line (default: the slab of the stairs)", false)
				.string("gable_block", "Gable only: block that closes the triangles at both ends (default: the full block of the stairs)", false)
				.build();
	}

	@Override
	public JsonElement call(JsonObject args, BuildSession session) throws ToolException {
		Box footprint = Box.of(Args.integer(args, "x1"), 0, Args.integer(args, "z1"), Args.integer(args, "x2"), 0, Args.integer(args, "z2"));
		if (footprint.sizeX() > MAX_SIDE || footprint.sizeZ() > MAX_SIDE) {
			throw new ToolException("The roof is larger than " + MAX_SIDE + " blocks on a side; split it");
		}
		int baseY = Args.integer(args, "y");
		RoofPlan.Style style = switch (Args.string(args, "style")) {
			case "gable" -> RoofPlan.Style.GABLE;
			case "hip" -> RoofPlan.Style.HIP;
			case "flat" -> RoofPlan.Style.FLAT;
			default -> throw new ToolException("style must be gable, hip or flat");
		};
		int overhang = Args.integer(args, "overhang", 1);
		if (overhang < 0 || overhang > 3) {
			throw new ToolException("overhang must be between 0 and 3");
		}
		String axis = Args.string(args, "ridge_axis", footprint.sizeX() >= footprint.sizeZ() ? "x" : "z");
		boolean ridgeAlongX = axis.equals("x");

		BlockState main = BlockStates.parse(Args.string(args, "block"));
		BlockState ridge;
		BlockState gable = null;
		if (style == RoofPlan.Style.FLAT) {
			ridge = main;
		} else {
			if (!(main.getBlock() instanceof StairBlock)) {
				throw new ToolException("For gable and hip roofs 'block' must be a stairs block, such as spruce_stairs");
			}
			String base = BlockStates.id(main).replaceFirst("_stairs$", "");
			ridge = args.has("ridge_block") ? BlockStates.parse(Args.string(args, "ridge_block")) : derived(base, "ridge_block", "_slab");
			if (ridge.getBlock() instanceof SlabBlock) {
				ridge = ridge.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
			}
			if (style == RoofPlan.Style.GABLE) {
				gable = args.has("gable_block") ? BlockStates.parse(Args.string(args, "gable_block")) : derived(base, "gable_block", "s", "_planks", "_block", "");
			}
		}

		List<RoofPlan.Piece> pieces = RoofPlan.plan(footprint, baseY, style, ridgeAlongX, overhang);
		int topY = pieces.stream().mapToInt(RoofPlan.Piece::y).max().orElse(baseY);
		Box bounds = Box.of(footprint.minX() - overhang, baseY, footprint.minZ() - overhang, footprint.maxX() + overhang, topY, footprint.maxZ() + overhang);
		BlockState ridgeState = ridge;
		BlockState gableState = gable;

		return session.onServer(game -> {
			WorldEditor editor = game.editor();
			editor.checkFits(bounds, pieces.size());
			int changed = 0;
			for (RoofPlan.Piece piece : pieces) {
				BlockState state = switch (piece.part()) {
					case STAIR -> main.setValue(StairBlock.FACING, direction(piece.rise())).setValue(StairBlock.HALF, Half.BOTTOM);
					case RIDGE -> ridgeState;
					case GABLE_WALL -> gableState;
				};
				if (editor.set(new BlockPos(piece.x(), piece.y(), piece.z()), state)) {
					changed++;
				}
			}
			JsonObject out = GameTools.box(bounds);
			out.addProperty("style", style.name().toLowerCase(Locale.ROOT));
			out.addProperty("ridge_y", topY);
			out.addProperty("changed", changed);
			out.addProperty("blocks_left", editor.blocksLeft());
			out.addProperty("reminder", "The space under this roof is closed: light it or add a ceiling.");
			return out;
		});
	}

	/** The slab or full block that goes with a stairs block: oak_stairs gives oak_slab and oak_planks. */
	private static BlockState derived(String base, String argument, String... suffixes) throws ToolException {
		for (String suffix : suffixes) {
			Identifier id = Identifier.tryParse(base + suffix);
			if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
				return BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
			}
		}
		throw new ToolException("Could not guess the matching block for " + base + "; give " + argument + " explicitly");
	}

	private static Direction direction(RoofPlan.Rise rise) {
		return switch (rise) {
			case NORTH -> Direction.NORTH;
			case SOUTH -> Direction.SOUTH;
			case EAST -> Direction.EAST;
			case WEST -> Direction.WEST;
		};
	}
}
