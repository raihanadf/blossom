package com.raihanadf.blossom.nusantara;

import com.raihanadf.blossom.furniture.LampBlock;
import com.raihanadf.blossom.kampung.Warga;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomEntities;
import com.raihanadf.blossom.sports.Football;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

// the heart of a town: the alun-alun with its two beringin trees, a street around it, and around that the great mosque
// on the west, the pasar and the bioskop on the east, the pendopo on the south, a row of ruko on the north and the
// school, puskesmas and post office on the corners
public final class TownCore {
	private static final int STREET = 4;
	private static final int LOT = 15;
	private static final List<String> RUKO = List.of("white_terracotta", "light_blue_terracotta", "yellow_terracotta", "pink_terracotta",
			"lime_terracotta", "orange_terracotta", "cyan_terracotta", "light_gray_terracotta");
	private static final List<String> AWNING = List.of("red_wool", "blue_wool", "green_wool", "orange_wool", "yellow_wool", "white_wool");

	private TownCore() {
	}

	public static int reach(int half) {
		return half + STREET + LOT + 1;
	}

	// a frame for one side of the square: u runs along the side, v from the street (0) outwards, front faces the square
	private record Frame(int cx, int cz, int start, Direction out) {
		int x(int u, int v) {
			return cx + out.getStepX() * (start + v) + -out.getStepZ() * u;
		}

		int z(int u, int v) {
			return cz + out.getStepZ() * (start + v) + out.getStepX() * u;
		}

		Direction front() {
			return out.getOpposite();
		}
	}

	public static void build(Site site, Settlement town, RandomSource random) {
		int cx = town.centerX;
		int cz = town.centerZ;
		int g = town.coreGround;
		int half = town.coreHalf;
		int reach = reach(half);
		if (!site.overlaps(cx - reach - 30, cz - reach - 30, cx + reach + 30, cz + reach + 30)) {
			return;
		}
		// level the whole block, blending back to the land over a broad bank
		for (int x = cx - reach - 30; x <= cx + reach + 30; x++) {
			for (int z = cz - reach - 30; z <= cz + reach + 30; z++) {
				if (!site.has(x, z)) {
					continue;
				}
				int out = Math.max(Math.abs(x - cx), Math.abs(z - cz)) - reach;
				if (out <= 0) {
					site.level(x, g, z, "grass_block");
				} else {
					site.blend(x, z, g, out, 30);
				}
			}
		}
		alunAlun(site, cx, cz, g, half);
		int start = half + STREET + 1;
		Frame west = new Frame(cx, cz, start, Direction.WEST);
		Frame east = new Frame(cx, cz, start, Direction.EAST);
		Frame north = new Frame(cx, cz, start, Direction.NORTH);
		Frame south = new Frame(cx, cz, start, Direction.SOUTH);
		int side = half + STREET;
		masjid(site, west, g, half);
		pasar(site, east, g, half >= 15 ? 11 : 8);
		pendopo(site, south, g);
		for (int u = -side + 1, i = 0; u + 4 <= side - 1; u += 5, i++) {
			ruko(site, north, g, u, i + town.node.cx() * 3 + town.node.cz());
		}
		// the corner lots
		sekolah(site, new Frame(cx, cz, start, Direction.NORTH), g, -side - LOT, -side - 1);
		puskesmas(site, new Frame(cx, cz, start, Direction.SOUTH), g, -side - LOT, -side - 2);
		kantorPos(site, new Frame(cx, cz, start, Direction.SOUTH), g, side + 2, side + LOT - 1);
		if (half >= 15) {
			bioskop(site, new Frame(cx, cz, start, Direction.NORTH), g, side + 2, side + LOT - 1);
		}
		people(site, town, cx, cz, g, half, random);
	}

	private static void alunAlun(Site site, int cx, int cz, int g, int half) {
		for (int x = -half - STREET; x <= half + STREET; x++) {
			for (int z = -half - STREET; z <= half + STREET; z++) {
				boolean square = Math.abs(x) <= half && Math.abs(z) <= half;
				boolean path = square && (x == 0 || z == 0 || Math.abs(x) == half || Math.abs(z) == half);
				String top = !square ? ((x + z) % 7 == 0 ? "andesite" : "polished_andesite") : path ? "dirt_path" : "grass_block";
				site.set(cx + x, g, cz + z, top);
				if (square && !path && (x * 7 + z * 3) % 11 == 0) {
					site.set(cx + x, g + 1, cz + z, "short_grass");
				}
			}
		}
		// the twin beringin trees in their little fences
		for (int side : new int[] {-1, 1}) {
			int tx = cx + side * (half / 2);
			beringin(site, tx, g, cz);
		}
		// lamp posts at the corners of the square
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				int x = cx + sx * half;
				int z = cz + sz * half;
				for (int y = g + 1; y <= g + 3; y++) {
					site.set(x, y, z, "dark_oak_fence");
				}
				site.set(x, g + 4, z, BlossomBlocks.LAMP.defaultBlockState().setValue(LampBlock.LIT, true).setValue(LampBlock.GLOW, 4)
						.setValue(LampBlock.COLOR, DyeColor.WHITE));
			}
		}
		// a flag in the middle
		for (int y = g + 1; y <= g + 10; y++) {
			site.set(cx, y, cz, y == g + 1 ? "stone_brick_wall" : "iron_bars");
		}
		for (int x = 1; x <= 3; x++) {
			site.set(cx + x, g + 9, cz, "red_wool");
			site.set(cx + x, g + 8, cz, "white_wool");
		}
	}

	private static void beringin(Site site, int x, int g, int z) {
		BlockState leaves = Site.block("dark_oak_leaves").setValue(BlockStateProperties.PERSISTENT, true);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
					site.set(x + dx, g + 1, z + dz, "white_concrete");
				}
			}
		}
		for (int y = g + 1; y <= g + 6; y++) {
			site.set(x, y, z, "jungle_log");
		}
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				for (int y = g + 5; y <= g + 9; y++) {
					double r = Math.sqrt(dx * dx + dz * dz) + Math.abs(y - g - 7) * 1.3;
					if (r < 5.3 && !(dx == 0 && dz == 0 && y < g + 7)) {
						site.set(x + dx, y, z + dz, leaves);
					}
				}
				// hanging roots
				if ((dx * 5 + dz * 3) % 7 == 0 && Math.sqrt(dx * dx + dz * dz) < 4.5) {
					site.set(x + dx, g + 4, z + dz, "hanging_roots");
				}
			}
		}
	}

	// walls of a plain building in a frame: corner posts, `wall` blocks, a door in the middle of the front, windows
	private static void box(Site site, Frame f, int g, int u0, int u1, int depth, int height, String wall, String trim, String floor) {
		int mid = (u0 + u1) / 2;
		for (int u = u0; u <= u1; u++) {
			for (int v = 0; v < depth; v++) {
				int x = f.x(u, v);
				int z = f.z(u, v);
				site.set(x, g, z, floor);
				boolean edge = u == u0 || u == u1 || v == 0 || v == depth - 1;
				boolean corner = (u == u0 || u == u1) && (v == 0 || v == depth - 1);
				for (int y = g + 1; y <= g + height; y++) {
					if (!edge) {
						site.set(x, y, z, "air");
						continue;
					}
					boolean door = v == 0 && Math.abs(u - mid) <= 1 && y <= g + 2;
					boolean window = !corner && y == g + 2 && (u + v) % 3 == 0;
					site.set(x, y, z, door ? Site.block("air") : window ? Site.block("glass_pane") : Site.block(corner || y == g + 1 ? trim : wall));
				}
				site.set(x, g + height + 1, z, edge ? trim : "smooth_stone");
			}
		}
	}

	// rings of stairs stepping inwards over a rectangle, steep or shallow, ending in a ridge
	private static void roof(Site site, Frame f, int g, int u0, int u1, int depth, String material, int levels) {
		BlockState full = Site.block(material.endsWith("brick") ? material + "s" : material.equals("dark_oak") ? "dark_oak_planks" : material);
		String stairs = material + "_stairs";
		for (int ring = 0; ring < levels; ring++) {
			int a0 = u0 - 1 + ring;
			int a1 = u1 + 1 - ring;
			int b0 = -1 + ring;
			int b1 = depth - ring;
			if (a0 > a1 || b0 > b1) {
				break;
			}
			for (int u = a0; u <= a1; u++) {
				for (int v = b0; v <= b1; v++) {
					boolean onU = u == a0 || u == a1;
					boolean onV = v == b0 || v == b1;
					int x = f.x(u, v);
					int z = f.z(u, v);
					if (!onU && !onV) {
						if (ring == levels - 1 || a1 - a0 <= 1 || b1 - b0 <= 1) {
							site.set(x, g + ring, z, full);
						}
						continue;
					}
					Direction up = onV ? (v == b0 ? f.out() : f.front()) : (u == a0 ? rotate(f, 1) : rotate(f, -1));
					site.set(x, g + ring, z, onU && onV ? full : Site.facing(stairs, up));
				}
			}
		}
	}

	// the world direction of the +u (side 1) or -u (side -1) way in a frame, pointing inwards for a roof
	private static Direction rotate(Frame f, int side) {
		Direction plusU = Direction.getNearest(-f.out().getStepZ(), 0, f.out().getStepX(), null);
		return side > 0 ? plusU : plusU.getOpposite();
	}

	private static void masjid(Site site, Frame f, int g, int half) {
		int w = half >= 15 ? 9 : 7;
		box(site, f, g, -w, w, 13, 5, "white_concrete", "green_terracotta", "smooth_quartz");
		// carpet inside, the porch (serambi) in front
		for (int u = -w + 1; u < w; u++) {
			for (int v = 1; v < 12; v++) {
				site.set(f.x(u, v), g + 1, f.z(u, v), (u + v) % 2 == 0 ? "green_carpet" : "lime_carpet");
			}
		}
		roof(site, f, g + 6, -w, w, 13, "prismarine_brick", 4);
		roof(site, f, g + 10, -w + 4, w - 4, 5, "prismarine_brick", 3);
		// the dome-ish crown and the minaret
		site.set(f.x(0, 6), g + 13, f.z(0, 6), "prismarine_bricks");
		site.set(f.x(0, 6), g + 14, f.z(0, 6), "lightning_rod");
		int mu = w + 3;
		for (int y = g + 1; y <= g + 18; y++) {
			for (int du = 0; du <= 1; du++) {
				for (int dv = 10; dv <= 11; dv++) {
					site.set(f.x(mu + du, dv), y, f.z(mu + du, dv), y % 5 == 0 ? "green_terracotta" : "white_concrete");
				}
			}
		}
		site.set(f.x(mu, 10), g + 19, f.z(mu, 10), "prismarine_bricks");
		site.set(f.x(mu, 10), g + 20, f.z(mu, 10), "lightning_rod");
		// bedug on the porch
		site.set(f.x(-w + 2, 0), g + 1, f.z(-w + 2, 0), Site.block("barrel").setValue(BlockStateProperties.FACING, rotate(f, 1)));
	}

	private static void pasar(Site site, Frame f, int g, int w) {
		// an open hall: posts, a long gable roof of clay tiles, stalls of goods underneath
		for (int u = -w; u <= w; u++) {
			for (int v = 0; v < 13; v++) {
				int x = f.x(u, v);
				int z = f.z(u, v);
				site.set(x, g, z, "packed_mud");
				boolean post = (u == -w || u == w || u % 4 == 0) && (v == 0 || v == 12 || v == 6);
				for (int y = g + 1; y <= g + 4; y++) {
					site.set(x, y, z, post ? "stripped_spruce_log" : "air");
				}
			}
		}
		roof(site, f, g + 5, -w, w, 13, "brick", 4);
		String[] goods = {"barrel", "melon", "pumpkin", "hay_block", "composter", "barrel", "decorated_pot", "cauldron"};
		RandomSource random = RandomSource.create(f.cx() * 31L + f.cz());
		for (int u = -w + 2; u <= w - 2; u += 2) {
			for (int v : new int[] {3, 9}) {
				site.set(f.x(u, v), g + 1, f.z(u, v), "spruce_planks");
				site.set(f.x(u, v), g + 2, f.z(u, v), goods[random.nextInt(goods.length)]);
			}
		}
		site.sign(f.x(0, -1), g + 4, f.z(0, -1), f.front(), true, "", "PASAR", "", "");
	}

	private static void pendopo(Site site, Frame f, int g) {
		// the open hall of the bupati: a raised marble floor under a tall joglo roof on wooden pillars
		for (int u = -7; u <= 7; u++) {
			for (int v = 1; v <= 12; v++) {
				int x = f.x(u, v);
				int z = f.z(u, v);
				site.set(x, g + 1, z, "smooth_quartz");
				boolean pillar = (u == -7 || u == 7 || u == -3 || u == 3) && (v == 1 || v == 12 || v == 5 || v == 8);
				for (int y = g + 2; y <= g + 5; y++) {
					site.set(x, y, z, pillar ? "dark_oak_log" : "air");
				}
			}
		}
		for (int u = -2; u <= 2; u++) {
			site.set(f.x(u, 0), g + 1, f.z(u, 0), Site.facing("quartz_stairs", f.out()));
		}
		roof(site, new Frame(f.cx(), f.cz(), f.start() + 1, f.out()), g + 6, -7, 7, 12, "dark_oak", 3);
		roof(site, new Frame(f.cx(), f.cz(), f.start() + 4, f.out()), g + 9, -3, 3, 6, "dark_oak", 4);
		site.set(f.x(0, 6), g + 2, f.z(0, 6), BlossomBlocks.TABLES.get("dark_oak").defaultBlockState());
		for (int u : new int[] {-1, 1}) {
			site.set(f.x(u, 6), g + 2, f.z(u, 6), BlossomBlocks.CHAIRS.get("dark_oak").defaultBlockState()
					.setValue(BlockStateProperties.HORIZONTAL_FACING, u < 0 ? rotate(f, 1) : rotate(f, -1)));
		}
		site.set(f.x(0, 5), g + 5, f.z(0, 5), Site.block("lantern").setValue(BlockStateProperties.HANGING, true));
	}

	private static void ruko(Site site, Frame f, int g, int u0, int index) {
		// a two storey shophouse: an open shop front under an awning, living quarters with windows above
		String wall = RUKO.get(Math.floorMod(index, RUKO.size()));
		String awning = AWNING.get(Math.floorMod(index * 7, AWNING.size()));
		int u1 = u0 + 4;
		for (int u = u0; u <= u1; u++) {
			for (int v = 0; v < 10; v++) {
				int x = f.x(u, v);
				int z = f.z(u, v);
				site.set(x, g, z, v < 2 ? "smooth_stone" : "spruce_planks");
				boolean side = u == u0 || u == u1 || v == 9;
				for (int y = g + 1; y <= g + 8; y++) {
					boolean upper = y >= g + 5;
					boolean front = v == 2;
					String state = "air";
					if (side) {
						state = wall;
					} else if (front && upper) {
						state = y == g + 6 && u != u0 + 2 ? "glass_pane" : wall;
					} else if (front && y == g + 4) {
						state = wall;
					} else if (y == g + 4 && v > 2) {
						state = "spruce_planks";
					}
					site.set(x, y, z, state);
				}
				site.set(x, g + 9, z, v <= 9 && v >= 2 ? "smooth_stone_slab" : "air");
			}
			site.set(f.x(u, 1), g + 4, f.z(u, 1), awning);
			site.set(f.x(u, 1), g + 5, f.z(u, 1), "air");
		}
		// goods on the shop counter and a sign over the front
		site.set(f.x(u0 + 1, 3), g + 1, f.z(u0 + 1, 3), "barrel");
		site.set(f.x(u1 - 1, 3), g + 1, f.z(u1 - 1, 3), Math.floorMod(index, 3) == 0 ? "bookshelf" : "decorated_pot");
		site.set(f.x(u0 + 2, 4), g + 1, f.z(u0 + 2, 4), "spruce_planks");
		String[] names = {"TOKO MAJU", "TOKO SUMBER", "WARUNG MAKAN", "TOKO EMAS", "FOTO KOPI", "TOKO BESI", "APOTIK", "TOKO KAIN", "SALON", "JAHIT"};
		site.sign(f.x(u0 + 2, 1), g + 3, f.z(u0 + 2, 1), f.front(), true, "", names[Math.floorMod(index, names.length)], "", "");
	}

	private static void sekolah(Site site, Frame f, int g, int u0, int u1) {
		int w = Math.abs(u1 - u0);
		int a = Math.min(u0, u1);
		box(site, f, g, a, a + w, 8, 4, "white_concrete", "red_terracotta", "polished_andesite");
		roof(site, f, g + 5, a, a + w, 8, "brick", 3);
		site.sign(f.x(a + w / 2, -1), g + 4, f.z(a + w / 2, -1), f.front(), true, "SD NEGERI", "INPRES", "", "");
		// the flagpole in the schoolyard
		int fx = f.x(a + 2, -2);
		int fz = f.z(a + 2, -2);
		for (int y = g + 1; y <= g + 8; y++) {
			site.set(fx, y, fz, y == g + 1 ? "stone_brick_wall" : "iron_bars");
		}
		site.set(fx, g + 7, fz + 1, "red_wool");
		site.set(fx, g + 6, fz + 1, "white_wool");
	}

	private static void puskesmas(Site site, Frame f, int g, int u0, int u1) {
		int a = Math.min(u0, u1);
		int w = Math.abs(u1 - u0);
		box(site, f, g, a, a + w, 9, 4, "white_concrete", "lime_terracotta", "smooth_quartz");
		site.sign(f.x(a + w / 2, -1), g + 4, f.z(a + w / 2, -1), f.front(), true, "PUSKESMAS", "", "", "");
		for (int u = a + 1; u < a + w; u += 3) {
			site.set(f.x(u, 2), g + 1, f.z(u, 2), "white_bed");
		}
	}

	private static void kantorPos(Site site, Frame f, int g, int u0, int u1) {
		int a = Math.min(u0, u1);
		int w = Math.abs(u1 - u0);
		box(site, f, g, a, a + w, 9, 4, "orange_terracotta", "white_concrete", "polished_andesite");
		roof(site, f, g + 5, a, a + w, 9, "brick", 2);
		site.sign(f.x(a + w / 2, -1), g + 4, f.z(a + w / 2, -1), f.front(), true, "KANTOR POS", "", "", "");
		site.set(f.x(a + 1, -1), g + 1, f.z(a + 1, -1), "orange_concrete");
	}

	private static void bioskop(Site site, Frame f, int g, int u0, int u1) {
		int a = Math.min(u0, u1);
		int w = Math.abs(u1 - u0);
		box(site, f, g, a, a + w, 13, 8, "red_terracotta", "black_concrete", "red_carpet");
		// the marquee: lights across the front and the name in big letters
		for (int u = a; u <= a + w; u++) {
			site.set(f.x(u, -1), g + 5, f.z(u, -1), u % 2 == 0 ? "glowstone" : "yellow_concrete");
		}
		site.sign(f.x(a + w / 2, -2), g + 5, f.z(a + w / 2, -2), f.front(), true, "BIOSKOP", "MISBAR", "film baru!", "");
		for (int u = a + 2; u < a + w - 1; u += 3) {
			for (int v = 6; v <= 10; v += 2) {
				site.set(f.x(u, v), g + 1, f.z(u, v), BlossomBlocks.CHAIRS.get("dark_oak").defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, f.out()));
			}
		}
	}

	private static void people(Site site, Settlement town, int cx, int cz, int g, int half, RandomSource random) {
		BlockPos center = new BlockPos(cx, g + 1, cz);
		int radius = reach(half) + 20;
		// sellers at the pasar, people strolling round the square, kids with a ball, the satpam at the pendopo
		for (int i = 0; i < 3; i++) {
			int x = cx + half + STREET + 4 + i * 3;
			int z = cz - 4 + i * 4;
			site.person(x, g + 1, z, Warga.VENDOR, Warga.SKIN_WOMEN + random.nextInt(Warga.SKINS_PER_GROUP), new BlockPos(x, g + 1, z), center, radius);
		}
		for (int i = 0; i < 4 + half / 4; i++) {
			int x = cx + random.nextInt(half * 2) - half;
			int z = cz + random.nextInt(half * 2) - half;
			site.person(x, g + 1, z, Warga.RESIDENT, Warga.randomSkin(random), new BlockPos(x, g + 1, z), center, radius);
		}
		for (int i = 0; i < 3; i++) {
			int x = cx - half / 2 + i * 2;
			site.person(x, g + 1, cz + 4, Warga.KID, Warga.SKIN_BOYS + random.nextInt(8), new BlockPos(x, g + 1, cz + 4), center, radius);
		}
		if (site.has(cx + 2, cz + 5)) {
			Football ball = BlossomEntities.FOOTBALL.create(site.level.getLevel(), EntitySpawnReason.STRUCTURE);
			ball.snapTo(cx + 2.5, g + 1, cz + 5.5, 0, 0);
			site.level.addFreshEntity(ball);
		}
		int px = cx;
		int pz = cz + half + STREET + 2;
		site.person(px, g + 1, pz, Warga.GUARD, Warga.SKIN_SATPAM, new BlockPos(px, g + 1, pz), center, radius);
	}
}
