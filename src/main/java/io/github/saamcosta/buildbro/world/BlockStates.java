package io.github.saamcosta.buildbro.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.saamcosta.buildbro.tool.ToolException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** Block state text such as {@code oak_stairs[facing=east,half=top]}, in the same syntax as /setblock. */
public final class BlockStates {
	private BlockStates() {
	}

	/** NBT is never accepted: the model only chooses blocks and their properties. */
	public static BlockState parse(String text) throws ToolException {
		try {
			return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, text.trim(), false).blockState();
		} catch (CommandSyntaxException e) {
			throw new ToolException("Invalid block '" + text + "': " + e.getMessage() + ". Use find_block to look up valid ids.");
		}
	}

	public static String describe(BlockState state) {
		return BlockStateParser.serialize(state);
	}

	public static String id(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}
}
