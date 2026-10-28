package com.raihanadf.blossom;

import com.raihanadf.blossom.pets.PetFeeding;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomEntities;
import com.raihanadf.blossom.registry.BlossomItems;
import com.raihanadf.blossom.registry.BlossomWorldgen;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Blossom implements ModInitializer {
	public static final String MOD_ID = "blossom";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BlossomBlocks.init();
		BlossomEntities.init();
		BlossomWorldgen.init();
		PetFeeding.init();
		BlossomItems.init();
		LOGGER.info("blossom loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
