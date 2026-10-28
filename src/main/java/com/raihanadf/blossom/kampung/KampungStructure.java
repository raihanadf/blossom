package com.raihanadf.blossom.kampung;

import com.mojang.serialization.MapCodec;
import com.raihanadf.blossom.registry.BlossomWorldgen;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

// a fenced kampung, only on fairly flat dry land
public class KampungStructure extends Structure {
	public static final MapCodec<KampungStructure> CODEC = simpleCodec(KampungStructure::new);

	public KampungStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		if (!context.couldValidBiomeExistOnTopOfChunkCenter()) {
			return Optional.empty();
		}
		ChunkPos chunk = context.chunkPos();
		int x = chunk.getMiddleBlockX();
		int z = chunk.getMiddleBlockZ();
		ChunkGenerator generator = context.chunkGenerator();
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		int sum = 0;
		int samples = 0;
		for (int dx = -24; dx <= 24; dx += 12) {
			for (int dz = -24; dz <= 24; dz += 12) {
				int surface = generator.getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
				int floor = generator.getFirstOccupiedHeight(x + dx, z + dz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
				// water on top means a lake or the sea
				if (surface != floor) {
					return Optional.empty();
				}
				lowest = Math.min(lowest, surface);
				highest = Math.max(highest, surface);
				sum += surface;
				samples++;
			}
		}
		if (highest - lowest > 6) {
			return Optional.empty();
		}
		int ground = Math.round(sum / (float) samples);
		long seed = context.random().nextLong();
		return Optional.of(new GenerationStub(new BlockPos(x, ground, z), builder -> builder.addPiece(new KampungPiece(seed, x, ground, z))));
	}

	@Override
	public StructureType<?> type() {
		return BlossomWorldgen.KAMPUNG;
	}
}
