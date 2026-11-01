package com.raihanadf.blossom.kampung;

import static com.raihanadf.blossom.kampung.KampungSite.block;
import static com.raihanadf.blossom.kampung.KampungSite.facing;
import static com.raihanadf.blossom.nusantara.Site.isNatural;

import com.raihanadf.blossom.kampung.KampungPlan.Building;
import com.raihanadf.blossom.kampung.KampungPlan.Kind;
import com.raihanadf.blossom.kampung.KampungPlan.Lane;
import com.raihanadf.blossom.kampung.KampungPlan.Region;
import com.raihanadf.blossom.kampung.KampungPlan.Style;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomEntities;
import com.raihanadf.blossom.registry.BlossomWorldgen;
import com.raihanadf.blossom.sports.Football;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

// builds a kampung from its plan, one chunk at a time: levels the yard, fences it in, lays the streets,
// raises the houses, plants the yards and moves the people in
public class KampungPiece extends StructurePiece {
	private static final int EDGE_BLEND = 30;

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
		KampungSite site = new KampungSite(level, chunk, cx, cz);
		levelGround(site);
		streets(site);
		fenceAndGate(site);
		KampungHouses houses = new KampungHouses(site, plan, ground);
		for (Building building : plan.buildings) {
			houses.build(building);
			boolean wired = plan.region == Region.JAWA || plan.region == Region.SUNDA || plan.region == Region.BUGIS;
			if (building.kind() == Kind.HOUSE && wired && building.palette() % 3 != 0) {
				houses.antenna(building);
			}
		}
		powerLines(site);
		beringin(site);
		yards(site);
		moveIn(site);
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

	private void levelGround(KampungSite site) {
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
					BlockState above = site.get(x, y, z);
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

	private boolean railway(KampungSite site, int x, int y, int z) {
		BlockState top = site.get(x, y, z);
		if (!isNatural(top)) {
			return true;
		}
		if (!top.is(Blocks.GRAVEL)) {
			return false;
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (site.get(x + dx, y + 1, z + dz).is(BlossomBlocks.TRACK)) {
					return true;
				}
			}
		}
		return false;
	}

	private int naturalGround(KampungSite site, int x, int z, int surface) {
		int y = surface;
		while (y > site.level.getMinY()) {
			BlockState state = site.get(x, y, z);
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

	// the streets: the main street packed and wide, the gang a narrow footpath; round the beringin they run as a ring
	private void streets(KampungSite site) {
		for (Lane lane : plan.lanes) {
			for (int along = lane.from() - (lane.main() ? 4 : 0); along <= lane.to() + (lane.main() ? 4 : 0); along++) {
				for (int side = -lane.half(); side <= lane.half(); side++) {
					// a gang is two blocks wide
					if (!lane.main() && side == lane.half()) {
						continue;
					}
					int x = lane.alongX() ? along : lane.across() + side;
					int z = lane.alongX() ? lane.across() + side : along;
					// only the main street runs on out through the gate
					if (Math.max(Math.abs(x), Math.abs(z)) <= 2 || plan.distanceOutside(x, z) > (lane.main() ? 4 : -1)) {
						continue;
					}
					site.set(x, ground, z, "dirt_path");
				}
			}
		}
		for (int x = -KampungPlan.PLAZA; x <= KampungPlan.PLAZA; x++) {
			for (int z = -KampungPlan.PLAZA; z <= KampungPlan.PLAZA; z++) {
				if (Math.max(Math.abs(x), Math.abs(z)) >= 3) {
					site.set(x, ground, z, "dirt_path");
				}
			}
		}
	}

	private void fenceAndGate(KampungSite site) {
		boolean bali = plan.region == Region.BALI;
		BlockState fence = block(plan.bambooFence ? "bamboo_fence" : "spruce_fence");
		int reach = plan.extent;
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				if (!site.has(x, z) || !isFenceAt(x, z) || plan.lane(x, z) != null && plan.lane(x, z).main()) {
					continue;
				}
				if (bali) {
					// the penyengker: a brick wall with a paras stone coping
					site.set(x, ground + 1, z, "bricks");
					site.set(x, ground + 2, z, "tuff_brick_slab");
				} else {
					site.set(x, ground + 1, z, fence);
					if (Math.floorMod(x * 31 + z * 17, 9) == 0) {
						site.set(x, ground + 2, z, "lantern");
					}
				}
			}
		}
		Direction out = plan.gateFacing();
		int ox = out.getStepX();
		int oz = out.getStepZ();
		if (bali) {
			// candi bentar: a temple tower split in two, the street goes through the cut
			for (int side : new int[] {1, -1}) {
				for (int step = 0; step < 3; step++) {
					int s = side * (3 + step);
					int height = 9 - step * 2;
					for (int o = -1; o <= 0; o++) {
						int x = plan.gateX - oz * s + ox * o;
						int z = plan.gateZ + ox * s + oz * o;
						for (int y = 1; y <= height; y++) {
							site.set(x, ground + y, z, y % 3 == 0 ? "tuff_bricks" : "bricks");
						}
						site.set(x, ground + height + 1, z, "chiseled_tuff");
					}
				}
			}
			return;
		}
		// gapura: two white pillars, a beam with a little tiled roof, "selamat datang" on the way in, "sampai jumpa" on the way out
		BlockState white = BlossomBlocks.PLASTER.get("white").defaultBlockState();
		BlockState red = BlossomBlocks.PLASTER.get("pink").defaultBlockState();
		String color = plan.region == Region.TORAJA || plan.region == Region.MINANG ? "black" : plan.region == Region.BUGIS ? "brown" : "red";
		Block stairs = BlossomBlocks.GENTENG_STAIRS.get(color);
		for (int s = -3; s <= 3; s++) {
			int x = plan.gateX - oz * s;
			int z = plan.gateZ + ox * s;
			if (Math.abs(s) == 3) {
				site.set(x, ground + 1, z, "stone_bricks");
				for (int y = 2; y <= 5; y++) {
					site.set(x, ground + y, z, y == 3 ? red : white);
				}
			}
			site.set(x, ground + 6, z, Math.abs(s) == 3 ? white : red);
			site.set(x + ox, ground + 7, z + oz, facing(stairs, out.getOpposite()));
			site.set(x - ox, ground + 7, z - oz, facing(stairs, out));
			site.set(x, ground + 7, z, BlossomBlocks.GENTENG.get(color));
			site.set(x, ground + 8, z, BlossomBlocks.GENTENG_SLABS.get(color));
			if (Math.abs(s) == 2) {
				site.set(x, ground + 5, z, block("lantern").setValue(BlockStateProperties.HANGING, true));
			}
		}
		site.sign(plan.gateX + ox, ground + 6, plan.gateZ + oz, out, "SELAMAT", "DATANG");
		site.sign(plan.gateX - ox, ground + 6, plan.gateZ - oz, out.getOpposite(), "SAMPAI", "JUMPA");
	}

	private boolean isFenceAt(int x, int z) {
		return plan.isFence(x, z) && !plan.isGateOpening(x, z) && !plan.isGatePillar(x, z);
	}

	// tiang listrik along the main street, the wires strung from pole to pole and a street lamp on each
	private void powerLines(KampungSite site) {
		Lane main = plan.lanes.stream().filter(Lane::main).findFirst().orElseThrow();
		int line = main.across() + main.half() + 1;
		int last = Integer.MIN_VALUE;
		for (int along = main.from() + 2; along <= main.to() - 2; along++) {
			int x = main.alongX() ? along : line;
			int z = main.alongX() ? line : along;
			// worked out from the plan alone (every chunk has to agree where the poles stand), never on a path to a door
			boolean pole = along - last >= 11 && Math.max(Math.abs(x), Math.abs(z)) > KampungPlan.PLAZA + 1 && plan.building(x, z) == null
					&& plan.lane(x, z) == null && plan.distanceOutside(x, z) < -1 && !doorPath(x, z);
			if (pole) {
				if (last != Integer.MIN_VALUE) {
					for (int a = last + 1; a < along; a++) {
						int wx = main.alongX() ? a : line;
						int wz = main.alongX() ? line : a;
						BlockState wire = block("tripwire").setValue(main.alongX() ? BlockStateProperties.EAST : BlockStateProperties.NORTH, true)
								.setValue(main.alongX() ? BlockStateProperties.WEST : BlockStateProperties.SOUTH, true);
						site.grow(wx, ground + 7, wz, wire);
					}
				}
				site.set(x, ground + 1, z, "stone_bricks");
				for (int y = ground + 2; y <= ground + 7; y++) {
					site.set(x, y, z, "andesite_wall");
				}
				site.set(x, ground + 8, z, "lightning_rod");
				int lx = main.alongX() ? x : x - 1;
				int lz = main.alongX() ? z - 1 : z;
				site.set(lx, ground + 6, lz, "iron_bars");
				site.set(lx, ground + 5, lz, block("lantern").setValue(BlockStateProperties.HANGING, true));
				last = along;
			}
		}
	}

	// the footpath from a building's door (and the yard beside it) out to its street
	private boolean doorPath(int x, int z) {
		for (Building b : plan.buildings) {
			for (int lx = b.width() / 2 - 1; lx <= b.width() / 2 + 1; lx++) {
				for (int k = 0; k < 6; k++) {
					if (b.x(lx, b.depth() + k) == x && b.z(lx, b.depth() + k) == z) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// the beringin in the middle of the village: a huge banyan in a low walled planter, with seats all round it
	private void beringin(KampungSite site) {
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				boolean ring = Math.max(Math.abs(x), Math.abs(z)) == 2;
				site.set(x, ground + 1, z, ring ? block("stone_brick_wall") : block("air"));
				if (!ring) {
					site.set(x, ground, z, "grass_block");
				}
			}
		}
		BlockState wood = block("jungle_wood");
		for (int y = ground + 1; y <= ground + 8; y++) {
			site.set(0, y, 0, wood);
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			// the buttress roots and the first big branches
			for (int y = ground + 1; y <= ground + 3; y++) {
				site.set(side.getStepX(), y, side.getStepZ(), wood);
			}
			site.set(side.getStepX() * 2, ground + 7, side.getStepZ() * 2, wood);
			site.set(side.getStepX(), ground + 6, side.getStepZ(), wood);
		}
		BlockState leaves = block("jungle_leaves").setValue(BlockStateProperties.PERSISTENT, true);
		for (int x = -7; x <= 7; x++) {
			for (int z = -7; z <= 7; z++) {
				for (int y = ground + 7; y <= ground + 12; y++) {
					float dy = (y - (ground + 9.5f)) / 2.8f;
					float r = (x * x + z * z) / 49f + dy * dy;
					if (r <= 1 && (r < 0.8f || Math.floorMod(x * 13 + z * 7 + y, 4) != 0)) {
						site.grow(x, y, z, leaves);
					}
				}
				// aerial roots hanging from the edge of the crown (wood, so the yard sweep that clears wild vines leaves them)
				int d2 = x * x + z * z;
				if (d2 >= 16 && d2 <= 36 && Math.floorMod(x * 31 + z * 17, 5) == 0) {
					site.grow(x, ground + 6, z, block("jungle_fence"));
					site.grow(x, ground + 5, z, block("jungle_fence"));
				}
			}
		}
		// seats looking out from under the tree
		for (Direction side : Direction.Plane.HORIZONTAL) {
			for (int s : new int[] {-1, 1}) {
				int x = side.getStepX() * 3 + (side.getStepX() == 0 ? s * 2 : 0);
				int z = side.getStepZ() * 3 + (side.getStepZ() == 0 ? s * 2 : 0);
				site.set(x, ground + 1, z, facing(BlossomBlocks.CHAIRS.get("jungle"), side));
			}
		}
	}

	// the yards between the houses: coconut palms, mango trees, flowering shrubs and washing lines
	private void yards(KampungSite site) {
		int reach = plan.extent;
		for (int gx = -reach; gx <= reach; gx += 6) {
			for (int gz = -reach; gz <= reach; gz += 6) {
				int hash = Mth.murmurHash3Mixer((int) plan.seed * 31 + gx * 73428767 ^ gz * 912931);
				int x = gx + Math.floorMod(hash, 5);
				int z = gz + Math.floorMod(hash >> 4, 5);
				int kind = Math.floorMod(hash >> 8, 10);
				if (!site.near(x, z, 5)) {
					continue;
				}
				if (!clear(x, z, kind == 8 ? 2 : 1)) {
					continue;
				}
				if (kind < 4) {
					palm(site, x, z, hash);
				} else if (kind < 7) {
					mango(site, x, z);
				} else if (kind == 7) {
					site.set(x, ground + 1, z, block("flowering_azalea_leaves").setValue(BlockStateProperties.PERSISTENT, true));
					if (hash % 2 == 0) {
						site.set(x, ground + 2, z, block("azalea_leaves").setValue(BlockStateProperties.PERSISTENT, true));
					}
				} else if (kind == 8) {
					washingLine(site, x, z, hash);
				}
			}
		}
	}

	// nothing built, no street and no front yard within `r` (and well away from walls, so crowns clear the roofs)
	private boolean clear(int x, int z, int r) {
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int px = x + dx;
				int pz = z + dz;
				if (plan.distanceOutside(px, pz) > -2 || plan.lane(px, pz) != null || Math.max(Math.abs(px), Math.abs(pz)) <= KampungPlan.PLAZA + 2) {
					return false;
				}
			}
		}
		for (Building b : plan.buildings) {
			int gap = b.kind() == Kind.HOUSE ? 4 : 3;
			if (x >= b.x0() - gap - r && x <= b.x1() + gap + r && z >= b.z0() - gap - r && z <= b.z1() + gap + r) {
				return false;
			}
		}
		return true;
	}

	private void palm(KampungSite site, int x, int z, int hash) {
		int height = 7 + Math.floorMod(hash >> 12, 3);
		Direction lean = Direction.from2DDataValue(Math.floorMod(hash >> 16, 4));
		int tx = x;
		int tz = z;
		BlockState wood = block("jungle_wood");
		for (int y = ground + 1; y <= ground + height; y++) {
			// the trunk curves over a little as it climbs
			if (y == ground + height - 2) {
				tx += lean.getStepX();
				tz += lean.getStepZ();
			}
			site.set(tx, y, tz, wood);
		}
		int top = ground + height + 1;
		BlockState leaves = block("jungle_leaves").setValue(BlockStateProperties.PERSISTENT, true);
		site.grow(tx, top, tz, leaves);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				// fronds out in eight directions, drooping at the tips
				int length = dx != 0 && dz != 0 ? 2 : 3;
				for (int i = 1; i <= length; i++) {
					site.grow(tx + dx * i, i == length ? top - 1 : top, tz + dz * i, leaves);
				}
			}
		}
		// a bunch of coconuts under the crown
		for (Direction side : new Direction[] {lean.getClockWise(), lean.getCounterClockWise()}) {
			site.grow(tx + side.getStepX(), top - 2, tz + side.getStepZ(), facing("cocoa", side.getOpposite()).setValue(BlockStateProperties.AGE_2, 2));
		}
	}

	private void mango(KampungSite site, int x, int z) {
		BlockState wood = block("jungle_wood");
		for (int y = ground + 1; y <= ground + 3; y++) {
			site.set(x, y, z, wood);
		}
		BlockState leaves = block("oak_leaves").setValue(BlockStateProperties.PERSISTENT, true);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = 0; dy <= 3; dy++) {
					float r = (dx * dx + dz * dz) / 6.5f + (dy - 1.3f) * (dy - 1.3f) / 3.5f;
					if (r <= 1) {
						site.grow(x + dx, ground + 3 + dy, z + dz, leaves);
					}
				}
			}
		}
	}

	// a line between two bamboo poles with the washing hung out on it
	private void washingLine(KampungSite site, int x, int z, int hash) {
		boolean alongX = hash % 2 == 0;
		DyeColor[] clothes = {DyeColor.WHITE, DyeColor.LIGHT_BLUE, DyeColor.PINK, DyeColor.YELLOW, DyeColor.RED, DyeColor.LIME};
		for (int i = -2; i <= 2; i++) {
			int px = alongX ? x + i : x;
			int pz = alongX ? z : z + i;
			if (Math.abs(i) == 2) {
				for (int y = ground + 1; y <= ground + 3; y++) {
					site.set(px, y, pz, "bamboo_fence");
				}
				continue;
			}
			site.set(px, ground + 3, pz, block("tripwire").setValue(alongX ? BlockStateProperties.EAST : BlockStateProperties.NORTH, true)
					.setValue(alongX ? BlockStateProperties.WEST : BlockStateProperties.SOUTH, true));
			DyeColor color = clothes[Math.floorMod(hash + i * 7, clothes.length)];
			site.set(px, ground + 2, pz, color.getSerializedName() + "_stained_glass_pane");
		}
	}

	// the residents: one cook, a farmer, the rest just live here, the satpam at the gate and whoever keeps the warung
	private void moveIn(KampungSite site) {
		Building kitchen = find(Kind.KITCHEN);
		Building field = find(Kind.FIELD);
		Building post = find(Kind.POST);
		int radius = Math.round(plan.reach()) + 4;
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
		// a ball by the beringin for the kids
		BlockPos ball = world(4, ground + 1, 4);
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

	private void spawn(KampungSite site, BlockPos at, int job, int skin, BlockPos home, BlockPos bed, BlockPos work, BlockPos center, int radius) {
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
