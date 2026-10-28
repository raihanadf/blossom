package com.raihanadf.blossom.kampung;

import com.raihanadf.blossom.furniture.LampBlock;
import com.raihanadf.blossom.kampung.KampungPlan.Building;
import com.raihanadf.blossom.kampung.KampungPlan.Kind;
import com.raihanadf.blossom.kampung.KampungPlan.Style;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomEntities;
import com.raihanadf.blossom.registry.BlossomWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
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
		super(BlossomWorldgen.KAMPUNG_PIECE, 0, new BoundingBox(cx - KampungPlan.EXTENT - 6, ground - 14, cz - KampungPlan.EXTENT - 6,
				cx + KampungPlan.EXTENT + 6, ground + 26, cz + KampungPlan.EXTENT + 6));
		this.plan = new KampungPlan(seed);
		this.cx = cx;
		this.cz = cz;
		this.ground = ground;
	}

	public KampungPiece(CompoundTag tag) {
		super(BlossomWorldgen.KAMPUNG_PIECE, tag);
		this.plan = new KampungPlan(tag.getLongOr("Seed", 0));
		this.cx = tag.getIntOr("CX", 0);
		this.cz = tag.getIntOr("CZ", 0);
		this.ground = tag.getIntOr("Ground", 64);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putLong("Seed", plan.seed);
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
	private void levelGround(Site site) {
		int reach = KampungPlan.EXTENT + 6;
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				if (!site.has(x, z)) {
					continue;
				}
				float outside = plan.distanceOutside(x, z);
				if (outside > 6) {
					continue;
				}
				// the village is built after the trees, so walk down past trunks, leaves and plants to the real ground
				int canopy = Math.max(site.level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx + x, cz + z), ground + 30);
				int natural = canopy;
				while (natural > site.level.getMinY() && isCover(site.level.getBlockState(new BlockPos(cx + x, natural, cz + z)))) {
					natural--;
				}
				int top = outside <= 2 ? ground : Math.round(Mth.lerp((outside - 2) / 4f, ground, natural));
				for (int y = top - 1; y > top - 12; y--) {
					BlockState below = site.level.getBlockState(new BlockPos(cx + x, y, cz + z));
					if (!below.isAir() && below.getFluidState().isEmpty() && !below.canBeReplaced()) {
						break;
					}
					site.set(x, y, z, "dirt");
				}
				site.set(x, top - 1, z, "dirt");
				site.set(x, top, z, "grass_block");
				for (int y = top + 1; y <= Math.max(Math.max(natural, ground) + 22, canopy); y++) {
					site.set(x, y, z, "air");
				}
			}
		}
	}

	private static boolean isCover(BlockState state) {
		return state.canBeReplaced() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.BAMBOO)
				|| state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK);
	}

	private void fenceAndGate(Site site) {
		BlockState fence = block(plan.bambooFence ? "bamboo_fence" : "spruce_fence");
		int reach = KampungPlan.EXTENT;
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
		int d = b.depth();
		int palette = b.palette();
		int raise = b.style() == Style.TONGKONAN ? 3 : b.style() == Style.PANGGUNG ? 2 : 0;
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

		// a staircase down from the raised porch, running sideways along the front edge
		if (raise > 0) {
			for (int i = 0; i < raise; i++) {
				int x = b.x(mid - i, d - 1);
				int z = b.z(mid - i, d - 1);
				site.set(x, floor - i, z, facing(wood + "_stairs", b.turn(Direction.EAST)));
				for (int y = floor - i + 1; y <= floor + 2; y++) {
					site.set(x, y, z, "air");
				}
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

	// the residents: one cook, a farmer, the rest just live here, and the satpam at the gate
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
			int raise = house.style() == Style.TONGKONAN ? 3 : house.style() == Style.PANGGUNG ? 2 : 0;
			int floor = ground + raise + 1;
			for (int i = 0; i < house.residents(); i++) {
				int job = i > 0 ? Warga.RESIDENT : houseIndex == 0 && kitchen != null ? Warga.COOK : houseIndex == 1 && field != null ? Warga.FARMER : Warga.RESIDENT;
				BlockPos work = job == Warga.COOK ? world(kitchen.x(kitchen.width() / 2, 1), ground + 2, kitchen.z(kitchen.width() / 2, 1))
						: job == Warga.FARMER ? world(field.centerX(), ground, field.centerZ()) : null;
				int bx = i == 0 ? 1 : house.width() - 2;
				BlockPos bed = world(house.x(bx, 1), floor, house.z(bx, 1));
				BlockPos inside = world(house.x(house.width() / 2, house.depth() - 4), floor, house.z(house.width() / 2, house.depth() - 4));
				spawn(site, inside, job, RandomSource.create(plan.seed + index).nextInt(6), inside, bed, work, center, radius);
				index++;
			}
			houseIndex++;
		}
		if (post != null) {
			BlockPos seat = world(post.x(1, 1), ground + 1, post.z(1, 1));
			spawn(site, seat.relative(post.turn(Direction.SOUTH), 2), Warga.GUARD, 6, seat, null, seat, center, radius);
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
		Warga warga = BlossomEntities.WARGA.create(site.level.getLevel(), EntitySpawnReason.STRUCTURE);
		warga.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		warga.finalizeSpawn(site.level, site.level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
		warga.settle(job, skin, home, bed, work, center, radius);
		site.level.addFreshEntityWithPassengers(warga);
	}
}
