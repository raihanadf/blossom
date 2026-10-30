package com.raihanadf.blossom.nusantara;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jspecify.annotations.Nullable;

// the biomes of a nusantara world: the normal overworld ones, with the snowy, frozen and desert ones swapped for warm
// ones so it feels like the tropics. it also marks a world as nusantara and holds its railway network
public class TropicalBiomes extends BiomeSource {
	public record Swap(Holder<Biome> from, Holder<Biome> to) {
		public static final Codec<Swap> CODEC = RecordCodecBuilder.create(i -> i.group(
				Biome.CODEC.fieldOf("from").forGetter(Swap::from),
				Biome.CODEC.fieldOf("to").forGetter(Swap::to)
		).apply(i, Swap::new));
	}

	public static final MapCodec<TropicalBiomes> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			BiomeSource.CODEC.fieldOf("base").forGetter(s -> s.base),
			Swap.CODEC.listOf().fieldOf("replace").forGetter(s -> s.swaps)
	).apply(i, i.stable(TropicalBiomes::new)));

	// structure sets that don't belong in a nusantara world (it has its own kampungs and towns)
	public static final List<ResourceKey<StructureSet>> SKIPPED = List.of(
			ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("villages")),
			ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("pillager_outposts")),
			ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("woodland_mansions")),
			ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("igloos")),
			ResourceKey.create(Registries.STRUCTURE_SET, Identifier.fromNamespaceAndPath("blossom", "kampung")));

	private final BiomeSource base;
	private final List<Swap> swaps;
	private final Map<Holder<Biome>, Holder<Biome>> swapped = new HashMap<>();
	private volatile @Nullable NusantaraNetwork network;

	public TropicalBiomes(BiomeSource base, List<Swap> swaps) {
		this.base = base;
		this.swaps = swaps;
		for (Swap swap : swaps) {
			swapped.put(swap.from(), swap.to());
		}
	}

	private Holder<Biome> swap(Holder<Biome> biome) {
		return swapped.getOrDefault(biome, biome);
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return base.possibleBiomes().stream().map(this::swap).distinct();
	}

	@Override
	public BiomeResolver createResolver(Climate.Sampler sampler) {
		BiomeResolver inner = base.createResolver(sampler);
		return (x, y, z) -> swap(inner.getNoiseBiome(x, y, z));
	}

	// the railway network of this world, worked out the first time anything asks for it
	public NusantaraNetwork network(ServerLevel level) {
		NusantaraNetwork current = network;
		if (current == null) {
			synchronized (this) {
				current = network;
				if (current == null) {
					current = new NusantaraNetwork(level.getSeed(), level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), level);
					network = current;
				}
			}
		}
		return current;
	}

	public NusantaraNetwork network(WorldGenLevel level) {
		return network(level.getLevel());
	}
}
