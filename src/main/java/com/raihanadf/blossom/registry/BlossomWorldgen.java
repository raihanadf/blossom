package com.raihanadf.blossom.registry;

import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.kampung.KampungPiece;
import com.raihanadf.blossom.kampung.KampungStructure;
import com.raihanadf.blossom.nusantara.NusantaraFeature;
import com.raihanadf.blossom.nusantara.TropicalBiomes;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

// the kampung structure for normal worlds, and the nusantara world type: its tropical biome source and the feature
// that builds the railway, stations, towns and kampungs into every chunk of a nusantara world
public final class BlossomWorldgen {
	public static final StructureType<KampungStructure> KAMPUNG = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Blossom.id("kampung"),
			() -> KampungStructure.CODEC);
	public static final StructurePieceType KAMPUNG_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Blossom.id("kampung"),
			(context, tag) -> new KampungPiece(tag));

	private BlossomWorldgen() {
	}

	// touching this class runs the static registrations above
	public static void init() {
		Registry.register(BuiltInRegistries.BIOME_SOURCE, Blossom.id("tropical"), TropicalBiomes.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Blossom.id("nusantara"), NusantaraFeature.CODEC);
		// last of all in every overworld biome, so the trees are already there to be cleared
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
				ResourceKey.create(Registries.PLACED_FEATURE, Blossom.id("nusantara")));
	}
}
