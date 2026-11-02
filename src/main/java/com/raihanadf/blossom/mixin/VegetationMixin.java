package com.raihanadf.blossom.mixin;

import com.raihanadf.blossom.nusantara.TropicalBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.BambooFeature;
import net.minecraft.world.level.levelgen.feature.BlockBlobFeature;
import net.minecraft.world.level.levelgen.feature.FallenTreeFeature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// in a nusantara world the wild trees, fallen logs, mossy boulders and bamboo of world generation don't
// take root where the railway, a station or a settlement will be built, instead of being cut down after. saplings a
// player grows are not world generation and grow anywhere
@Mixin({TreeFeature.class, FallenTreeFeature.class, BlockBlobFeature.class, BambooFeature.class})
public abstract class VegetationMixin {
	@Inject(method = "place", at = @At("HEAD"), cancellable = true)
	private void blossom$keepOffTheLine(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> result) {
		if (level instanceof WorldGenRegion && generator.getBiomeSource() instanceof TropicalBiomes tropical
				&& tropical.network(level).reserved(origin.getX(), origin.getZ())) {
			result.setReturnValue(false);
		}
	}
}
