package com.raihanadf.blossom.nusantara;

import com.raihanadf.blossom.kampung.Satpam;
import com.raihanadf.blossom.kampung.Warga;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomEntities;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

// everything the nusantara builders place goes through here, clipped to the chunk being generated
public class Site {
	public final WorldGenLevel level;
	public final BoundingBox box;

	// a dry run changes nothing: it keeps what a builder would set (the last word on each block counts, and the
	// builder reads its own work back) to compare with what stands there in the end
	private final @Nullable Map<BlockPos, BlockState> planned;

	public Site(WorldGenLevel level, BoundingBox box) {
		this(level, box, null);
	}

	private Site(WorldGenLevel level, BoundingBox box, @Nullable Map<BlockPos, BlockState> planned) {
		this.level = level;
		this.box = box;
		this.planned = planned;
	}

	public static Site survey(WorldGenLevel level, BoundingBox box) {
		return new Site(level, box, new LinkedHashMap<>());
	}

	public boolean dry() {
		return planned != null;
	}

	// after a dry run: where the builder would put a different block than the one standing there. grass and flowers
	// that grew back don't count
	public List<BlockPos> changes() {
		List<BlockPos> out = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> entry : planned.entrySet()) {
			BlockState there = level.getBlockState(entry.getKey());
			boolean growth = !there.isAir() && there.canBeReplaced() && there.getFluidState().isEmpty();
			if (!there.is(entry.getValue().getBlock()) && !growth) {
				out.add(entry.getKey());
			}
		}
		return out;
	}

	public boolean has(int x, int z) {
		return x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
	}

	public boolean overlaps(int x0, int z0, int x1, int z1) {
		return x1 >= box.minX() && x0 <= box.maxX() && z1 >= box.minZ() && z0 <= box.maxZ();
	}

	public void set(int x, int y, int z, BlockState state) {
		if (has(x, z) && y >= level.getMinY() && y <= level.getMaxY()) {
			BlockPos pos = new BlockPos(x, y, z);
			if (planned != null) {
				planned.put(pos, state);
				return;
			}
			level.setBlock(pos, state, 2);
			// fences, bars, panes and walls join up with their neighbours once the chunk is finished
			if (state.getBlock() instanceof CrossCollisionBlock || state.getBlock() instanceof WallBlock) {
				level.getChunk(pos).markPosForPostProcessing(pos);
			}
		}
	}

	public void set(int x, int y, int z, String id) {
		set(x, y, z, block(id));
	}

	public BlockState get(int x, int y, int z) {
		BlockPos pos = new BlockPos(x, y, z);
		if (planned != null && planned.containsKey(pos)) {
			return planned.get(pos);
		}
		return level.getBlockState(pos);
	}

	public static BlockState block(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}

	public static BlockState facing(String id, Direction direction) {
		return block(id).setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
	}

	// trees, plants, flowers, snow and the like: things a builder clears away without a second thought (flowers and
	// saplings aren't "replaceable", so without their tags they were taken for something built and left floating)
	public static boolean isCover(BlockState state) {
		return state.isAir() || state.canBeReplaced() || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS)
				|| state.is(BlockTags.SAPLINGS) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.PUMPKIN)
				|| state.is(Blocks.MELON) || state.is(Blocks.AZALEA) || state.is(Blocks.BIG_DRIPLEAF) || state.is(Blocks.SMALL_DRIPLEAF) || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.BAMBOO)
				|| state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)
				|| state.is(Blocks.COCOA) || state.is(Blocks.BEE_NEST) || state.is(Blocks.CACTUS) || state.is(Blocks.MOSS_CARPET);
	}

	// the highest block of real ground in a column, looking through trees and plants (and water if `throughWater`)
	public int ground(int x, int z, boolean throughWater) {
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
		while (y > level.getMinY()) {
			BlockState state = get(x, y, z);
			if (!state.getFluidState().isEmpty()) {
				if (!throughWater) {
					return y;
				}
			} else if (!isCover(state)) {
				return y;
			}
			y--;
		}
		return y;
	}

	public int top(int x, int z) {
		return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
	}

	// clear everything above y in a column, up to the top of whatever stands there. the worldgen heightmap doesn't
	// count trees, so keep going up until a run of open air (else the crowns of trees float over a cutting). it only
	// takes away the land and what grows on it: at anything built (a viaduct over a road, a house) it stops
	public void clearAbove(int x, int y, int z) {
		int top = Math.max(top(x, z), y + 6);
		int open = 0;
		for (int k = y + 1; k <= level.getMaxY() && (k <= top + 1 || open < 8); k++) {
			BlockState state = get(x, k, z);
			if (state.isAir()) {
				open++;
				continue;
			}
			if (!isNatural(state)) {
				return;
			}
			open = 0;
			set(x, k, z, Blocks.AIR.defaultBlockState());
		}
	}

	// (in 26.x #dirt is only dirt itself: grass, podzol, moss and mud are under #substrate_overworld)
	public static boolean isNatural(BlockState state) {
		return isCover(state) || !state.getFluidState().isEmpty() || state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.CLAY)
				|| state.is(BlockTags.SNOW) || state.is(BlockTags.ICE) || state.is(BlockTags.BADLANDS_TERRACOTTA) || state.is(Blocks.COBBLESTONE)
				|| state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.COAL_ORE) || state.is(Blocks.IRON_ORE) || state.is(Blocks.COPPER_ORE)
				|| state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK)
				|| state.is(Blocks.POINTED_DRIPSTONE);
	}

	// level one column to `y`: grass (or `surface`) on top, soil below down to whatever ground was there, clear above.
	// a column already claimed by the railway or a building is left alone. bringing land down never buries anything,
	// but raising it is skipped where it would come up over a track beside it
	public void level(int x, int y, int z, String surface) {
		int ground = ground(x, z, true);
		if (isBuilt(x, ground, z) || y > ground && trackBelow(x, ground, y, z)) {
			return;
		}
		for (int k = Math.min(ground + 1, y); k < y; k++) {
			set(x, k, z, k < y - 3 ? "stone" : "dirt");
		}
		// a cave or an overhang under the new level gets filled, so nothing built on it stands over a hole
		for (int k = y - 1; k > y - 24 && k > level.getMinY(); k--) {
			BlockState below = get(x, k, z);
			if (!isCover(below) || !below.getFluidState().isEmpty()) {
				break;
			}
			set(x, k, z, k < y - 3 ? "stone" : "dirt");
		}
		set(x, y, z, surface);
		clearAbove(x, y, z);
	}

	// whether the top of a column is something built: a track (or the ballast it lies on), a platform, a wall
	public boolean isBuilt(int x, int top, int z) {
		BlockState state = get(x, top, z);
		return state.is(BlossomBlocks.TRACK) || get(x, top + 1, z).is(BlossomBlocks.TRACK) || !isNatural(state);
	}

	// whether a track within two blocks lies between the column's ground and `y`
	private boolean trackBelow(int x, int top, int y, int z) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int k = top - 2; k < y; k++) {
					if (get(x + dx, k, z + dz).is(BlossomBlocks.TRACK)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// a level that blends from `y` back to the land over `blend` blocks beyond `inside`
	public void blend(int x, int z, int y, double distanceOutside, int blend) {
		int land = ground(x, z, true);
		int target = (int) Math.round(y + (land - y) * Math.min(1, Math.max(0, distanceOutside / blend)));
		if (target != land || distanceOutside <= 0) {
			level(x, target, z, "grass_block");
		}
	}

	public void sign(int x, int y, int z, Direction facing, boolean wall, String... lines) {
		if (!has(x, z)) {
			return;
		}
		set(x, y, z, wall ? facing("spruce_wall_sign", facing) : block("spruce_sign").setValue(BlockStateProperties.ROTATION_16, facing.get2DDataValue() * 4));
		if (level.getBlockEntity(new BlockPos(x, y, z)) instanceof SignBlockEntity sign) {
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

	public void person(int x, int y, int z, int job, int skin, BlockPos post, BlockPos center, int radius) {
		if (!has(x, z)) {
			return;
		}
		if (job == Warga.GUARD) {
			Satpam satpam = BlossomEntities.SATPAM.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
			satpam.snapTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360, 0);
			satpam.finalizeSpawn(level, level.getCurrentDifficultyAt(new BlockPos(x, y, z)), EntitySpawnReason.STRUCTURE, null);
			satpam.settle(job, skin, post, null, post, center, radius);
			level.addFreshEntityWithPassengers(satpam);
			return;
		}
		Warga warga = BlossomEntities.WARGA.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		warga.snapTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360, 0);
		warga.finalizeSpawn(level, level.getCurrentDifficultyAt(new BlockPos(x, y, z)), EntitySpawnReason.STRUCTURE, null);
		warga.settle(job, skin, post, null, post, center, radius);
		level.addFreshEntityWithPassengers(warga);
	}
}
