package com.raihanadf.blossom.kampung;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueInput;

// everything a kampung places goes through here, in coordinates relative to its centre, clipped to the chunk being generated
class KampungSite {
	final WorldGenLevel level;
	final BoundingBox chunk;
	final int cx;
	final int cz;

	KampungSite(WorldGenLevel level, BoundingBox chunk, int cx, int cz) {
		this.level = level;
		this.chunk = chunk;
		this.cx = cx;
		this.cz = cz;
	}

	// within `r` blocks of the chunk being built
	boolean near(int x, int z, int r) {
		return cx + x >= chunk.minX() - r && cx + x <= chunk.maxX() + r && cz + z >= chunk.minZ() - r && cz + z <= chunk.maxZ() + r;
	}

	boolean has(int x, int z) {
		return chunk.isInside(cx + x, chunk.minY(), cz + z);
	}

	BlockState get(int x, int y, int z) {
		return level.getBlockState(new BlockPos(cx + x, y, cz + z));
	}

	void set(int x, int y, int z, BlockState state) {
		if (chunk.isInside(cx + x, y, cz + z)) {
			BlockPos pos = new BlockPos(cx + x, y, cz + z);
			level.setBlock(pos, state, 2);
			// fences, bars, panes and walls join up with their neighbours once the chunk is done
			if (state.getBlock() instanceof CrossCollisionBlock || state.getBlock() instanceof WallBlock) {
				level.getChunk(pos).markPosForPostProcessing(pos);
			}
		}
	}

	void set(int x, int y, int z, String id) {
		set(x, y, z, block(id));
	}

	void set(int x, int y, int z, Block block) {
		set(x, y, z, block.defaultBlockState());
	}

	// only into open air, so leaves never cut into a roof
	void grow(int x, int y, int z, BlockState state) {
		if (has(x, z) && get(x, y, z).isAir()) {
			set(x, y, z, state);
		}
	}

	void sign(int x, int y, int z, Direction facing, String... lines) {
		if (!chunk.isInside(cx + x, y, cz + z)) {
			return;
		}
		set(x, y, z, facing(block("spruce_wall_sign"), facing));
		if (level.getBlockEntity(new BlockPos(cx + x, y, cz + z)) instanceof SignBlockEntity sign) {
			List<Component> text = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				text.add(Component.literal(i < lines.length ? lines[i] : ""));
			}
			RegistryAccess registries = level.registryAccess();
			CompoundTag tag = new CompoundTag();
			tag.put("front_text", SignText.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),
					new SignText(text, text, DyeColor.WHITE, true)).getOrThrow());
			sign.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
		}
	}

	static BlockState block(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}

	static BlockState facing(BlockState state, Direction direction) {
		return state.setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
	}

	static BlockState facing(String id, Direction direction) {
		return facing(block(id), direction);
	}

	static BlockState facing(Block block, Direction direction) {
		return facing(block.defaultBlockState(), direction);
	}
}
