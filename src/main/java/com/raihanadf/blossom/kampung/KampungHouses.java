package com.raihanadf.blossom.kampung;

import static com.raihanadf.blossom.kampung.KampungSite.block;
import static com.raihanadf.blossom.kampung.KampungSite.facing;

import com.raihanadf.blossom.furniture.LampBlock;
import com.raihanadf.blossom.kampung.KampungPlan.Building;
import com.raihanadf.blossom.kampung.KampungPlan.Kind;
import com.raihanadf.blossom.kampung.KampungPlan.Region;
import com.raihanadf.blossom.kampung.KampungPlan.Style;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

// every building of a kampung, each house built the way they build them in the village's region
final class KampungHouses {
	private static final String[] FLOWERS = {"potted_red_tulip", "potted_poppy", "potted_fern", "potted_bamboo", "potted_orange_tulip", "potted_azalea_bush"};
	private static final String[] GARDEN = {"poppy", "dandelion", "oxeye_daisy", "red_tulip", "orange_tulip", "allium", "azure_bluet"};

	private final KampungSite site;
	private final KampungPlan plan;
	private final int ground;

	KampungHouses(KampungSite site, KampungPlan plan, int ground) {
		this.site = site;
		this.plan = plan;
		this.ground = ground;
	}

	void build(Building b) {
		switch (b.kind()) {
			case HOUSE -> house(b);
			case KITCHEN -> kitchen(b);
			case FIELD -> field(b);
			case POST -> post(b);
			case WARUNG -> warung(b);
			case MUSHOLLA -> {
				switch (plan.region) {
					case BALI -> pura(b);
					case TORAJA -> gereja(b);
					default -> musholla(b);
				}
			}
			case RONDA -> ronda(b);
			case SUMUR -> sumur(b);
			case KANDANG -> kandang(b);
			case JEMURAN -> jemuran(b);
		}
		frontYard(b);
	}

	// the colour of the roof tiles all over the village
	String roofColor() {
		return switch (plan.region) {
			case JAWA, BALI -> "red";
			case SUNDA -> plan.seed % 2 == 0 ? "red" : "black";
			case BUGIS -> "brown";
			case TORAJA, MINANG -> "black";
		};
	}

	private Block roofStairs(Building b) {
		// now and then a javanese house has had its roof redone in darker tiles
		String color = plan.region == Region.JAWA && b.palette() % 4 == 0 ? "brown" : roofColor();
		return BlossomBlocks.GENTENG_STAIRS.get(color);
	}

	private BlockState roofFull(Building b) {
		String color = plan.region == Region.JAWA && b.palette() % 4 == 0 ? "brown" : roofColor();
		return BlossomBlocks.GENTENG.get(color).defaultBlockState();
	}

	private BlockState roofSlab(Building b) {
		return BlossomBlocks.GENTENG_SLABS.get(roofColor()).defaultBlockState();
	}

	private static BlockState plaster(int palette) {
		return BlossomBlocks.PLASTER.get(BlossomBlocks.PLASTER_COLORS.get(Math.floorMod(palette, BlossomBlocks.PLASTER_COLORS.size()))).defaultBlockState();
	}

	// what a house is made of
	private record Look(BlockState wall, BlockState plinth, BlockState post, BlockState floor, BlockState porchPost, String wood, Block shutter) {
	}

	private Look look(Style style, int palette) {
		Block shutter = BlossomBlocks.SHUTTERS.get(BlossomBlocks.SHUTTER_COLORS.get(Math.floorMod(palette / 5, BlossomBlocks.SHUTTER_COLORS.size())));
		String[] woods = {"jungle", "spruce", "dark_oak"};
		String wood = woods[Math.floorMod(palette / 3, woods.length)];
		return switch (style) {
			// painted plaster on a grey plinth, white trim, a tiled floor and the terrace columns
			case LIMASAN, KAMPUNG -> {
				BlockState wall = plaster(palette);
				BlockState trim = plaster(palette % BlossomBlocks.PLASTER_COLORS.size() == 0 ? 1 : 0);
				yield new Look(wall, block("polished_andesite"), trim, block("quartz_bricks"), trim, wood, shutter);
			}
			// pak lurah's joglo is all teak
			case JOGLO -> new Look(block("jungle_planks"), block("polished_andesite"), block("stripped_jungle_log"), block("quartz_bricks"),
					block("jungle_log"), "jungle", BlossomBlocks.SHUTTERS.get("brown"));
			// woven bamboo walls (bilik) on a timber frame
			case SUNDA -> new Look(block("bamboo_mosaic"), null, block("stripped_spruce_log"), block("bamboo_planks"), block("stripped_spruce_log"),
					"bamboo", shutter);
			case PANGGUNG -> {
				String[] walls = {"spruce", "dark_oak", "jungle"};
				String w = walls[Math.floorMod(palette, walls.length)];
				yield new Look(block(w + "_planks"), null, block(w.equals("jungle") ? "stripped_jungle_log" : w + "_log"), block("spruce_planks"),
						block(w.equals("jungle") ? "stripped_jungle_log" : w + "_log"), w, shutter);
			}
			case TONGKONAN -> new Look(block("dark_oak_planks"), null, block("dark_oak_log"), block("dark_oak_planks"), block("dark_oak_log"),
					"dark_oak", null);
			case GADANG -> new Look(block("dark_oak_planks"), null, block("dark_oak_log"), block("dark_oak_planks"), block("dark_oak_log"),
					"dark_oak", BlossomBlocks.SHUTTERS.get("brown"));
			// red brick with carved paras stone at the corners and along the bottom
			case BALI -> new Look(block("bricks"), block("polished_tuff"), block("tuff_bricks"), block("quartz_bricks"), block("tuff_bricks"),
					"jungle", null);
		};
	}

	private static int wallHeight(Style style) {
		return style == Style.LIMASAN || style == Style.KAMPUNG || style == Style.JOGLO || style == Style.BALI ? 4 : 3;
	}

	private void house(Building b) {
		Style style = b.style();
		int w = b.width();
		int raise = Style.raise(style);
		// the stairs of a raised house take the last `raise` rows of the footprint, the porch sits right behind them
		int d = b.depth() - raise;
		int palette = b.palette();
		int floor = ground + raise;
		int height = wallHeight(style);
		Look look = look(style, palette);
		int front = d - 3;
		int mid = w / 2;

		// stilts for the raised houses, on stone footings
		if (raise > 0) {
			for (int lx = 0; lx < w; lx += Math.max(1, (w - 1) / 2)) {
				for (int lz : new int[] {0, front / 2, front, d - 1}) {
					site.set(b.x(lx, lz), ground + 1, b.z(lx, lz), "stone_bricks");
					for (int y = ground + 2; y < floor; y++) {
						site.set(b.x(lx, lz), y, b.z(lx, lz), look.post);
					}
				}
			}
		}

		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				site.set(x, floor, z, look.floor);
				boolean edgeX = lx == 0 || lx == w - 1;
				boolean walled = lz <= front && (edgeX || lz == 0 || lz == front);
				boolean porchPost = lz == d - 1 && edgeX;
				for (int y = floor + 1; y <= floor + height; y++) {
					if (walled && edgeX && (lz == 0 || lz == front)) {
						site.set(x, y, z, look.post);
					} else if (walled) {
						site.set(x, y, z, look.plinth != null && y == floor + 1 ? look.plinth : wall(style, look, y - floor, lz == front));
					} else if (porchPost) {
						site.set(x, y, z, look.porchPost);
					} else {
						site.set(x, y, z, "air");
					}
				}
				// the beam all around under the roof
				if (lx == 0 || lx == w - 1 || lz == 0 || lz == d - 1 || lz == front) {
					site.set(x, floor + height + 1, z, lz > front && !edgeX && lz != d - 1 ? block("air") : look.post);
				}
			}
		}

		// windows on the sides and the back, shutters folded open beside them, a roster above to let the air through
		int top = height == 4 ? 2 : 1;
		for (int lz = 1; lz < front; lz++) {
			if ((lz + palette) % 2 == 0) {
				window(b, 0, lz, floor + 2, top, Direction.WEST, look.shutter, height);
				window(b, w - 1, lz, floor + 2, top, Direction.EAST, look.shutter, height);
			}
		}
		for (int lx = 1; lx < w - 1; lx++) {
			if (lx != mid && (lx + palette) % 2 == 1) {
				window(b, lx, 0, floor + 2, top, Direction.NORTH, look.shutter, height);
			}
		}
		// the front: two windows looking out over the porch, minang houses have a whole row of them
		for (int lx = 1; lx < w - 1; lx++) {
			if (lx == 1 || lx == w - 2 || style == Style.GADANG && lx != mid && lx % 2 == 1) {
				window(b, lx, front, floor + 2, top, Direction.SOUTH, null, height);
			}
		}

		// an open door, with a roster over it in the taller houses
		Direction out = b.turn(Direction.SOUTH);
		BlockState door = facing(look.wood + "_door", out).setValue(BlockStateProperties.OPEN, true);
		site.set(b.x(mid, front), floor + 1, b.z(mid, front), door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		site.set(b.x(mid, front), floor + 2, b.z(mid, front), door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		if (height == 4) {
			site.set(b.x(mid, front), floor + 3, b.z(mid, front), style == Style.JOGLO ? look.wall : BlossomBlocks.ROSTER.defaultBlockState());
		}

		// a straight staircase from the middle of the porch down to the yard, with a railing round the porch
		for (int i = 0; i < raise; i++) {
			int x = b.x(mid, d + i);
			int z = b.z(mid, d + i);
			int step = floor - i;
			site.set(x, step, z, facing(look.wood + "_stairs", b.turn(Direction.NORTH)));
			for (int y = ground + 1; y < step; y++) {
				site.set(x, y, z, look.floor);
			}
			for (int y = step + 1; y <= floor + height; y++) {
				site.set(x, y, z, "air");
			}
		}
		if (raise > 0) {
			BlockState rail = block(look.wood.equals("bamboo") ? "bamboo_fence" : look.wood + "_fence");
			for (int lx = 0; lx < w; lx++) {
				for (int lz = front + 1; lz < d; lz++) {
					boolean edge = lx == 0 || lx == w - 1 || lz == d - 1;
					if (edge && lx != mid && !(lz == d - 1 && (lx == 0 || lx == w - 1))) {
						site.set(b.x(lx, lz), floor + 1, b.z(lx, lz), rail);
					}
				}
			}
		}

		// beds against the back wall, a table with a lamp, a chest, a barrel and a tikar on the floor
		DyeColor bedColor = DyeColor.values()[palette % DyeColor.values().length];
		for (int i = 0; i < b.residents(); i++) {
			int lx = i == 0 ? 1 : w - 2;
			Direction headward = b.turn(Direction.NORTH);
			BlockState bed = facing(bedColor.getSerializedName() + "_bed", headward);
			site.set(b.x(lx, 2), floor + 1, b.z(lx, 2), bed.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
			site.set(b.x(lx, 1), floor + 1, b.z(lx, 1), bed.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		}
		Block table = BlossomBlocks.TABLES.get(look.wood);
		Block chair = BlossomBlocks.CHAIRS.get(look.wood);
		site.set(b.x(mid, 1), floor + 1, b.z(mid, 1), table);
		site.set(b.x(mid, 1), floor + 2, b.z(mid, 1), BlossomBlocks.LAMP.defaultBlockState().setValue(LampBlock.LIT, true).setValue(LampBlock.GLOW, 4)
				.setValue(LampBlock.COLOR, DyeColor.values()[(palette / 7) % DyeColor.values().length]));
		if (front > 3) {
			site.set(b.x(mid, 2), floor + 1, b.z(mid, 2), facing(chair, b.turn(Direction.SOUTH)));
		}
		site.set(b.x(w - 2, front - 1), floor + 1, b.z(w - 2, front - 1), "barrel");
		site.set(b.x(1, front - 1), floor + 1, b.z(1, front - 1), facing("chest", b.turn(Direction.EAST)));
		if (front > 4) {
			site.set(b.x(mid, front - 1), floor + 1, b.z(mid, front - 1), palette % 2 == 0 ? "yellow_carpet" : "brown_carpet");
		}

		// the porch: chairs and a little table looking out, potted plants, a lantern under the beam
		if (raise == 0) {
			site.set(b.x(1, d - 2), floor + 1, b.z(1, d - 2), facing(chair, b.turn(Direction.SOUTH)));
			if (w > 6) {
				site.set(b.x(2, d - 2), floor + 1, b.z(2, d - 2), table);
				site.set(b.x(3, d - 2), floor + 1, b.z(3, d - 2), facing(chair, b.turn(Direction.SOUTH)));
			}
			site.set(b.x(w - 2, d - 1), floor + 1, b.z(w - 2, d - 1), FLOWERS[(palette / 3) % FLOWERS.length]);
		} else {
			site.set(b.x(w - 2, d - 2), floor + 1, b.z(w - 2, d - 2), facing(chair, b.turn(Direction.SOUTH)));
			site.set(b.x(1, d - 2), floor + 1, b.z(1, d - 2), FLOWERS[(palette / 3) % FLOWERS.length]);
		}
		site.set(b.x(mid, d - 1), floor + height, b.z(mid, d - 1), block("lantern").setValue(BlockStateProperties.HANGING, true));

		int roofY = floor + height + 1;
		Block stairs = roofStairs(b);
		BlockState full = roofFull(b);
		switch (style) {
			case LIMASAN -> hipRoof(b, roofY, 99, stairs, full, d);
			case JOGLO -> hipRoof(b, roofY, 2, stairs, full, d);
			case BALI -> hipRoof(b, roofY, 99, stairs, full, d);
			case KAMPUNG, SUNDA, PANGGUNG -> gableRoof(b, roofY, stairs, full, true, false, d, look);
			case TONGKONAN -> gableRoof(b, roofY, stairs, full, false, true, d, look);
			case GADANG -> gableRoof(b, roofY, stairs, full, true, true, d, look);
		}
	}

	// one block of wall `row` blocks above the floor. a minang house has its carved, painted band right under the roof
	private static BlockState wall(Style style, Look look, int row, boolean front) {
		if (style == Style.GADANG && row == 3) {
			return block(front ? "red_terracotta" : "yellow_terracotta");
		}
		if (style == Style.TONGKONAN && row == 2 && front) {
			return block("red_terracotta");
		}
		return look.wall;
	}

	// a window `rows` high in the wall at (lx, lz), `outward` the local way out of that wall
	private void window(Building b, int lx, int lz, int y, int rows, Direction outward, Block shutter, int height) {
		for (int i = 0; i < rows; i++) {
			site.set(b.x(lx, lz), y + i, b.z(lx, lz), "glass_pane");
		}
		if (height == 4 && rows == 2) {
			site.set(b.x(lx, lz), y + 2, b.z(lx, lz), BlossomBlocks.ROSTER);
		}
		if (shutter == null) {
			return;
		}
		Direction world = b.turn(outward);
		int ox = outward.getStepX();
		int oz = outward.getStepZ();
		// the shutters stand just outside the wall either side of the window, folded back flat against it
		for (int side : new int[] {-1, 1}) {
			int sx = lx + ox + (oz != 0 ? side : 0);
			int sz = lz + oz + (ox != 0 ? side : 0);
			for (int i = 0; i < rows; i++) {
				if (site.has(b.x(sx, sz), b.z(sx, sz)) && site.get(b.x(sx, sz), y + i, b.z(sx, sz)).isAir()) {
					site.set(b.x(sx, sz), y + i, b.z(sx, sz), facing(shutter, world));
				}
			}
		}
	}

	// rings of stairs stepping inwards over `depth` rows. joglo roofs turn steep after `steepFrom` rings
	private void hipRoof(Building b, int y0, int steepFrom, Block stairs, BlockState full, int depth) {
		for (int k = 0; ; k++) {
			int x0 = -1 + k;
			int x1 = b.width() - k;
			int z0 = -1 + k;
			int z1 = depth - k;
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

	// two sloping sides meeting at a ridge, the ridge along the front (`alongWidth`) or front to back. `saddle`
	// sweeps both ends of the ridge up into horns, the tongkonan and the gonjong of a rumah gadang
	private void gableRoof(Building b, int y0, Block stairs, BlockState full, boolean alongWidth, boolean saddle, int depth, Look look) {
		int span = alongWidth ? depth : b.width();
		int length = alongWidth ? b.width() : depth;
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
					int lx = alongWidth ? along : across;
					int lz = alongWidth ? across : along;
					Direction up = alongWidth ? (across == low ? Direction.SOUTH : Direction.NORTH) : (across == low ? Direction.EAST : Direction.WEST);
					site.set(b.x(lx, lz), y, b.z(lx, lz), high == low ? full : facing(stairs, b.turn(up)));
					// the swept-up horns are solid underneath, not a lattice of floating steps
					for (int fill = y0 + k; fill < y; fill++) {
						site.set(b.x(lx, lz), fill, b.z(lx, lz), full);
					}
				}
				// close the triangular ends: painted in toraja colours, in stacked boards on a bugis house
				boolean end = along == 0 || along == length - 1 || (!alongWidth && along == depth - 3);
				if (k > 0 && end) {
					for (int across = low + 1; across < high; across++) {
						int lx = alongWidth ? along : across;
						int lz = alongWidth ? across : along;
						site.set(b.x(lx, lz), y0 + k, b.z(lx, lz), gable(b.style(), look, across, k));
					}
				}
			}
		}
	}

	private static BlockState gable(Style style, Look look, int across, int k) {
		return switch (style) {
			case TONGKONAN -> block(new String[] {"red_terracotta", "black_terracotta", "yellow_terracotta", "white_terracotta"}[Math.floorMod(across + k, 4)]);
			case GADANG -> block(k % 2 == 0 ? "red_terracotta" : "dark_oak_planks");
			case PANGGUNG -> k % 2 == 0 ? look.post : look.wall;
			default -> look.wall;
		};
	}

	// the strip between a building and its street: a garden, then a hedge, a fence or a wall with the gap for the path
	private void frontYard(Building b) {
		int w = b.width();
		int mid = w / 2;
		boolean house = b.kind() == Kind.HOUSE;
		for (int lx = -1; lx <= w; lx++) {
			int rows = 0;
			while (rows < 6 && plan.lane(b.x(lx, b.depth() + rows), b.z(lx, b.depth() + rows)) == null) {
				rows++;
			}
			if (rows == 6) {
				continue;
			}
			for (int k = 0; k < rows; k++) {
				int x = b.x(lx, b.depth() + k);
				int z = b.z(lx, b.depth() + k);
				if (lx == mid || b.kind() == Kind.FIELD && lx >= 0 && lx < w) {
					site.set(x, ground, z, "dirt_path");
				} else if (house && k == rows - 1) {
					hedge(b, x, z, Math.abs(lx - mid) == 1);
				} else if (house && k == 0 && lx >= 0 && lx < w && (lx * 7 + b.palette()) % 3 == 0) {
					site.set(x, ground + 1, z, GARDEN[Math.floorMod(lx + b.palette(), GARDEN.length)]);
				}
			}
			// a motor parked by the house
			if (house && lx == w - 1 && rows >= 2 && b.palette() % 5 < 2) {
				BlockPos at = new BlockPos(site.cx + b.x(lx, b.depth()), ground + 1, site.cz + b.z(lx, b.depth()));
				if (site.chunk.isInside(at)) {
					var motor = BlossomEntities.MOTOR.create(site.level.getLevel(), EntitySpawnReason.STRUCTURE);
					motor.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, b.turn(Direction.EAST).toYRot(), 0);
					motor.setColor(DyeColor.values()[b.palette() % DyeColor.values().length]);
					site.level.addFreshEntity(motor);
				}
			}
		}
	}

	// the front fence of one house. `gatepost` is right beside the path
	private void hedge(Building b, int x, int z, boolean gatepost) {
		switch (plan.region) {
			case JAWA -> {
				if (b.palette() % 2 == 0) {
					// a low painted wall with iron railings on top
					site.set(x, ground + 1, z, plaster(b.palette() + 1));
					site.set(x, ground + 2, z, gatepost ? plaster(b.palette() + 1) : block("iron_bars"));
				} else {
					site.set(x, ground + 1, z, block("azalea_leaves").setValue(BlockStateProperties.PERSISTENT, true));
				}
			}
			case BALI -> {
				// the brick compound wall, with the angkul-angkul gate pillars either side of the path
				site.set(x, ground + 1, z, "bricks");
				site.set(x, ground + 2, z, gatepost ? block("bricks") : block("tuff_brick_slab"));
				if (gatepost) {
					site.set(x, ground + 3, z, "tuff_bricks");
					site.set(x, ground + 4, z, roofSlab(b));
				}
			}
			default -> site.set(x, ground + 1, z, plan.bambooFence ? "bamboo_fence" : "spruce_fence");
		}
	}

	// dapur: an open pavilion with a cooking pot on the fire, a cutting board and a table to eat at
	private void kitchen(Building b) {
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
		site.set(b.x(mid, 1), ground + 2, b.z(mid, 1), BlossomBlocks.COOKING_POT);
		site.set(b.x(1, 1), ground + 1, b.z(1, 1), "barrel");
		site.set(b.x(1, 1), ground + 2, b.z(1, 1), BlossomBlocks.CUTTING_BOARD);
		site.set(b.x(w - 2, 1), ground + 1, b.z(w - 2, 1), "barrel");
		site.set(b.x(mid, d - 2), ground + 1, b.z(mid, d - 2), BlossomBlocks.TABLES.get("bamboo"));
		site.set(b.x(mid - 1, d - 2), ground + 1, b.z(mid - 1, d - 2), facing(BlossomBlocks.CHAIRS.get("bamboo"), b.turn(Direction.WEST)));
		site.set(b.x(mid + 1, d - 2), ground + 1, b.z(mid + 1, d - 2), facing(BlossomBlocks.CHAIRS.get("bamboo"), b.turn(Direction.EAST)));
		hipRoof(b, ground + 4, 99, roofStairs(b), roofFull(b), d);
	}

	// sawah: rows of rice and vegetables around a water channel, with a scarecrow in the corner
	private void field(Building b) {
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
					site.set(x, ground + 1, z, plan.bambooFence ? "bamboo_fence" : "spruce_fence");
					site.set(x, ground + 2, z, "hay_block");
					site.set(x, ground + 3, z, facing("carved_pumpkin", b.turn(Direction.SOUTH)));
					continue;
				}
				site.set(x, ground, z, block("farmland").setValue(BlockStateProperties.MOISTURE, 7));
				site.set(x, ground + 1, z, rows[lx].defaultBlockState().setValue(CropBlock.AGE, random.nextInt(CropBlock.MAX_AGE + 1)));
			}
		}
	}

	// pos satpam: a little white post by the gate with a tiled roof, a chair and a lantern
	private void post(Building b) {
		for (int lx = 0; lx < 3; lx++) {
			for (int lz = 0; lz < 3; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				site.set(x, ground, z, "quartz_bricks");
				boolean corner = lx != 1 && lz != 1;
				boolean half = lz == 0 || lx != 1;
				for (int y = ground + 1; y <= ground + 3; y++) {
					site.set(x, y, z, corner ? plaster(0) : half && y == ground + 1 ? plaster(0) : block("air"));
				}
				site.set(x, ground + 4, z, roofSlab(b));
			}
		}
		site.set(b.x(1, 1), ground + 1, b.z(1, 1), facing(BlossomBlocks.CHAIRS.get("bamboo"), b.turn(Direction.NORTH)));
		site.set(b.x(1, 0), ground + 3, b.z(1, 0), block("lantern").setValue(BlockStateProperties.HANGING, true));
	}

	// warung: a little shop with a striped awning, a counter full of jars and sacks, and a bench out front
	private void warung(Building b) {
		int w = b.width();
		int d = b.depth();
		BlockState wall = plaster(b.palette());
		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				site.set(x, ground, z, "quartz_bricks");
				boolean walled = lz <= d - 2 && (lx == 0 || lx == w - 1 || lz == 0);
				for (int y = ground + 1; y <= ground + 3; y++) {
					site.set(x, y, z, walled ? wall : block("air"));
				}
				site.set(x, ground + 4, z, lz <= d - 2 ? roofSlab(b) : block("air"));
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
			site.set(b.x(lx, 1), ground + 1, b.z(lx, 1), "barrel");
			site.set(b.x(lx, 1), ground + 2, b.z(lx, 1), lx % 2 == 0 ? "melon" : "decorated_pot");
		}
		site.set(b.x(0, d - 1), ground + 1, b.z(0, d - 1), facing(BlossomBlocks.CHAIRS.get("bamboo"), b.turn(Direction.SOUTH)));
		site.set(b.x(w - 1, d - 1), ground + 1, b.z(w - 1, d - 1), "potted_bamboo");
		site.set(b.x(w / 2, d - 2), ground + 3, b.z(w / 2, d - 2), block("lantern").setValue(BlockStateProperties.HANGING, true));
	}

	// the musholla: white walls, a green tiered roof with a little finial, the bedug drum and a kentongan on the porch
	private void musholla(Building b) {
		int w = b.width();
		int d = b.depth();
		int mid = w / 2;
		BlockState wall = plaster(b.palette() % 2 == 0 ? 0 : 2);
		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				site.set(x, ground, z, "quartz_bricks");
				site.set(x, ground + 1, z, lz < d - 2 ? block(lx % 2 == lz % 2 ? "green_carpet" : "lime_carpet") : block("air"));
				boolean walled = lz < d - 2 && (lx == 0 || lx == w - 1 || lz == 0 || lz == d - 3);
				boolean porchPost = lz == d - 1 && (lx == 0 || lx == w - 1);
				for (int y = ground + 1; y <= ground + 4; y++) {
					if (walled) {
						boolean window = lx % 2 == 1 && (lz == 0 || lx == 0 || lx == w - 1);
						site.set(x, y, z, window && y == ground + 4 ? BlossomBlocks.ROSTER.defaultBlockState() : window && y > ground + 1 ? block("glass_pane") : wall);
					} else if (porchPost) {
						site.set(x, y, z, wall);
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
		// green tiered roof, then the mustaka on top
		hipRoof(b, ground + 5, 2, block("prismarine_brick_stairs").getBlock(), block("prismarine_bricks"), d);
		for (int y = ground + 5; y <= ground + 16 && site.has(b.x(mid, d / 2), b.z(mid, d / 2)); y++) {
			if (site.get(b.x(mid, d / 2), y, b.z(mid, d / 2)).isAir()) {
				site.set(b.x(mid, d / 2), y, b.z(mid, d / 2), "lightning_rod");
				break;
			}
		}
	}

	// a balinese pura: a brick wall round a little courtyard, a split gate, and a meru with three tiers of black roofs
	private void pura(Building b) {
		int w = b.width();
		int d = b.depth();
		int mid = w / 2;
		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				site.set(x, ground, z, "polished_tuff");
				for (int y = ground + 1; y <= ground + 9; y++) {
					site.set(x, y, z, "air");
				}
				boolean wall = lx == 0 || lx == w - 1 || lz == 0 || lz == d - 1;
				if (wall && !(lz == d - 1 && Math.abs(lx - mid) <= 1)) {
					site.set(x, ground + 1, z, "bricks");
					site.set(x, ground + 2, z, "bricks");
					site.set(x, ground + 3, z, "tuff_brick_slab");
				}
			}
		}
		// candi bentar: the two halves of a tower either side of the way in
		for (int lx : new int[] {mid - 2, mid + 2}) {
			for (int y = ground + 1; y <= ground + 6; y++) {
				site.set(b.x(lx, d - 1), y, b.z(lx, d - 1), y % 3 == 0 ? "tuff_bricks" : "bricks");
			}
			site.set(b.x(lx, d - 1), ground + 7, b.z(lx, d - 1), "chiseled_tuff");
		}
		// the meru at the back
		BlockState black = BlossomBlocks.GENTENG.get("black").defaultBlockState();
		Block blackStairs = BlossomBlocks.GENTENG_STAIRS.get("black");
		for (int lx = mid - 1; lx <= mid + 1; lx++) {
			for (int lz = 1; lz <= 3; lz++) {
				site.set(b.x(lx, lz), ground + 1, b.z(lx, lz), "tuff_bricks");
			}
		}
		for (int y = ground + 2; y <= ground + 9; y++) {
			site.set(b.x(mid, 2), y, b.z(mid, 2), "dark_oak_log");
		}
		int[] tiers = {2, 1};
		for (int t = 0; t < tiers.length; t++) {
			int r = tiers[t];
			int y = ground + 4 + t * 3;
			for (int lx = mid - r; lx <= mid + r; lx++) {
				for (int lz = 2 - r; lz <= 2 + r; lz++) {
					boolean onX = lx == mid - r || lx == mid + r;
					boolean onZ = lz == 2 - r || lz == 2 + r;
					if (onX && onZ) {
						site.set(b.x(lx, lz), y, b.z(lx, lz), black);
					} else if (onX || onZ) {
						Direction up = lz == 2 - r ? Direction.SOUTH : lz == 2 + r ? Direction.NORTH : lx == mid - r ? Direction.EAST : Direction.WEST;
						site.set(b.x(lx, lz), y, b.z(lx, lz), facing(blackStairs, b.turn(up)));
					} else {
						site.set(b.x(lx, lz), y, b.z(lx, lz), black);
					}
				}
			}
		}
		site.set(b.x(mid, 2), ground + 10, b.z(mid, 2), black);
		// a padmasana shrine in the corner and offerings by the gate
		site.set(b.x(1, 1), ground + 1, b.z(1, 1), "chiseled_tuff");
		site.set(b.x(1, 1), ground + 2, b.z(1, 1), "chiseled_tuff_bricks");
		site.set(b.x(1, 1), ground + 3, b.z(1, 1), "yellow_carpet");
		site.set(b.x(w - 2, d - 2), ground + 1, b.z(w - 2, d - 2), "potted_red_tulip");
		site.set(b.x(1, d - 2), ground + 1, b.z(1, d - 2), "potted_orange_tulip");
	}

	// the church of a toraja village: white walls, a tiled gable roof, a cross over the door and a bell
	private void gereja(Building b) {
		int w = b.width();
		int d = b.depth();
		int mid = w / 2;
		BlockState wall = plaster(0);
		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				site.set(x, ground, z, "quartz_bricks");
				boolean walled = lx == 0 || lx == w - 1 || lz == 0 || lz == d - 1;
				for (int y = ground + 1; y <= ground + 4; y++) {
					boolean window = lz % 2 == 1 && (lx == 0 || lx == w - 1) && y > ground + 1 && y < ground + 4;
					site.set(x, y, z, walled ? (window ? block("glass_pane") : wall) : block("air"));
				}
			}
		}
		for (int lz = 1; lz < d - 2; lz += 2) {
			site.set(b.x(mid - 1, lz), ground + 1, b.z(mid - 1, lz), facing("spruce_stairs", b.turn(Direction.NORTH)));
			site.set(b.x(mid + 1, lz), ground + 1, b.z(mid + 1, lz), facing("spruce_stairs", b.turn(Direction.NORTH)));
		}
		BlockState door = facing("spruce_door", b.turn(Direction.SOUTH)).setValue(BlockStateProperties.OPEN, true);
		site.set(b.x(mid, d - 1), ground + 1, b.z(mid, d - 1), door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		site.set(b.x(mid, d - 1), ground + 2, b.z(mid, d - 1), door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		Look look = new Look(wall, null, wall, wall, wall, "spruce", null);
		gableRoof(b, ground + 5, BlossomBlocks.GENTENG_STAIRS.get("red"), BlossomBlocks.GENTENG.get("red").defaultBlockState(), false, false, d, look);
		// the cross on the front gable, the bell under it
		int top = ground + 5 + w / 2;
		site.set(b.x(mid, d), top, b.z(mid, d), "end_rod");
		site.set(b.x(mid, d), top + 1, b.z(mid, d), "end_rod");
		site.set(b.x(mid, d), top + 2, b.z(mid, d), "end_rod");
		site.set(b.x(mid - 1, d), top + 1, b.z(mid - 1, d), block("end_rod").setValue(BlockStateProperties.FACING, b.turn(Direction.EAST)));
		site.set(b.x(mid + 1, d), top + 1, b.z(mid + 1, d), block("end_rod").setValue(BlockStateProperties.FACING, b.turn(Direction.WEST)));
		site.set(b.x(mid, d - 1), ground + 4, b.z(mid, d - 1), "bell");
	}

	// pos ronda: a raised bamboo platform under a little roof where the men keep the night watch, with the kentongan
	private void ronda(Building b) {
		for (int lx = 0; lx < 3; lx++) {
			for (int lz = 0; lz < 3; lz++) {
				int x = b.x(lx, lz);
				int z = b.z(lx, lz);
				boolean corner = lx != 1 && lz != 1;
				site.set(x, ground + 1, z, corner ? "stripped_bamboo_block" : "bamboo_slab");
				for (int y = ground + 2; y <= ground + 3; y++) {
					site.set(x, y, z, corner ? "bamboo_fence" : "air");
				}
				site.set(x, ground + 4, z, roofSlab(b));
			}
		}
		// the kentongan: a hollow log on a chain to drum out the alarm
		site.set(b.x(1, 2), ground + 3, b.z(1, 2), "iron_chain");
		site.set(b.x(1, 2), ground + 2, b.z(1, 2), "bamboo_block");
		site.set(b.x(1, 0), ground + 3, b.z(1, 0), block("lantern").setValue(BlockStateProperties.HANGING, true));
	}

	// sumur: a stone well with a little tiled roof and a bucket on its rope
	private void sumur(Building b) {
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
					site.set(x, y, z, corner && lx == lz ? "spruce_fence" : "air");
				}
				site.set(x, ground + 4, z, roofSlab(b));
			}
		}
		site.set(b.x(1, 1), ground + 3, b.z(1, 1), "iron_chain");
		site.set(b.x(1, 1), ground + 2, b.z(1, 1), "cauldron");
	}

	// kandang ayam: a little coop with a fenced run, nest boxes of hay
	private void kandang(Building b) {
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
					site.set(x, ground + 3, z, roofSlab(b));
				}
			}
		}
		site.set(b.x(w / 2, d - 1), ground + 1, b.z(w / 2, d - 1), facing("spruce_fence_gate", b.turn(Direction.SOUTH)));
		site.set(b.x(1, d - 2), ground + 1, b.z(1, d - 2), block("water_cauldron").setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		for (int i = 1; i < 3; i++) {
			BlockPos at = new BlockPos(site.cx + b.x(1 + i, d - 2), ground + 1, site.cz + b.z(1 + i, d - 2));
			if (site.chunk.isInside(at)) {
				var chicken = EntityTypes.CHICKEN.create(site.level.getLevel(), EntitySpawnReason.STRUCTURE);
				chicken.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, i * 90, 0);
				chicken.setPersistenceRequired();
				site.level.addFreshEntity(chicken);
			}
		}
	}

	// jemur gabah: harvested rice spread out on mats to dry in the sun, with a couple of sacks
	private void jemuran(Building b) {
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

	// a tv antenna on a bamboo pole poking out of the roof
	void antenna(Building b) {
		int lx = b.width() / 2;
		int lz = Math.max(1, (b.depth() - Style.raise(b.style())) / 2 - 1);
		int x = b.x(lx, lz);
		int z = b.z(lx, lz);
		if (!site.has(x, z)) {
			return;
		}
		for (int y = ground + 4; y <= ground + 22; y++) {
			if (site.get(x, y, z).isAir()) {
				site.set(x, y, z, "bamboo_fence");
				site.set(x, y + 1, z, "bamboo_fence");
				site.set(x, y + 2, z, "iron_bars");
				site.set(x, y + 3, z, "lightning_rod");
				return;
			}
		}
	}
}
