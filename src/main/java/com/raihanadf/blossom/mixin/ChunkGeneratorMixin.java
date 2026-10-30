package com.raihanadf.blossom.mixin;

import com.raihanadf.blossom.nusantara.TropicalBiomes;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// a nusantara world leaves out vanilla villages, outposts, mansions and igloos (and blossom's own scattered kampungs):
// its towns and kampungs come with the railway instead
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
	@Inject(method = "createState", at = @At("HEAD"), cancellable = true)
	private void blossom$skipStructures(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed,
			CallbackInfoReturnable<ChunkGeneratorStructureState> result) {
		ChunkGenerator self = (ChunkGenerator) (Object) this;
		if (self.getBiomeSource() instanceof TropicalBiomes && structureSets instanceof HolderLookup.RegistryLookup<StructureSet> lookup) {
			Set<StructureSet> skipped = TropicalBiomes.SKIPPED.stream().map(lookup::get).flatMap(Optional::stream).map(Holder::value)
					.collect(Collectors.toSet());
			result.setReturnValue(ChunkGeneratorStructureState.createForNormal(randomState, seed, self.getOrigin(randomState), self.getBiomeSource(),
					lookup.filterElements(set -> !skipped.contains(set))));
		}
	}
}
