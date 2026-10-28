package com.raihanadf.blossom.registry;

import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.kampung.KampungPiece;
import com.raihanadf.blossom.kampung.KampungStructure;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

// the kampung structure; where it may appear lives in data/blossom/worldgen and the has_structure biome tag
public final class BlossomWorldgen {
	public static final StructureType<KampungStructure> KAMPUNG = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Blossom.id("kampung"),
			() -> KampungStructure.CODEC);
	public static final StructurePieceType KAMPUNG_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Blossom.id("kampung"),
			(context, tag) -> new KampungPiece(tag));

	private BlossomWorldgen() {
	}

	// touching this class runs the static registrations above
	public static void init() {
	}
}
