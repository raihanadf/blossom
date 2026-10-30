package com.raihanadf.blossom.nusantara;

import com.raihanadf.blossom.kampung.KampungPiece;
import com.raihanadf.blossom.nusantara.NusantaraNetwork.Edge;
import com.raihanadf.blossom.nusantara.Settlement.Cluster;
import java.util.List;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

// runs once for every chunk of a nusantara world, after the trees: builds this chunk's share of the railway, the
// stations, the towns, kampungs and fields. in any other world it does nothing
public record NusantaraFeature() implements Feature {
	public static final MapCodec<NusantaraFeature> CODEC = MapCodec.unit(new NusantaraFeature());

	@Override
	public MapCodec<NusantaraFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource chunkRandom, BlockPos origin) {
		if (!(generator.getBiomeSource() instanceof TropicalBiomes tropical)) {
			return false;
		}
		NusantaraNetwork network = tropical.network(level);
		ChunkPos chunk = ChunkPos.containing(origin);
		BoundingBox box = new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ());
		Site site = new Site(level, box);
		Site around = new Site(level, box.inflatedBy(16, 0, 16));
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		RandomSource random = RandomSource.create(network.hash(chunk.x(), chunk.z(), 77));

		List<Settlement> settlements = network.settlementsNear(x, z);
		for (Settlement settlement : settlements) {
			StationBuilder.level(site, settlement);
		}
		for (Edge edge : network.edgesNear(x, z)) {
			TrackBuilder.build(site, edge);
			TrackBuilder.sweep(site, around, edge);
		}
		for (Settlement settlement : settlements) {
			StationBuilder.build(site, network, settlement, random);
			StationBuilder.sweepRoads(site, around, settlement);
			StationBuilder.sweep(site, around, settlement);
			if (settlement.isTown()) {
				TownCore.build(site, settlement, random);
			}
			for (Cluster cluster : settlement.clusters) {
				KampungPiece piece = new KampungPiece(cluster.plan(), cluster.x(), cluster.ground(), cluster.z()).keepOff((px, pz) -> settlement.builtAt(px, pz, cluster));
				sweepYard(site, around, cluster);
				if (piece.getBoundingBox().intersects(box)) {
					piece.postProcess(level, null, generator, random, box, chunk, new BlockPos(cluster.x(), cluster.ground(), cluster.z()));
				}
			}
		}
		return true;
	}

	// no wild trees in a kampung yard: they are taken out before the kampung is built in this chunk, and whatever grows
	// over from the chunk next door later loses its leaves and vines (placed leaves are persistent and stay)
	private static void sweepYard(Site site, Site around, Cluster cluster) {
		int reach = cluster.plan().extent;
		if (!around.overlaps(cluster.x() - reach, cluster.z() - reach, cluster.x() + reach, cluster.z() + reach)) {
			return;
		}
		for (int x = Math.max(cluster.x() - reach, around.box.minX()); x <= Math.min(cluster.x() + reach, around.box.maxX()); x++) {
			for (int z = Math.max(cluster.z() - reach, around.box.minZ()); z <= Math.min(cluster.z() + reach, around.box.maxZ()); z++) {
				if (!around.has(x, z) || cluster.plan().distanceOutside(x - cluster.x(), z - cluster.z()) > 0) {
					continue;
				}
				// in this chunk the kampung isn't built yet, so whole trees go; next door only what grows over the fence
				boolean own = site.has(x, z);
				for (int y = cluster.ground() + 1; y <= cluster.ground() + 32; y++) {
					BlockState state = around.get(x, y, z);
					boolean wild = state.is(BlockTags.LEAVES) && !state.getValue(BlockStateProperties.PERSISTENT) || state.is(Blocks.VINE);
					boolean tree = state.is(BlockTags.LOGS) || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
							|| state.is(Blocks.BAMBOO);
					if (wild || own && tree) {
						around.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}
}
