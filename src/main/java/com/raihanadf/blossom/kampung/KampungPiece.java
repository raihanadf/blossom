package com.raihanadf.blossom.kampung;

import static com.raihanadf.blossom.nusantara.Site.isNatural;

import com.raihanadf.blossom.furniture.LampBlock;
import com.raihanadf.blossom.kampung.KampungPlan.Building;
import com.raihanadf.blossom.kampung.KampungPlan.Kind;
import com.raihanadf.blossom.kampung.KampungPlan.Style;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomEntities;
import com.raihanadf.blossom.registry.BlossomWorldgen;
import com.raihanadf.blossom.sports.Football;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

// builds a kampung from its plan, one chunk at a time: levels the yard, fences it in, raises the houses and moves the people in
public class KampungPiece extends StructurePiece {
	private static final int WALL_HEIGHT = 3;
	private static final int EDGE_BLEND = 30;
	private static final String[] JAVA_WALLS = {"oak_planks", "spruce_planks", "bamboo_planks", "jungle_planks", "white_terracotta", "mud_bricks"};
	private static final String[] JAVA_POSTS = {"stripped_oak_log", "stripped_spruce_log", "stripped_bamboo_block", "stripped_jungle_log", "stripped_spruce_log", "stripped_oak_log"};
	private static final String[] DOORS = {"oak_door", "spruce_door", "bamboo_door", "jungle_door", "spruce_door", "oak_door"};
	private static final String[] FLOWERS = {"potted_red_tulip", "potted_poppy", "potted_fern", "potted_bamboo", "potted_orange_tulip", "potted_cornflower"};
	private static final String[] WOODS = {"oak", "spruce", "bamboo", "jungle", "spruce", "oak"};

	private final KampungPlan plan;
	private final int cx;
	private final int cz;
	private final int ground;

	public KampungPiece(long seed, int cx, int ground, int cz) {
		this(new KampungPlan(seed), cx, ground, cz);
	}

	// a kampung from a ready plan (the nusantara settlements size theirs and point the gate at the station)
	// columns something else already stands on (another kampung or the town square of the same settlement):
	// this kampung's yard slope stays off them so it never digs under their houses
	private BiPredicate<Integer, Integer> keep = (x, z) -> false;

	public KampungPiece keepOff(BiPredicate<Integer, Integer> keep) {
		this.keep = keep;
		return this;
	}

	public KampungPiece(KampungPlan plan, int cx, int ground, int cz) {
		super(BlossomWorldgen.KAMPUNG_PIECE, 0, new BoundingBox(cx - plan.extent - EDGE_BLEND, ground - 40, cz - plan.extent - EDGE_BLEND,
				cx + plan.extent + EDGE_BLEND, ground + 48, cz + plan.extent + EDGE_BLEND));
		this.plan = plan;
		this.cx = cx;
		this.cz = cz;
		this.ground = ground;
	}

	public KampungPiece(CompoundTag tag) {
		super(BlossomWorldgen.KAMPUNG_PIECE, tag);
		this.plan = new KampungPlan(tag.getLongOr("Seed", 0), tag.getFloatOr("Gate", Float.NaN), tag.getIntOr("Size", 10));
		this.cx = tag.getIntOr("CX", 0);
		this.cz = tag.getIntOr("CZ", 0);
		this.ground = tag.getIntOr("Ground", 64);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putLong("Seed", plan.seed);
		tag.putFloat("Gate", plan.gateAngle);
		tag.putInt("Size", plan.size);
		tag.putInt("CX", cx);
		tag.putInt("CZ", cz);
		tag.putInt("Ground", ground);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunk, ChunkPos chunkPos, BlockPos reference) {
		Site site = new Site(level, chunk);
		levelGround(site);
		fenceAndGate(site);
		paths(site);
		for (Building building : plan.buildings) {
			switch (building.kind()) {
				case HOUSE -> house(site, building);
				case KITCHEN -> kitchen(site, building);
				case FIELD -> field(site, building);
				case POST -> post(site, building);
				case WARUNG -> warung(site, building);
				case MUSHOLLA -> musholla(site, building);
				case RONDA -> ronda(site, building);
				case SUMUR -> sumur(site, building);
				case KANDANG -> kandang(site, building);
				case JEMURAN -> jemuran(site, building);
			}
			if (building.kind() == Kind.HOUSE && building.palette() % 3 != 0) {
				antenna(site, building);
			}
		}
		moveIn(site);
	}

	// everything is placed through this, clipped to the chunk being generated
	private class Site {
		final WorldGenLevel level;
		final BoundingBox chunk;

		Site(WorldGenLevel level, BoundingBox chunk) {
			this.level = level;
			this.chunk = chunk;
		}

		boolean has(int x, int z) {
			return chunk.isInside(cx + x, chunk.minY(), cz + z);
		}

		void set(int x, int y, int z, BlockState state) {
			if (chunk.isInside(cx + x, y, cz + z)) {
				level.setBlock(new BlockPos(cx + x, y, cz + z), state, 2);
			}
		}

		void set(int x, int y, int z, String id) {
			set(x, y, z, block(id));
		}
	}

	private static BlockState block(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}

	private static BlockState facing(String id, Direction direction) {
		return block(id).setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
	}

	// flat yard inside the fence, then a gentle slope back to the natural terrain around it
	// the height of a kampung's ground `outside` blocks out from its fence once it is built: its own level inside,
	// sloping back to `natural` over a width that grows with the height to make up
	public static int yardLevel(float outside, int ground, int natural) {
		if (outside > EDGE_BLEND) {
			return natural;
		}
		int width = Math.min(EDGE_BLEND, Math.max(8, Math.abs(natural - ground) * 2));
		return outside <= 0 ? ground : Math.round(Mth.lerp(Math.min(1, outside / width), ground, natural));
	}

	private void levelGround(Site site) {
		int reach = plan.extent + EDGE_BLEND;
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				if (!site.has(x, z)) {
					continue;
				}
				float outside = plan.distanceOutside(x, z);
				if (outside > EDGE_BLEND || outside > 0 && keep.test(cx + x, cz + z)) {
					continue;
				}
				int surface = site.level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx + x, cz + z) - 1;
				int natural = naturalGround(site, x, z, surface);
				// a railway or anything else built here stays: the yard's slope never buries a track
				if (railway(site, x, natural, z)) {
					continue;
				}
				int top = yardLevel(outside, ground, natural);
				// past the slope the land (and any water on it) is left as it is
				if (top == natural && outside > 0) {
					continue;
				}
				if (top > natural) {
					for (int y = natural + 1; y < top; y++) {
						site.set(x, y, z, y < top - 3 ? "stone" : "dirt");
					}
				}
				// cut down to the slope and take the trees off it, but never drain a lake or the sea beside it
				// the worldgen heightmap misses what was built or grown since, so keep going until a run of open air
				int open = 0;
				for (int y = top + 1; y < site.level.getMaxY() && (y <= Math.max(surface, top + 6) || open < 8); y++) {
					BlockState above = site.level.getBlockState(new BlockPos(cx + x, y, cz + z));
					if (above.isAir()) {
						open++;
						continue;
					}
					open = 0;
					// stop at anything built over the yard (a railway viaduct passing over the village)
					if (!isNatural(above)) {
						break;
					}
					if (above.getFluidState().isEmpty()) {
						site.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
				site.set(x, top - 1, z, "dirt");
				site.set(x, top, z, "grass_block");
			}
		}
	}

	private boolean railway(Site site, int x, int y, int z) {
		BlockState top = site.level.getBlockState(new BlockPos(cx + x, y, cz + z));
		if (!isNatural(top)) {
			return true;
		}
		if (!top.is(Blocks.GRAVEL)) {
			return false;
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (site.level.getBlockState(new BlockPos(cx + x + dx, y + 1, cz + z + dz)).is(BlossomBlocks.TRACK)) {
					return true;
				}
			}
		}
		return false;
	}

	private int naturalGround(Site site, int x, int z, int surface) {
		int y = surface;
		while (y > site.level.getMinY()) {
			BlockState state = site.level.getBlockState(new BlockPos(cx + x, y, cz + z));
			if (!state.getFluidState().isEmpty() || isCover(state)) {
				y--;
			} else {
				break;
			}
		}
		return y;
	}

	private static boolean isCover(BlockState state) {
		return state.canBeReplaced() || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
				|| state.is(Blocks.SUGAR_CANE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON) || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.BAMBOO)
				|| state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK);
	}

	private void fenceAndGate(Site site) {
		BlockState fence = block(plan.bambooFence ? "bamboo_fence" : "spruce_fence");
		int reach = plan.extent;
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				if (plan.isGatePillar(x, z)) {
					// gapura: two brick pillars with a lantern on each
					for (int y = 1; y <= 4; y++) {
						site.set(x, ground + y, z, "bricks");
					}
					site.set(x, ground + 5, z, block("brick_slab"));
					site.set(x, ground + 6, z, "lantern");
				} else if (isFenceAt(x, z)) {
					BlockState state = fence;
					for (Direction side : Direction.Plane.HORIZONTAL) {
						int nx = x + side.getStepX();
						int nz = z + side.getStepZ();
						state = state.setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(side), isFenceAt(nx, nz) || plan.isGatePillar(nx, nz));
					}
					site.set(x, ground + 1, z, state);
					if (Math.floorMod(x * 31 + z * 17, 9) == 0) {
						site.set(x, ground + 2, z, "lantern");
					}
				}
			}
		}
	}

	private boolean isFenceAt(int x, int z) {
		return plan.isFence(x, z) && !plan.isGateOpening(x, z) && !plan.isGatePillar(x, z);
	}

	// footpaths: a wide one from the gate to the plaza, a narrow one from every door
	private void paths(Site site) {
		float dx = Mth.cos(plan.gateAngle);
		float dz = Mth.sin(plan.gateAngle);
		for (float t = -3; t <= Mth.sqrt(plan.gateX * plan.gateX + plan.gateZ * plan.gateZ) + 3; t += 0.5f) {
			for (int w = -1; w <= 1; w++) {
				path(site, Math.round(dx * t - dz * w), Math.round(dz * t + dx * w));
			}
		}
		for (Building building : plan.buildings) {
			if (building.kind() == Kind.FIELD) {
				continue;
			}
			int sx = building.x(building.width() / 2, building.depth());
			int sz = building.z(building.width() / 2, building.depth());
			int steps = Math.max(Math.abs(sx), Math.abs(sz));
			for (int i = 0; i <= steps; i++) {
				path(site, Math.round(sx - sx * (float) i / steps), Math.round(sz - sz * (float) i / steps));
			}
		}
		// the plaza in the middle with a tall bamboo flagpole flying merah putih, a lantern on top for the night.
		// it pokes out over the roofs so a kampung is easy to spot from a distance
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				path(site, x, z);
			}
		}
		int top = ground + 14;
		site.set(0, ground + 1, 0, "stone_brick_wall");
		for (int y = ground + 2; y < top; y++) {
			site.set(0, y, 0, "bamboo_fence");
		}
		site.set(0, top, 0, "lantern");
		for (int x = 1; x <= 4; x++) {
			site.set(x, top - 2, 0, "red_wool");
			site.set(x, top - 3, 0, "white_wool");
		}
	}

	private void path(Site site, int x, int z) {
		if (plan.building(x, z) == null && plan.distanceOutside(x, z) <= 3 && !isFenceAt(x, z)) {
			site.set(x, ground, z, "dirt_path");
		}
	}

	private void house(Site site, Building b) {
		int w = b.width();
		int raise = Style.raise(b.style());
		// the stairs of a raised house take the last `raise` rows of the footprint, the porch sits right behind them
		int d = b.depth() - raise;
		int palette = b.palette();
		int floor = ground + raise;
		boolean torajan = b.style() == Style.TONGKONAN;
		boolean bugis = b.style() == Style.PANGGUNG;
		String wall = torajan ? "dark_oak_planks" : bugis ? (palette % 2 == 0 ? "spruce_planks" : "dark_oak_planks") : JAVA_WALLS[palette % JAVA_WALLS.length];
		String post = torajan ? "dark_oak_log" : bugis ? "spruce_log" : JAVA_POSTS[palette % JAVA_POSTS.length];
		String floorBlock = torajan ? "dark_oak_planks" : bugis ? "spruce_planks" : (palette % 3 == 0 ? "bamboo_planks" : "spruce_planks");
		String wood = torajan ? "dark_oak" : bugis ? "spruce" : WOODS[palette % WOODS.length];
		int front = d - 3;
		int mid = w / 2;

		// stilts for the raised houses
		if (raise > 0) {
			for (int lx = 0; lx < w; lx += Math.max(1, (w - 1) / 2)) {
				for (int lz : new int[] {0, front / 2, front, d - 1}) {
					for (int y = ground + 1; y < floor; y++) {
						site.set(b.x(lx, lz), y, b.z(lx, lz), post);
					}
				}
			}
		}

		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				site.set(x, floor, z, floorBlock);
				boolean edgeX = lx == 0 || lx == w - 1;
				boolean walled = lz <= front && (edgeX || lz == 0 || lz == front);
				boolean porchPost = lz == d - 1 && edgeX;
				for (int y = floor + 1; y <= floor + WALL_HEIGHT; y++) {
					if (walled && edgeX && (lz == 0 || lz == front)) {
						site.set(x, y, z, post);
					} else if (walled) {
						boolean plinth = !torajan && !bugis && palette % 4 == 1 && y == floor + 1;
						site.set(x, y, z, plinth ? "bricks" : torajan && y == floor + 2 && lz == front ? "red_terracotta" : wall);
					} else if (porchPost) {
						site.set(x, y, z, post);
					} else {
						site.set(x, y, z, "air");
					}
				}
				// the beam all around under the roof
				if (lx == 0 || lx == w - 1 || lz == 0 || lz == d - 1 || lz == front) {
					site.set(x, floor + WALL_HEIGHT + 1, z, lz > front && !edgeX && lz != d - 1 ? "air" : post);
				}
			}
		}

		// windows on the sides and the back, beside the door at the front
		for (int lz = 1; lz < front; lz++) {
			if ((lz + palette) % 2 == 0) {
				window(site, b, 0, lz, floor + 2, true);
				window(site, b, w - 1, lz, floor + 2, true);
			}
		}
		for (int lx = 1; lx < w - 1; lx++) {
			if (lx != mid && (lx + palette) % 2 == 1) {
				window(site, b, lx, 0, floor + 2, false);
			}
			if (lx == 1 || lx == w - 2) {
				window(site, b, lx, front, floor + 2, false);
			}
		}

		// an open door
		Direction out = b.turn(Direction.SOUTH);
		BlockState door = facing(DOORS[palette % DOORS.length], out).setValue(BlockStateProperties.OPEN, true);
		site.set(b.x(mid, front), floor + 1, b.z(mid, front), door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		site.set(b.x(mid, front), floor + 2, b.z(mid, front), door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));

		// a straight staircase from the middle of the porch down to the yard
		for (int i = 0; i < raise; i++) {
			int x = b.x(mid, d + i);
			int z = b.z(mid, d + i);
			int step = floor - i;
			site.set(x, step, z, facing(wood + "_stairs", b.turn(Direction.NORTH)));
			for (int y = ground + 1; y < step; y++) {
				site.set(x, y, z, floorBlock);
			}
			for (int y = step + 1; y <= floor + WALL_HEIGHT; y++) {
				site.set(x, y, z, "air");
			}
		}

		// beds against the back wall, a table with a lamp, a chest and a barrel
		DyeColor bedColor = DyeColor.values()[palette % DyeColor.values().length];
		for (int i = 0; i < b.residents(); i++) {
			int lx = i == 0 ? 1 : w - 2;
			Direction headward = b.turn(Direction.NORTH);
			BlockState bed = facing(bedColor.getSerializedName() + "_bed", headward);
			site.set(b.x(lx, 2), floor + 1, b.z(lx, 2), bed.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
			site.set(b.x(lx, 1), floor + 1, b.z(lx, 1), bed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		}
		Block table = BlossomBlocks.TABLES.get(wood);
		Block chair = BlossomBlocks.CHAIRS.get(wood);
		site.set(b.x(mid, 1), floor + 1, b.z(mid, 1), table.defaultBlockState());
		site.set(b.x(mid, 1), floor + 2, b.z(mid, 1), BlossomBlocks.LAMP.defaultBlockState().setValue(LampBlock.LIT, true).setValue(LampBlock.GLOW, 4)
				.setValue(LampBlock.COLOR, DyeColor.values()[(palette / 7) % DyeColor.values().length]));
		if (front > 3) {
			site.set(b.x(mid, 2), floor + 1, b.z(mid, 2), chair.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, b.turn(Direction.SOUTH)));
		}
		site.set(b.x(w - 2, front - 1), floor + 1, b.z(w - 2, front - 1), "barrel");
		site.set(b.x(1, front - 1), floor + 1, b.z(1, front - 1), facing("chest", b.turn(Direction.EAST)));

		// the porch: chairs looking out, flowers, a lantern under the beam
		for (int lx : new int[] {1, w - 2}) {
			if (raise == 0 || lx > mid) {
				site.set(b.x(lx, d - 2), floor + 1, b.z(lx, d - 2), chair.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, b.turn(Direction.NORTH)));
			}
		}
		if (raise == 0) {
			site.set(b.x(1, d - 1), floor + 1, b.z(1, d - 1), FLOWERS[palette % FLOWERS.length]);
		}
		site.set(b.x(w - 2, d - 1), floor + 1, b.z(w - 2, d - 1), FLOWERS[(palette / 3) % FLOWERS.length]);
		site.set(b.x(mid, d - 1), floor + WALL_HEIGHT, b.z(mid, d - 1), block("lantern").setValue(BlockStateProperties.HANGING, true));

		int roofY = floor + WALL_HEIGHT + 1;
		switch (b.style()) {
			case LIMASAN -> hipRoof(site, b, roofY, 99, palette % 3 == 0 ? "mud_brick" : "brick");
			case JOGLO -> hipRoof(site, b, roofY, 2, "brick");
			case KAMPUNG -> gableRoof(site, b, roofY, "brick", wall, false);
			case PANGGUNG -> gableRoof(site, b, roofY, palette % 2 == 0 ? "brick" : wood, wall, false);
			case TONGKONAN -> gableRoof(site, b, roofY, palette % 2 == 0 ? "dark_oak" : "bamboo_mosaic", wall, true);
		}
	}

	private void window(Site site, Building b, int lx, int lz, int y, boolean sideWall) {
		BlockState pane = block("glass_pane");
		Direction along = b.turn(sideWall ? Direction.NORTH : Direction.EAST);
		pane = pane.setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(along), true)
				.setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(along.getOpposite()), true);
		site.set(b.x(lx, lz), y, b.z(lx, lz), pane);
	}

	// stairs of `material` named like vanilla: brick -> brick_stairs, bricks
	private static String fullBlock(String material) {
		return switch (material) {
			case "brick", "mud_brick" -> material + "s";
			case "bamboo_mosaic" -> "bamboo_mosaic";
			default -> material + "_planks";
		};
	}

	// rings of stairs stepping inwards. joglo roofs turn steep after `steepFrom` rings
	private void hipRoof(Site site, Building b, int y0, int steepFrom, String material) {
		String stairs = material + "_stairs";
		BlockState full = block(fullBlock(material));
		for (int k = 0; ; k++) {
			int x0 = -1 + k;
			int x1 = b.width() - k;
			int z0 = -1 + k;
			int z1 = b.depth() - k;
			int y = y0 + (k < steepFrom ? k : steepFrom + 2 * (k - steepFrom));
			if (x1 - x0 <= 1 || z1 - z0 <= 1) {
				for (int lx = x0; lx <= x1; lx++) {
					for (int lz = z0; lz <= z1; lz++) {
						site.set(b.x(lx, lz), y, b.z(lx, lz), full);
					}
				}
				return;
			}
			for (int lx = x0; lx <= x1; lx++) {
				for (int lz = z0; lz <= z1; lz++) {
					boolean onX = lx == x0 || lx == x1;
					boolean onZ = lz == z0 || lz == z1;
					if (!onX && !onZ) {
						continue;
					}
					BlockState state = onX && onZ ? full : facing(stairs, b.turn(lz == z0 ? Direction.SOUTH : lz == z1 ? Direction.NORTH : lx == x0 ? Direction.EAST : Direction.WEST));
					site.set(b.x(lx, lz), y, b.z(lx, lz), state);
					if (k > steepFrom) {
						site.set(b.x(lx, lz), y - 1, b.z(lx, lz), full);
					}
				}
			}
		}
	}

	// two sloping sides meeting at a ridge. along the front for java and bugis houses,
	// front to back with sweeping raised ends for the toraja tongkonan
	private void gableRoof(Site site, Building b, int y0, String material, String wall, boolean saddle) {
		String stairs = material + "_stairs";
		BlockState full = block(fullBlock(material));
		int span = saddle ? b.width() : b.depth();
		int length = saddle ? b.depth() : b.width();
		int reach = saddle ? 2 : 1;
		for (int k = 0; ; k++) {
			int low = -1 + k;
			int high = span - k;
			if (high < low) {
				return;
			}
			for (int along = -reach; along < length + reach; along++) {
				int lift = !saddle ? 0 : along <= -2 || along >= length + 1 ? 2 : along <= 0 || along >= length - 1 ? 1 : 0;
				int y = y0 + k + lift;
				for (int across : high == low ? new int[] {low} : new int[] {low, high}) {
					int lx = saddle ? across : along;
					int lz = saddle ? along : across;
					Direction up = saddle ? (across == low ? Direction.EAST : Direction.WEST) : (across == low ? Direction.SOUTH : Direction.NORTH);
					site.set(b.x(lx, lz), y, b.z(lx, lz), high == low ? full : facing(stairs, b.turn(up)));
				}
				// close the triangular ends with wall, painted in toraja colours on the tongkonan
				if (k > 0 && (along == 0 || along == length - 1 || (saddle && along == b.depth() - 3))) {
					for (int across = low + 1; across < high; across++) {
						int lx = saddle ? across : along;
						int lz = saddle ? along : across;
						String fill = saddle ? new String[] {"red_terracotta", "black_terracotta", "yellow_terracotta", "white_terracotta"}[Math.floorMod(across + k, 4)] : wall;
						site.set(b.x(lx, lz), y0 + k, b.z(lx, lz), fill);
					}
				}
			}
		}
	}

	// dapur: an open pavilion with a cooking pot on the fire, a cutting board and a table to eat at
	private void kitchen(Site site, Building b) {
		int w = b.width();
		int d = b.depth();
		int mid = w / 2;
		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				site.set(x, ground, z, "packed_mud");
				boolean corner = (lx == 0 || lx == w - 1) && (lz == 0 || lz == d - 1);
				for (int y = ground + 1; y <= ground + 3; y++) {
					site.set(x, y, z, corner ? "stripped_bamboo_block" : "air");
				}
				if (lx == 0 || lx == w - 1 || lz == 0 || lz == d - 1) {
					site.set(x, ground + 4, z, "stripped_bamboo_block");
				}
			}
		}
		// signal fire: the cooking smoke rises high above the village, you can spot a kampung from far away
		site.set(b.x(mid, 1), ground + 1, b.z(mid, 1), block("campfire").setValue(BlockStateProperties.SIGNAL_FIRE, true));
		site.set(b.x(mid, 1), ground + 2, b.z(mid, 1), BlossomBlocks.COOKING_POT.defaultBlockState());
		site.set(b.x(1, 1), ground + 1, b.z(1, 1), "barrel");
		site.set(b.x(1, 1), ground + 2, b.z(1, 1), BlossomBlocks.CUTTING_BOARD.defaultBlockState());
		site.set(b.x(w - 2, 1), ground + 1, b.z(w - 2, 1), "barrel");
		site.set(b.x(mid, d - 2), ground + 1, b.z(mid, d - 2), BlossomBlocks.TABLES.get("bamboo").defaultBlockState());
		site.set(b.x(mid - 1, d - 2), ground + 1, b.z(mid - 1, d - 2), BlossomBlocks.CHAIRS.get("bamboo").defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, b.turn(Direction.WEST)));
		site.set(b.x(mid + 1, d - 2), ground + 1, b.z(mid + 1, d - 2), BlossomBlocks.CHAIRS.get("bamboo").defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, b.turn(Direction.EAST)));
		hipRoof(site, b, ground + 4, 99, b.palette() % 2 == 0 ? "brick" : "bamboo_mosaic");
	}

	// sawah: rows of rice and vegetables around a water channel, with a scarecrow in the corner
	private void field(Site site, Building b) {
		int w = b.width();
		int d = b.depth();
		RandomSource random = RandomSource.create(plan.seed ^ b.palette());
		Block[] rows = new Block[w];
		for (int lx = 0; lx < w; lx++) {
			int roll = random.nextInt(10);
			rows[lx] = roll < 6 ? BlossomBlocks.RICE_CROP : roll < 8 ? block("carrots").getBlock() : roll < 9 ? block("potatoes").getBlock() : block("wheat").getBlock();
		}
		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				if (lz == d / 2) {
					site.set(x, ground, z, "water");
					continue;
				}
				if (lx == 0 && lz == 0) {
					site.set(x, ground + 1, z, block(plan.bambooFence ? "bamboo_fence" : "spruce_fence"));
					site.set(x, ground + 2, z, "hay_block");
					site.set(x, ground + 3, z, facing("carved_pumpkin", b.turn(Direction.SOUTH)));
					continue;
				}
				site.set(x, ground, z, block("farmland").setValue(BlockStateProperties.MOISTURE, 7));
				site.set(x, ground + 1, z, rows[lx].defaultBlockState().setValue(CropBlock.AGE, random.nextInt(CropBlock.MAX_AGE + 1)));
			}
		}
	}

	// pos satpam: a little roofed post by the gate with a chair and a lantern
	private void post(Site site, Building b) {
		for (int lx = 0; lx < 3; lx++) {
			for (int lz = 0; lz < 3; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				site.set(x, ground, z, "bamboo_planks");
				boolean corner = lx != 1 && lz != 1;
				for (int y = ground + 1; y <= ground + 3; y++) {
					site.set(x, y, z, corner ? "stripped_bamboo_block" : "air");
				}
				site.set(x, ground + 4, z, block("bamboo_mosaic_slab").setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
			}
		}
		site.set(b.x(1, 1), ground + 1, b.z(1, 1), BlossomBlocks.CHAIRS.get("bamboo").defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, b.turn(Direction.SOUTH).getOpposite()));
		site.set(b.x(1, 0), ground + 3, b.z(1, 0), block("lantern").setValue(BlockStateProperties.HANGING, true));
	}

	// warung: a little shop with a striped awning, a counter full of jars and sacks, and a bench out front
	private void warung(Site site, Building b) {
		int w = b.width();
		int d = b.depth();
		String wall = WOODS[b.palette() % WOODS.length] + "_planks";
		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				site.set(x, ground, z, "spruce_planks");
				boolean walled = lz <= d - 2 && (lx == 0 || lx == w - 1 || lz == 0);
				for (int y = ground + 1; y <= ground + 3; y++) {
					site.set(x, y, z, walled ? wall : "air");
				}
				site.set(x, ground + 4, z, lz <= d - 2 ? block("spruce_slab").setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM) : block("air"));
			}
			// the counter across the front, the awning over it in red and white
			int counter = d - 2;
			if (lx > 0 && lx < w - 1) {
				site.set(b.x(lx, counter), ground + 1, b.z(lx, counter), "spruce_planks");
				site.set(b.x(lx, counter), ground + 2, b.z(lx, counter), lx % 2 == 0 ? "decorated_pot" : "barrel");
			}
			site.set(b.x(lx, d - 1), ground + 3, b.z(lx, d - 1), lx % 2 == 0 ? "red_wool" : "white_wool");
		}
		// shelves of goods at the back
		for (int lx = 1; lx < w - 1; lx++) {
			site.set(b.x(lx, 1), ground + 1, b.z(lx, 1), "chiseled_bookshelf");
			site.set(b.x(lx, 1), ground + 2, b.z(lx, 1), lx % 2 == 0 ? "hay_block" : "melon");
		}
		site.set(b.x(0, d - 1), ground + 1, b.z(0, d - 1), BlossomBlocks.CHAIRS.get("bamboo").defaultBlockState()
				.setValue(BlockStateProperties.HORIZONTAL_FACING, b.turn(Direction.SOUTH)));
		site.set(b.x(w - 1, d - 1), ground + 1, b.z(w - 1, d - 1), "potted_bamboo");
		site.set(b.x(w / 2, d - 2), ground + 3, b.z(w / 2, d - 2), block("lantern").setValue(BlockStateProperties.HANGING, true));
	}

	// the musholla: white walls, a green tiered roof with a little finial, the bedug drum and a kentongan on the porch
	private void musholla(Site site, Building b) {
		int w = b.width();
		int d = b.depth();
		int mid = w / 2;
		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				site.set(x, ground, z, "smooth_quartz");
				site.set(x, ground + 1, z, lz < d - 2 ? (lx % 2 == lz % 2 ? "green_carpet" : "lime_carpet") : "air");
				boolean walled = lz < d - 2 && (lx == 0 || lx == w - 1 || lz == 0 || lz == d - 3);
				boolean porchPost = lz == d - 1 && (lx == 0 || lx == w - 1);
				for (int y = ground + 1; y <= ground + 4; y++) {
					if (walled) {
						boolean window = y == ground + 2 && lx % 2 == 1 && (lz == 0 || lx == 0 || lx == w - 1);
						site.set(x, y, z, window ? "glass_pane" : "white_concrete");
					} else if (porchPost) {
						site.set(x, y, z, "white_concrete");
					} else if (y > ground + 1 || lz >= d - 2) {
						site.set(x, y, z, "air");
					}
				}
			}
		}
		// wide doorway in the middle of the front wall
		for (int lx = mid - 1; lx <= mid + 1; lx++) {
			site.set(b.x(lx, d - 3), ground + 1, b.z(lx, d - 3), "air");
			site.set(b.x(lx, d - 3), ground + 2, b.z(lx, d - 3), "air");
		}
		// mihrab niche in the back wall
		site.set(b.x(mid, 0), ground + 2, b.z(mid, 0), "green_glazed_terracotta");
		// bedug: the big drum lying on the porch, and the kentongan hanging beside it
		site.set(b.x(1, d - 1), ground + 1, b.z(1, d - 1), block("barrel").setValue(BlockStateProperties.FACING, b.turn(Direction.EAST)));
		site.set(b.x(w - 2, d - 1), ground + 3, b.z(w - 2, d - 1), "bamboo_block");
		site.set(b.x(w - 2, d - 1), ground + 4, b.z(w - 2, d - 1), "iron_chain");
		// green tiered roof, then the finial on top
		hipRoof(site, b, ground + 5, 2, "prismarine_brick");
		for (int y = ground + 5; y <= ground + 14 && site.has(b.x(mid, d / 2), b.z(mid, d / 2)); y++) {
			BlockState here = site.level.getBlockState(new BlockPos(cx + b.x(mid, d / 2), y, cz + b.z(mid, d / 2)));
			if (here.isAir()) {
				site.set(b.x(mid, d / 2), y, b.z(mid, d / 2), "lightning_rod");
				break;
			}
		}
	}

	// pos ronda: a raised bamboo platform under a little roof where the men keep the night watch, with the kentongan
	private void ronda(Site site, Building b) {
		for (int lx = 0; lx < 3; lx++) {
			for (int lz = 0; lz < 3; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				boolean corner = lx != 1 && lz != 1;
				site.set(x, ground + 1, z, corner ? "stripped_bamboo_block" : "bamboo_slab");
				for (int y = ground + 2; y <= ground + 3; y++) {
					site.set(x, y, z, corner ? "bamboo_fence" : "air");
				}
				site.set(x, ground + 4, z, block("bamboo_mosaic_slab").setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
			}
		}
		site.set(b.x(1, 1), ground + 5, b.z(1, 1), block("bamboo_mosaic_slab").setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
		// the kentongan: a hollow log on a chain to drum out the alarm
		site.set(b.x(1, 2), ground + 3, b.z(1, 2), "iron_chain");
		site.set(b.x(1, 2), ground + 2, b.z(1, 2), "bamboo_block");
		site.set(b.x(1, 0), ground + 3, b.z(1, 0), block("lantern").setValue(BlockStateProperties.HANGING, true));
	}

	// sumur: a stone well with a little roof and a bucket on its rope
	private void sumur(Site site, Building b) {
		for (int lx = 0; lx < 3; lx++) {
			for (int lz = 0; lz < 3; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				boolean middle = lx == 1 && lz == 1;
				for (int y = ground - 4; y <= ground; y++) {
					site.set(x, y, z, middle ? (y < ground - 1 ? "water" : "air") : "cobblestone");
				}
				site.set(x, ground + 1, z, middle ? block("air") : block("mossy_cobblestone_wall"));
				boolean corner = lx != 1 && lz != 1;
				for (int y = ground + 2; y <= ground + 3; y++) {
					site.set(x, y, z, corner && (lx == lz) ? "spruce_fence" : "air");
				}
				site.set(x, ground + 4, z, block("spruce_slab").setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
			}
		}
		site.set(b.x(1, 1), ground + 3, b.z(1, 1), "iron_chain");
		site.set(b.x(1, 1), ground + 2, b.z(1, 1), "cauldron");
	}

	// kandang ayam: a little coop with a fenced run, nest boxes of hay
	private void kandang(Site site, Building b) {
		int w = b.width();
		int d = b.depth();
		BlockState fence = block(plan.bambooFence ? "bamboo_fence" : "spruce_fence");
		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				site.set(x, ground, z, "coarse_dirt");
				boolean edge = lx == 0 || lx == w - 1 || lz == 0 || lz == d - 1;
				boolean coop = lz <= 1;
				site.set(x, ground + 1, z, coop ? block(lx == 0 || lx == w - 1 ? "stripped_bamboo_block" : "hay_block") : edge ? fence : block("air"));
				site.set(x, ground + 2, z, coop && (lx == 0 || lx == w - 1) ? block("stripped_bamboo_block") : block("air"));
				if (coop) {
					site.set(x, ground + 3, z, block("bamboo_mosaic_slab").setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
				}
			}
		}
		site.set(b.x(w / 2, d - 1), ground + 1, b.z(w / 2, d - 1), facing("spruce_fence_gate", b.turn(Direction.SOUTH)));
		site.set(b.x(1, d - 2), ground + 1, b.z(1, d - 2), block("water_cauldron").setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		for (int i = 0; i < 3; i++) {
			BlockPos at = new BlockPos(cx + b.x(1 + i, d - 2), ground + 1, cz + b.z(1 + i, d - 2));
			if (site.chunk.isInside(at) && i > 0) {
				var chicken = EntityTypes.CHICKEN.create(site.level.getLevel(), EntitySpawnReason.STRUCTURE);
				chicken.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, i * 90, 0);
				chicken.setPersistenceRequired();
				site.level.addFreshEntity(chicken);
			}
		}
	}

	// jemur gabah: harvested rice spread out on mats to dry in the sun, with a couple of sacks
	private void jemuran(Site site, Building b) {
		for (int lx = 0; lx < b.width(); lx++) {
			for (int lz = 0; lz < b.depth(); lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				site.set(x, ground, z, "packed_mud");
				site.set(x, ground + 1, z, (lx + lz) % 5 == 0 ? "brown_carpet" : "yellow_carpet");
			}
		}
		site.set(b.x(0, 0), ground + 1, b.z(0, 0), "hay_block");
		site.set(b.x(b.width() - 1, 0), ground + 1, b.z(b.width() - 1, 0), "hay_block");
	}

	// an 80s tv antenna (and a few lightning rods) poking out of the roofs
	private void antenna(Site site, Building b) {
		int lx = b.width() / 2;
		int lz = Math.max(1, (b.depth() - Style.raise(b.style())) / 2 - 1);
		int x = b.x(lx, lz);
		int z = b.z(lx, lz);
		if (!site.has(x, z)) {
			return;
		}
		for (int y = ground + 4; y <= ground + 22; y++) {
			if (site.level.getBlockState(new BlockPos(cx + x, y, cz + z)).isAir()) {
				site.set(x, y, z, "iron_bars");
				site.set(x, y + 1, z, "iron_bars");
				site.set(x, y + 2, z, "lightning_rod");
				return;
			}
		}
	}

	// the residents: one cook, a farmer, the rest just live here, the satpam at the gate and whoever keeps the warung
	private void moveIn(Site site) {
		Building kitchen = find(Kind.KITCHEN);
		Building field = find(Kind.FIELD);
		Building post = find(Kind.POST);
		int radius = Math.round(plan.radiusAt(0)) + 4;
		BlockPos center = new BlockPos(cx, ground + 1, cz);
		int houseIndex = 0;
		int index = 0;
		for (Building house : plan.buildings) {
			if (house.kind() != Kind.HOUSE) {
				continue;
			}
			int raise = Style.raise(house.style());
			int floor = ground + raise + 1;
			int front = house.depth() - raise - 4;
			BlockPos inside = world(house.x(house.width() / 2, front), floor, house.z(house.width() / 2, front));
			RandomSource random = RandomSource.create(plan.seed + houseIndex);
			for (int i = 0; i < house.residents(); i++) {
				int job = i > 0 ? Warga.RESIDENT : houseIndex == 0 && kitchen != null ? Warga.COOK : houseIndex == 1 && field != null ? Warga.FARMER : Warga.RESIDENT;
				BlockPos work = job == Warga.COOK ? world(kitchen.x(kitchen.width() / 2, 1), ground + 2, kitchen.z(kitchen.width() / 2, 1))
						: job == Warga.FARMER ? world(field.centerX(), ground, field.centerZ()) : null;
				int bx = i == 0 ? 1 : house.width() - 2;
				BlockPos bed = world(house.x(bx, 1), floor, house.z(bx, 1));
				// a couple: the first one a man, the second a woman, otherwise anyone
				int skin = house.residents() == 2 ? (i == 0 ? Warga.SKIN_MEN : Warga.SKIN_WOMEN) + random.nextInt(Warga.SKINS_PER_GROUP) : Warga.randomSkin(random);
				spawn(site, inside, job, skin, inside, bed, work, center, radius);
				index++;
			}
			// families with two grown-ups usually have a kid running around
			if (house.residents() == 2 && random.nextInt(3) != 0) {
				spawn(site, inside, Warga.KID, Warga.SKIN_BOYS + random.nextInt(8), inside, null, null, center, radius);
			}
			houseIndex++;
		}
		if (post != null) {
			BlockPos seat = world(post.x(1, 1), ground + 1, post.z(1, 1));
			spawn(site, seat.relative(post.turn(Direction.SOUTH), 2), Warga.GUARD, Warga.SKIN_SATPAM, seat, null, seat, center, radius);
		}
		Building warung = find(Kind.WARUNG);
		if (warung != null) {
			BlockPos behind = world(warung.x(warung.width() / 2, warung.depth() - 3), ground + 1, warung.z(warung.width() / 2, warung.depth() - 3));
			spawn(site, behind, Warga.VENDOR, Warga.SKIN_WOMEN + RandomSource.create(plan.seed).nextInt(Warga.SKINS_PER_GROUP), behind, null, behind, center, radius);
		}
		// a ball in the plaza for the kids
		BlockPos ball = world(2, ground + 1, 2);
		if (index > 0 && site.chunk.isInside(ball)) {
			Football football = BlossomEntities.FOOTBALL.create(site.level.getLevel(), EntitySpawnReason.STRUCTURE);
			football.snapTo(ball.getX() + 0.5, ball.getY(), ball.getZ() + 0.5, 0, 0);
			site.level.addFreshEntity(football);
		}
	}

	private Building find(Kind kind) {
		return plan.buildings.stream().filter(b -> b.kind() == kind).findFirst().orElse(null);
	}

	private BlockPos world(int x, int y, int z) {
		return new BlockPos(cx + x, y, cz + z);
	}

	private void spawn(Site site, BlockPos at, int job, int skin, BlockPos home, BlockPos bed, BlockPos work, BlockPos center, int radius) {
		if (!site.chunk.isInside(at)) {
			return;
		}
		if (job == Warga.GUARD) {
			Satpam satpam = BlossomEntities.SATPAM.create(site.level.getLevel(), EntitySpawnReason.STRUCTURE);
			satpam.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			satpam.finalizeSpawn(site.level, site.level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
			satpam.settle(job, skin, home, bed, work, center, radius);
			site.level.addFreshEntityWithPassengers(satpam);
			return;
		}
		Warga warga = BlossomEntities.WARGA.create(site.level.getLevel(), EntitySpawnReason.STRUCTURE);
		warga.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		warga.finalizeSpawn(site.level, site.level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
		warga.settle(job, skin, home, bed, work, center, radius);
		site.level.addFreshEntityWithPassengers(warga);
	}
}
