package com.raihanadf.blossom.client.datagen;

import com.raihanadf.blossom.registry.BlossomItems;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.core.HolderLookup;

public class BlossomDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator generator) {
		FabricDataGenerator.Pack pack = generator.createPack();

		pack.addProvider((FabricPackOutput output) -> new FabricModelProvider(output) {
			@Override
			public void generateBlockStateModels(BlockModelGenerators blocks) {
			}

			@Override
			public void generateItemModels(ItemModelGenerators items) {
				items.generateFlatItem(BlossomItems.SAKURA_PETAL, ModelTemplates.FLAT_ITEM);
			}
		});

		// keep both languages in the same order so a missing line is easy to spot
		pack.addProvider((output, registries) -> new FabricLanguageProvider(output, "en_us", registries) {
			@Override
			public void generateTranslations(HolderLookup.Provider lookup, TranslationBuilder builder) {
				builder.addCreativeModeTab(BlossomItems.TAB, "Blossom");
				builder.add(BlossomItems.SAKURA_PETAL, "Sakura Petal");
			}
		});

		pack.addProvider((output, registries) -> new FabricLanguageProvider(output, "id_id", registries) {
			@Override
			public void generateTranslations(HolderLookup.Provider lookup, TranslationBuilder builder) {
				builder.addCreativeModeTab(BlossomItems.TAB, "Blossom");
				builder.add(BlossomItems.SAKURA_PETAL, "Kelopak Sakura");
			}
		});
	}
}
