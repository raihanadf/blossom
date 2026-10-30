package com.raihanadf.blossom.client.datagen;

import static com.raihanadf.blossom.registry.BlossomBlocks.CHAIRS;
import static com.raihanadf.blossom.registry.BlossomBlocks.COOKING_POT;
import static com.raihanadf.blossom.registry.BlossomBlocks.CUTTING_BOARD;
import static com.raihanadf.blossom.registry.BlossomBlocks.GOAL_NET;
import static com.raihanadf.blossom.registry.BlossomBlocks.SPORTS_NET;
import static com.raihanadf.blossom.registry.BlossomBlocks.TICKET_GATE;
import static com.raihanadf.blossom.registry.BlossomBlocks.EXIT_GATE;
import static com.raihanadf.blossom.registry.BlossomBlocks.LAMP;
import static com.raihanadf.blossom.registry.BlossomBlocks.RICE;
import static com.raihanadf.blossom.registry.BlossomBlocks.RICE_CROP;
import static com.raihanadf.blossom.registry.BlossomBlocks.TABLES;
import static com.raihanadf.blossom.registry.BlossomBlocks.WOODS;
import static com.raihanadf.blossom.registry.BlossomBlocks.TRACK;
import static com.raihanadf.blossom.registry.BlossomItems.APPLE_PORRIDGE;
import static com.raihanadf.blossom.registry.BlossomItems.BATON;
import static com.raihanadf.blossom.registry.BlossomItems.FOOTBALL;
import static com.raihanadf.blossom.registry.BlossomItems.MOTOR;
import static com.raihanadf.blossom.registry.BlossomItems.PET_TREAT;
import static com.raihanadf.blossom.registry.BlossomItems.RACKET;
import static com.raihanadf.blossom.registry.BlossomItems.SHUTTLECOCK;
import static com.raihanadf.blossom.registry.BlossomItems.BEEF_SLICES;
import static com.raihanadf.blossom.registry.BlossomItems.CHICKEN_PIECES;
import static com.raihanadf.blossom.registry.BlossomItems.MIE_GORENG;
import static com.raihanadf.blossom.registry.BlossomItems.NASI_GORENG;
import static com.raihanadf.blossom.registry.BlossomItems.NOODLES;
import static com.raihanadf.blossom.registry.BlossomItems.SOTO_AYAM;
import static com.raihanadf.blossom.registry.BlossomItems.SOTO_SAPI;
import static com.raihanadf.blossom.registry.BlossomItems.APPLE_SLICES;
import static com.raihanadf.blossom.registry.BlossomItems.BERRY_COCOA;
import static com.raihanadf.blossom.registry.BlossomItems.BLOSSOM_HEART;
import static com.raihanadf.blossom.registry.BlossomItems.CARROT_SLICES;
import static com.raihanadf.blossom.registry.BlossomItems.CARROT_SOUP;
import static com.raihanadf.blossom.registry.BlossomItems.FISH_FILLET;
import static com.raihanadf.blossom.registry.BlossomItems.FISH_SOUP;
import static com.raihanadf.blossom.registry.BlossomItems.KITCHEN_KNIFE;
import static com.raihanadf.blossom.registry.BlossomItems.POTATO_WEDGES;
import static com.raihanadf.blossom.registry.BlossomItems.TAB;
import static com.raihanadf.blossom.registry.BlossomItems.VEGGIE_STEW;

import static com.raihanadf.blossom.registry.BlossomItems.TICKET;
import static com.raihanadf.blossom.registry.BlossomItems.TRACK_PIECE;
import com.google.gson.JsonObject;
import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.furniture.LampBlock;
import com.raihanadf.blossom.registry.BlossomEntities;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.storage.loot.predicates.MatchBlock;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;

public class BlossomDataGenerator implements DataGeneratorEntrypoint {
	// indonesian names of the vanilla woods, for the id_id lang file
	private static final Map<String, String> WOOD_ID = Map.ofEntries(Map.entry("oak", "Ek"), Map.entry("spruce", "Cemara"),
			Map.entry("birch", "Birk"), Map.entry("jungle", "Rimba"), Map.entry("acacia", "Akasia"), Map.entry("dark_oak", "Ek Gelap"),
			Map.entry("pale_oak", "Ek Pucat"), Map.entry("mangrove", "Bakau"), Map.entry("cherry", "Ceri"), Map.entry("poplar", "Poplar"),
			Map.entry("bamboo", "Bambu"), Map.entry("crimson", "Kirmizi"), Map.entry("warped", "Bengkok"));

	@Override
	public void onInitializeDataGenerator(FabricDataGenerator generator) {
		FabricDataGenerator.Pack pack = generator.createPack();

		pack.addProvider((FabricPackOutput output) -> new FabricModelProvider(output) {
			@Override
			public void generateBlockStateModels(BlockModelGenerators blocks) {
				// eight growth ages share four drawings, also writes the flat rice item model
				blocks.createCropBlock(RICE_CROP, CropBlock.AGE, 0, 0, 1, 1, 2, 2, 2, 3);

				// kitchen block shapes are hand-written in resources/assets/blossom/models/block
				for (Block block : List.of(CUTTING_BOARD, COOKING_POT, GOAL_NET)) {
					Identifier model = Blossom.id("block/" + name(block));
					blocks.blockStateOutput.accept(MultiVariantGenerator.dispatch(block, BlockModelGenerators.plainVariant(model)));
					blocks.registerSimpleItemModel(block, model);
				}
				Identifier track = Blossom.id("block/track");
				blocks.blockStateOutput.accept(MultiVariantGenerator.dispatch(TRACK, BlockModelGenerators.plainVariant(track)));
				blocks.registerSimpleItemModel(TRACK, track);
				Identifier net = Blossom.id("block/sports_net");
				blocks.blockStateOutput.accept(MultiVariantGenerator.dispatch(SPORTS_NET, BlockModelGenerators.plainVariant(net))
						.with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
				blocks.registerSimpleItemModel(SPORTS_NET, net);
				for (Block gate : List.of(TICKET_GATE, EXIT_GATE)) {
					Identifier model = Blossom.id("block/" + name(gate));
					blocks.blockStateOutput.accept(MultiVariantGenerator.dispatch(gate, BlockModelGenerators.plainVariant(model))
							.with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
					blocks.registerSimpleItemModel(gate, model);
				}

				// furniture reuses one template per shape, filled with the vanilla planks of its wood
				for (String wood : WOODS) {
					Identifier chair = child(blocks, name(CHAIRS.get(wood)), "template_chair", Map.of("planks", "minecraft:block/" + wood + "_planks"));
					blocks.blockStateOutput.accept(MultiVariantGenerator.dispatch(CHAIRS.get(wood), BlockModelGenerators.plainVariant(chair))
							.with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
					blocks.registerSimpleItemModel(CHAIRS.get(wood), chair);
					Identifier table = child(blocks, name(TABLES.get(wood)), "template_table", Map.of("planks", "minecraft:block/" + wood + "_planks"));
					blocks.blockStateOutput.accept(MultiVariantGenerator.dispatch(TABLES.get(wood), BlockModelGenerators.plainVariant(table)));
					blocks.registerSimpleItemModel(TABLES.get(wood), table);
				}

				// lamp: an oak base plus a wool shade in the dyed colour, the shade glows while the lamp is on
				MultiPartGenerator lamp = MultiPartGenerator.multiPart(LAMP)
						.with(BlockModelGenerators.plainVariant(child(blocks, "lamp_base", "template_lamp_base", Map.of("planks", "minecraft:block/oak_planks"))));
				for (DyeColor color : DyeColor.values()) {
					String wool = "minecraft:block/" + color.getSerializedName() + "_wool";
					lamp.with(BlockModelGenerators.condition().term(LampBlock.COLOR, color).term(LampBlock.GLOW, 0),
							BlockModelGenerators.plainVariant(child(blocks, "lamp_shade_" + color.getSerializedName(), "template_lamp_shade", Map.of("wool", wool))));
					lamp.with(BlockModelGenerators.condition().term(LampBlock.COLOR, color).term(LampBlock.GLOW, 1, 2, 3, 4),
							BlockModelGenerators.plainVariant(child(blocks, "lamp_shade_lit_" + color.getSerializedName(), "template_lamp_shade_lit", Map.of("wool", wool))));
				}
				blocks.blockStateOutput.accept(lamp);
				Identifier lampItem = child(blocks, "lamp", "template_lamp", Map.of("planks", "minecraft:block/oak_planks", "wool", "minecraft:block/white_wool"));
				blocks.registerSimpleItemModel(LAMP, lampItem);
			}

			// writes models/block/<name>.json as a child of a template with its textures filled in
			private Identifier child(BlockModelGenerators blocks, String name, String template, Map<String, String> textures) {
				Identifier id = Blossom.id("block/" + name);
				JsonObject json = new JsonObject();
				json.addProperty("parent", Blossom.id("block/" + template).toString());
				JsonObject slots = new JsonObject();
				textures.forEach(slots::addProperty);
				json.add("textures", slots);
				blocks.modelOutput.accept(id, () -> json);
				return id;
			}

			@Override
			public void generateItemModels(ItemModelGenerators items) {
				for (Item item : List.of(BLOSSOM_HEART, CARROT_SLICES, POTATO_WEDGES, APPLE_SLICES, FISH_FILLET,
						NOODLES, CHICKEN_PIECES, BEEF_SLICES, CARROT_SOUP, FISH_SOUP, VEGGIE_STEW, APPLE_PORRIDGE, BERRY_COCOA,
						SOTO_AYAM, SOTO_SAPI, MIE_GORENG, NASI_GORENG)) {
					items.generateFlatItem(item, ModelTemplates.FLAT_ITEM);
				}
				items.generateFlatItem(KITCHEN_KNIFE, ModelTemplates.FLAT_HANDHELD_ITEM);
				items.declareCustomModelItem(BATON);
				items.declareCustomModelItem(TRACK_PIECE);
				items.generateFlatItem(RACKET, ModelTemplates.FLAT_HANDHELD_ITEM);
				for (Item item : List.of(PET_TREAT, MOTOR, FOOTBALL, SHUTTLECOCK, TICKET)) {
					items.generateFlatItem(item, ModelTemplates.FLAT_ITEM);
				}
				for (EntityType<?> critter : CRITTERS) {
					items.generateFlatItem(spawnEgg(critter), ModelTemplates.FLAT_ITEM);
				}
				items.generateFlatItem(spawnEgg(BlossomEntities.WARGA), ModelTemplates.FLAT_ITEM);
				items.generateFlatItem(spawnEgg(BlossomEntities.SATPAM), ModelTemplates.FLAT_ITEM);
			}
		});

		pack.addProvider((output, registries) -> new FabricBlockLootSubProvider(output, registries) {
			@Override
			public void generate() {
				for (Block block : allBlocks()) {
					if (block != RICE_CROP) {
						dropSelf(block);
					}
				}
				add(RICE_CROP, createCropDrops(RICE_CROP, RICE, RICE, MatchBlock.blockMatches(blocks, RICE_CROP,
						StatePropertiesPredicate.Builder.properties().hasProperty(CropBlock.AGE, CropBlock.MAX_AGE))));
			}
		});

		pack.addProvider((output, registries) -> new FabricTagsProvider.BlockTagsProvider(output, registries) {
			@Override
			protected void addTags(HolderLookup.Provider lookup) {
				for (Block block : allBlocks()) {
					if (block == RICE_CROP) {
						continue;
					}
					builder(block == COOKING_POT ? BlockTags.MINEABLE_WITH_PICKAXE : BlockTags.MINEABLE_WITH_AXE).add(block.builtInRegistryHolder().key());
				}
			}
		});

		pack.addProvider((output, registries) -> new FabricRecipeProvider(output, registries) {
			@Override
			protected RecipeProvider createRecipeProvider(HolderLookup.Provider lookup, BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
				return new RecipeProvider(recipes, advancements) {
					@Override
					public void buildRecipes() {
						shaped(RecipeCategory.TOOLS, KITCHEN_KNIFE)
								.pattern(" I")
								.pattern("S ")
								.define('I', Items.IRON_INGOT)
								.define('S', Items.STICK)
								.unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
								.save(output);
						shaped(RecipeCategory.DECORATIONS, CUTTING_BOARD)
								.pattern("S##")
								.define('#', ItemTags.PLANKS)
								.define('S', Items.STICK)
								.unlockedBy("has_planks", has(ItemTags.PLANKS))
								.save(output);
						shaped(RecipeCategory.DECORATIONS, COOKING_POT)
								.pattern("C C")
								.pattern("CCC")
								.define('C', Items.COPPER_INGOT)
								.unlockedBy("has_copper_ingot", has(Items.COPPER_INGOT))
								.save(output);
						shaped(RecipeCategory.DECORATIONS, LAMP)
								.pattern("W")
								.pattern("T")
								.pattern("P")
								.define('W', Items.WOOL.white())
								.define('T', Items.TORCH)
								.define('P', ItemTags.WOODEN_SLABS)
								.unlockedBy("has_torch", has(Items.TORCH))
								.save(output);
						shapeless(RecipeCategory.FOOD, PET_TREAT, 4)
								.requires(Items.WHEAT)
								.requires(Items.SUGAR)
								.requires(Items.BONE_MEAL)
								.unlockedBy("has_wheat", has(Items.WHEAT))
								.save(output);
						shaped(RecipeCategory.TRANSPORTATION, MOTOR)
								.pattern("  I")
								.pattern("IRI")
								.pattern("B B")
								.define('I', Items.IRON_INGOT)
								.define('R', Items.REDSTONE)
								.define('B', Items.DYE.black())
								.unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
								.save(output);
						shaped(RecipeCategory.MISC, FOOTBALL)
								.pattern(" L ")
								.pattern("LSL")
								.pattern(" L ")
								.define('L', Items.LEATHER)
								.define('S', Items.SLIME_BALL)
								.unlockedBy("has_leather", has(Items.LEATHER))
								.save(output);
						shaped(RecipeCategory.MISC, RACKET)
								.pattern(" SS")
								.pattern(" SS")
								.pattern("T  ")
								.define('S', Items.STRING)
								.define('T', Items.STICK)
								.unlockedBy("has_string", has(Items.STRING))
								.save(output);
						shapeless(RecipeCategory.MISC, SHUTTLECOCK, 2)
								.requires(Items.FEATHER, 3)
								.requires(Items.STRING)
								.unlockedBy("has_feather", has(Items.FEATHER))
								.save(output);
						shaped(RecipeCategory.DECORATIONS, GOAL_NET, 2)
								.pattern("S S")
								.pattern(" S ")
								.pattern("S S")
								.define('S', Items.STRING)
								.unlockedBy("has_string", has(Items.STRING))
								.save(output);
						shaped(RecipeCategory.DECORATIONS, SPORTS_NET, 3)
								.pattern("SSS")
								.pattern("T T")
								.define('S', Items.STRING)
								.define('T', Items.STICK)
								.unlockedBy("has_string", has(Items.STRING))
								.save(output);
						for (String wood : WOODS) {
							Item planks = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(wood + "_planks"));
							shaped(RecipeCategory.DECORATIONS, CHAIRS.get(wood))
									.pattern("P  ")
									.pattern("PPP")
									.pattern("S S")
									.define('P', planks)
									.define('S', Items.STICK)
									.unlockedBy("has_planks", has(planks))
									.save(output);
							shaped(RecipeCategory.DECORATIONS, TABLES.get(wood))
									.pattern("PPP")
									.pattern("S S")
									.define('P', planks)
									.define('S', Items.STICK)
									.unlockedBy("has_planks", has(planks))
									.save(output);
						}
					}
				};
			}

			@Override
			public String getName() {
				return "Blossom recipes";
			}
		});

		// keep both languages in the same order so a missing line is easy to spot
		pack.addProvider((output, registries) -> new FabricLanguageProvider(output, "en_us", registries) {
			@Override
			public void generateTranslations(HolderLookup.Provider lookup, TranslationBuilder builder) {
				builder.addCreativeModeTab(TAB, "Blossom");
				builder.add(BLOSSOM_HEART, "Blossom");
				builder.add(CUTTING_BOARD, "Cutting Board");
				builder.add(COOKING_POT, "Cooking Pot");
				builder.add(LAMP, "Lamp");
				builder.add(TRACK, "Track");
				builder.add(TRACK_PIECE, "Track Piece");
				for (String wood : WOODS) {
					String name = titleCase(wood);
					builder.add(CHAIRS.get(wood), name + " Chair");
					builder.add(TABLES.get(wood), name + " Table");
				}
				builder.add(KITCHEN_KNIFE, "Kitchen Knife");
				builder.add(CARROT_SLICES, "Carrot Slices");
				builder.add(POTATO_WEDGES, "Potato Wedges");
				builder.add(APPLE_SLICES, "Apple Slices");
				builder.add(FISH_FILLET, "Fish Fillet");
				builder.add(RICE, "Rice");
				builder.add(RICE_CROP, "Rice");
				builder.add(NOODLES, "Noodles");
				builder.add(CHICKEN_PIECES, "Chicken Pieces");
				builder.add(BEEF_SLICES, "Beef Slices");
				builder.add(CARROT_SOUP, "Carrot Soup");
				builder.add(FISH_SOUP, "Fish Soup");
				builder.add(VEGGIE_STEW, "Veggie Stew");
				builder.add(APPLE_PORRIDGE, "Apple Porridge");
				builder.add(BERRY_COCOA, "Berry Cocoa");
				builder.add(SOTO_AYAM, "Soto Ayam");
				builder.add(SOTO_SAPI, "Soto Sapi");
				builder.add(MIE_GORENG, "Mie Goreng");
				builder.add(NASI_GORENG, "Nasi Goreng");
				critterNames(builder, "Hamster", "Hedgehog", "Capybara", "%s Spawn Egg");
				playNames(builder, "Pet Treat", "Motor", "Football", "Racket", "Shuttlecock", "Goal Net", "Badminton Net",
						"GOOOAL!", "GOOOAL! %s scores!", "Rally: %s hits", "Rally over at %s hits");
				wargaNames(builder, "Villager", "Baton", "Villager Spawn Egg", "Here, try some %s!",
						"Good afternoon! Come by any time.", "Have you eaten yet?", "Lovely weather today, isn't it?",
						"The rice is coming along nicely!", "Mind the paddy, it's muddy.", "Harvest is soon, insyaallah.",
						"Something good is on the stove!", "Wait a bit, it's almost ready.", "Come back later, I'll cook some more.",
						"Welcome! Everything's safe here.", "I'm on watch, don't worry.", "If you need anything, report to the post.",
						"Wanna play football?", "Catch me if you can!", "School's out!",
						"Mind the gap, the train is coming in.", "This train goes on to the next station.", "Tickets at the loket, please.",
						"Waiting for my train...", "I'm off to visit my family.", "The train is always a bit late, hehe.",
						"Snacks, snacks! Fresh and tasty!", "Something for the trip?", "Come back for more later!",
						"What a lovely kampung.", "Just got off the train!", "I'll head back to the station soon.",
						"Rails all clear up ahead!", "Inspecting the line before the train comes.", "Keep off the track, please.");
				builder.add(BlossomEntities.SATPAM, "Satpam");
				builder.add(spawnEgg(BlossomEntities.SATPAM), "Satpam Spawn Egg");
				builder.add("key.categories.blossom", "Blossom");
				builder.add("key.blossom.inspect", "Inspect Baton");
				builder.add("message.blossom.warga.vendor_gift", "Here you go, one %s!");
				builder.add("generator.blossom.nusantara", "Nusantara");
				builder.add("generator.blossom.nusantara.info", "Indonesia in the 80s and 90s: towns, kampungs and sawah joined by a winding railway.");
				builder.add(BlossomEntities.TRAIN_CAR, "Train");
				builder.add(BlossomEntities.LORI, "Inspection Trolley");
				builder.add("message.blossom.train.next", "KA %s · next stop: %s");
				builder.add("message.blossom.train.stopped", "KA %s · now at %s, sneak to get off");
				builder.add("message.blossom.train.full", "This car is full, try another one");
				builder.add("message.blossom.lori.stop", "Something's on the line up ahead, let's sort it out.");
				builder.add("message.blossom.lori.done", "All clear, let's go!");
				builder.add("message.blossom.lori.hurry", "Let's wrap this up, the train is waiting!");
				builder.add("message.blossom.lori.radio", "%s (radio): line clear, the train at %s may go.");
				builder.add("message.blossom.train.waiting", "KA %s · at %s, waiting for the line to be cleared");
				builder.add("message.blossom.train.cleared", "Stasiun %s (radio): the lori is in, the line to %s is clear.");
				builder.add(TICKET, "Train Ticket");
				builder.add(TICKET_GATE, "Ticket Gate");
				builder.add(EXIT_GATE, "Exit Gate");
				builder.add("message.blossom.gate.welcome", "Ticket checked, have a good trip!");
				builder.add("message.blossom.ticket.sold", "One ticket, here you are. Have a good trip!");
				builder.add("message.blossom.ticket.price", "A ticket is one iron ingot");
				builder.add("hud.blossom.place.station", "Stasiun %s");
				builder.add("hud.blossom.place.tier0", "Desa %s");
				builder.add("hud.blossom.place.tier1", "Desa %s");
				builder.add("hud.blossom.place.tier2", "Kota %s");
				builder.add("hud.blossom.place.tier3", "Kota %s");
				builder.add("message.blossom.pot.empty", "Drop some ingredients in!");
				builder.add("message.blossom.pot.no_recipe", "Hmm, these don't make a dish yet");
				builder.add("message.blossom.pot.no_heat", "Needs a fire, campfire, lava or magma underneath");
				builder.add("message.blossom.pot.cooking", "Bubbling away... almost there!");
				builder.add("message.blossom.pot.serve", "%s %s ready, serve it with a bowl");
			}
		});

		pack.addProvider((output, registries) -> new FabricLanguageProvider(output, "id_id", registries) {
			@Override
			public void generateTranslations(HolderLookup.Provider lookup, TranslationBuilder builder) {
				builder.addCreativeModeTab(TAB, "Blossom");
				builder.add(BLOSSOM_HEART, "Blossom");
				builder.add(CUTTING_BOARD, "Talenan");
				builder.add(COOKING_POT, "Panci Masak");
				builder.add(LAMP, "Lampu");
				builder.add(TRACK, "Rel");
				builder.add(TRACK_PIECE, "Potongan Rel");
				for (String wood : WOODS) {
					builder.add(CHAIRS.get(wood), "Kursi " + WOOD_ID.get(wood));
					builder.add(TABLES.get(wood), "Meja " + WOOD_ID.get(wood));
				}
				builder.add(KITCHEN_KNIFE, "Pisau Dapur");
				builder.add(CARROT_SLICES, "Irisan Wortel");
				builder.add(POTATO_WEDGES, "Potongan Kentang");
				builder.add(APPLE_SLICES, "Irisan Apel");
				builder.add(FISH_FILLET, "Fillet Ikan");
				builder.add(RICE, "Beras");
				builder.add(RICE_CROP, "Padi");
				builder.add(NOODLES, "Mie");
				builder.add(CHICKEN_PIECES, "Potongan Ayam");
				builder.add(BEEF_SLICES, "Irisan Daging Sapi");
				builder.add(CARROT_SOUP, "Sup Wortel");
				builder.add(FISH_SOUP, "Sup Ikan");
				builder.add(VEGGIE_STEW, "Semur Sayur");
				builder.add(APPLE_PORRIDGE, "Bubur Apel");
				builder.add(BERRY_COCOA, "Cokelat Beri");
				builder.add(SOTO_AYAM, "Soto Ayam");
				builder.add(SOTO_SAPI, "Soto Sapi");
				builder.add(MIE_GORENG, "Mie Goreng");
				builder.add(NASI_GORENG, "Nasi Goreng");
				critterNames(builder, "Hamster", "Landak", "Kapibara", "Telur Spawn %s");
				playNames(builder, "Camilan Hewan", "Motor", "Bola Sepak", "Raket", "Kok", "Jaring Gawang", "Net Bulu Tangkis",
						"GOOOL!", "GOOOL! %s mencetak gol!", "Rally: %s pukulan", "Rally selesai di %s pukulan");
				wargaNames(builder, "Warga", "Pentungan", "Telur Spawn Warga", "Nih, cobain %s-nya!",
						"Sugeng siang! Mampir sini aja.", "Sudah makan belum?", "Cuacanya enak ya hari ini.",
						"Padinya lagi bagus-bagusnya!", "Hati-hati, sawahnya becek.", "Sebentar lagi panen, insyaallah.",
						"Lagi masak yang enak nih!", "Tunggu sebentar ya, hampir matang.", "Nanti mampir lagi, saya masakin.",
						"Selamat datang! Aman terkendali.", "Saya jaga di sini, tenang saja.", "Kalau ada perlu, lapor ke pos ya.",
						"Main bola yuk!", "Ayo kejar aku!", "Asyik, sudah pulang sekolah!",
						"Awas, kereta mau masuk.", "Kereta ini lanjut ke stasiun berikutnya.", "Karcisnya di loket ya.",
						"Lagi nunggu kereta...", "Mau ke rumah saudara.", "Keretanya suka telat dikit, hehe.",
						"Jajan, jajan! Masih anget!", "Buat bekal di jalan?", "Nanti mampir lagi ya!",
						"Kampungnya asri ya.", "Baru turun dari kereta!", "Sebentar lagi balik ke stasiun.",
						"Rel aman, siap dilewati!", "Lagi periksa jalur sebelum kereta lewat.", "Jangan main di rel ya.");
				builder.add(BlossomEntities.SATPAM, "Satpam");
				builder.add(spawnEgg(BlossomEntities.SATPAM), "Telur Spawn Satpam");
				builder.add("key.categories.blossom", "Blossom");
				builder.add("key.blossom.inspect", "Periksa Pentungan");
				builder.add("message.blossom.warga.vendor_gift", "Monggo, %s-nya satu!");
				builder.add("generator.blossom.nusantara", "Nusantara");
				builder.add("generator.blossom.nusantara.info", "Indonesia tahun 80-90an: kota, kampung, dan sawah dihubungkan rel kereta yang berkelok.");
				builder.add(BlossomEntities.TRAIN_CAR, "Kereta");
				builder.add(BlossomEntities.LORI, "Lori Inspeksi");
				builder.add("message.blossom.train.next", "KA %s · stasiun berikutnya: %s");
				builder.add("message.blossom.train.stopped", "KA %s · berhenti di %s, jongkok untuk turun");
				builder.add("message.blossom.train.full", "Gerbong ini penuh, coba gerbong lain");
				builder.add("message.blossom.lori.stop", "Ada yang ganggu rel di depan, kita beresin dulu.");
				builder.add("message.blossom.lori.done", "Rel aman, lanjut jalan!");
				builder.add("message.blossom.lori.hurry", "Ayo cepat beresin, keretanya nunggu!");
				builder.add("message.blossom.lori.radio", "%s (radio): jalur aman, kereta di %s silakan berangkat.");
				builder.add("message.blossom.train.waiting", "KA %s · di %s, menunggu jalur aman");
				builder.add("message.blossom.train.cleared", "Stasiun %s (radio): lori sudah masuk, jalur ke %s aman.");
				builder.add(TICKET, "Karcis Kereta");
				builder.add(TICKET_GATE, "Pintu Masuk Peron");
				builder.add(EXIT_GATE, "Pintu Keluar Peron");
				builder.add("message.blossom.gate.welcome", "Karcis sudah dicek, selamat jalan!");
				builder.add("message.blossom.ticket.sold", "Satu karcis, ini dia. Selamat jalan!");
				builder.add("message.blossom.ticket.price", "Karcisnya satu batang besi");
				builder.add("hud.blossom.place.station", "Stasiun %s");
				builder.add("hud.blossom.place.tier0", "Desa %s");
				builder.add("hud.blossom.place.tier1", "Desa %s");
				builder.add("hud.blossom.place.tier2", "Kota %s");
				builder.add("hud.blossom.place.tier3", "Kota %s");
				builder.add("message.blossom.pot.empty", "Masukin bahan-bahannya dulu!");
				builder.add("message.blossom.pot.no_recipe", "Hmm, bahan ini belum jadi masakan apa-apa");
				builder.add("message.blossom.pot.no_heat", "Butuh api, api unggun, lava, atau magma di bawahnya");
				builder.add("message.blossom.pot.cooking", "Lagi mendidih... bentar lagi jadi!");
				builder.add("message.blossom.pot.serve", "%s %s siap, ambil pakai mangkuk");
			}
		});
	}

	private static final List<EntityType<?>> CRITTERS = List.of(BlossomEntities.HAMSTER, BlossomEntities.HEDGEHOG, BlossomEntities.CAPYBARA);

	private static Item spawnEgg(EntityType<?> critter) {
		return BuiltInRegistries.ITEM.getValue(Blossom.id(BuiltInRegistries.ENTITY_TYPE.getKey(critter).getPath() + "_spawn_egg"));
	}

	// names line up with CRITTERS, the egg pattern wraps the critter's name
	private static void critterNames(FabricLanguageProvider.TranslationBuilder builder, String hamster, String hedgehog, String capybara, String egg) {
		List<String> names = List.of(hamster, hedgehog, capybara);
		for (int i = 0; i < CRITTERS.size(); i++) {
			builder.add(CRITTERS.get(i), names.get(i));
			builder.add(spawnEgg(CRITTERS.get(i)), egg.formatted(names.get(i)));
		}
	}

	// item and block names for treats, rides and sports (the entities share them), then the goal and rally messages
	private static void playNames(FabricLanguageProvider.TranslationBuilder builder, String treat, String motor, String football, String racket,
			String shuttlecock, String goalNet, String sportsNet, String goal, String goalBy, String rally, String rallyOver) {
		builder.add(PET_TREAT, treat);
		builder.add(MOTOR, motor);
		builder.add(BlossomEntities.MOTOR, motor);
		builder.add(FOOTBALL, football);
		builder.add(BlossomEntities.FOOTBALL, football);
		builder.add(RACKET, racket);
		builder.add(SHUTTLECOCK, shuttlecock);
		builder.add(BlossomEntities.SHUTTLECOCK, shuttlecock);
		builder.add(GOAL_NET, goalNet);
		builder.add(SPORTS_NET, sportsNet);
		builder.add("message.blossom.goal", goal);
		builder.add("message.blossom.goal_by", goalBy);
		builder.add("message.blossom.rally", rally);
		builder.add("message.blossom.rally_over", rallyOver);
	}

	// villager, baton and egg names, then the cook's gift line, then three lines each for resident, farmer, cook and satpam
	private static void wargaNames(FabricLanguageProvider.TranslationBuilder builder, String warga, String baton, String egg, String gift, String... lines) {
		builder.add(BlossomEntities.WARGA, warga);
		builder.add(BATON, baton);
		builder.add(spawnEgg(BlossomEntities.WARGA), egg);
		builder.add("message.blossom.warga.says", "%s: %s");
		builder.add("message.blossom.warga.cook_gift", gift);
		List<String> jobs = List.of("resident", "farmer", "cook", "guard", "kid", "ppka", "passenger", "vendor", "traveler", "inspector");
		for (int i = 0; i < lines.length; i++) {
			builder.add("message.blossom.warga." + jobs.get(i / 3) + "." + i % 3, lines[i]);
		}
	}

	private static List<Block> allBlocks() {
		return BuiltInRegistries.BLOCK.stream().filter(block -> BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Blossom.MOD_ID)).toList();
	}

	private static String name(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath();
	}

	private static String titleCase(String id) {
		StringBuilder out = new StringBuilder();
		for (String word : id.split("_")) {
			out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}
}
