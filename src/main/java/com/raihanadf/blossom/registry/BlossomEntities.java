package com.raihanadf.blossom.registry;

import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.critters.Capybara;
import com.raihanadf.blossom.critters.Hamster;
import com.raihanadf.blossom.critters.Hedgehog;
import com.raihanadf.blossom.critters.Bebek;
import com.raihanadf.blossom.critters.Kodok;
import com.raihanadf.blossom.critters.Kucing;
import com.raihanadf.blossom.critters.Kukang;
import com.raihanadf.blossom.critters.Kutilang;
import com.raihanadf.blossom.furniture.SeatEntity;
import com.raihanadf.blossom.kampung.Satpam;
import com.raihanadf.blossom.kampung.Warga;
import com.raihanadf.blossom.motor.Motor;
import com.raihanadf.blossom.nusantara.Lori;
import com.raihanadf.blossom.nusantara.TrainCar;
import com.raihanadf.blossom.sports.Football;
import com.raihanadf.blossom.sports.Shuttlecock;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

public final class BlossomEntities {
	public static final EntityType<SeatEntity> SEAT = register("seat",
			EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.001f, 0.001f).noSummon().clientTrackingRange(10));

	public static final EntityType<Hamster> HAMSTER = register("hamster",
			EntityType.Builder.of(Hamster::new, MobCategory.CREATURE).sized(0.4f, 0.4f).clientTrackingRange(10));
	public static final EntityType<Hedgehog> HEDGEHOG = register("hedgehog",
			EntityType.Builder.of(Hedgehog::new, MobCategory.CREATURE).sized(0.45f, 0.35f).clientTrackingRange(10));
	public static final EntityType<Capybara> CAPYBARA = register("capybara",
			EntityType.Builder.of(Capybara::new, MobCategory.CREATURE).sized(0.8f, 0.75f).clientTrackingRange(10));
	public static final EntityType<Kodok> KODOK = register("kodok",
			EntityType.Builder.of(Kodok::new, MobCategory.CREATURE).sized(0.55f, 0.95f).clientTrackingRange(10));
	public static final EntityType<Bebek> BEBEK = register("bebek",
			EntityType.Builder.of(Bebek::new, MobCategory.CREATURE).sized(0.5f, 0.7f).clientTrackingRange(10));
	public static final EntityType<Kucing> KUCING = register("kucing",
			EntityType.Builder.of(Kucing::new, MobCategory.CREATURE).sized(0.55f, 0.6f).clientTrackingRange(10));
	public static final EntityType<Kukang> KUKANG = register("kukang",
			EntityType.Builder.of(Kukang::new, MobCategory.CREATURE).sized(0.45f, 0.55f).clientTrackingRange(10));
	public static final EntityType<Kutilang> KUTILANG = register("kutilang",
			EntityType.Builder.of(Kutilang::new, MobCategory.CREATURE).sized(0.35f, 0.35f).clientTrackingRange(10));

	// kampung folk, they live in the villages and never despawn
	public static final EntityType<Warga> WARGA = register("warga",
			EntityType.Builder.of(Warga::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).ridingOffset(-0.6f).clientTrackingRange(10));
	public static final EntityType<Satpam> SATPAM = register("satpam",
			EntityType.Builder.of(Satpam::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).ridingOffset(-0.6f).clientTrackingRange(10));

	public static final EntityType<Motor> MOTOR = register("motor",
			EntityType.Builder.<Motor>of(Motor::new, MobCategory.MISC).sized(0.9f, 1.0f).clientTrackingRange(10));
	public static final EntityType<Football> FOOTBALL = register("football",
			EntityType.Builder.<Football>of(Football::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(1));
	public static final EntityType<Shuttlecock> SHUTTLECOCK = register("shuttlecock",
			EntityType.Builder.<Shuttlecock>of(Shuttlecock::new, MobCategory.MISC).sized(0.25f, 0.25f).clientTrackingRange(8).updateInterval(1));
	// a car of a nusantara train, moved by the server along its line every tick
	public static final EntityType<TrainCar> TRAIN_CAR = register("train_car",
			EntityType.Builder.<TrainCar>of(TrainCar::new, MobCategory.MISC).sized(2.8f, 3.7f).noSummon().fireImmune().clientTrackingRange(16).updateInterval(1));
	// the inspection trolley that runs over a line just before its train, mending it
	public static final EntityType<Lori> LORI = register("lori",
			EntityType.Builder.<Lori>of(Lori::new, MobCategory.MISC).sized(1.9f, 2.1f).noSummon().fireImmune().clientTrackingRange(12).updateInterval(1));

	private BlossomEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(HAMSTER, Hamster.createAttributes());
		FabricDefaultAttributeRegistry.register(HEDGEHOG, Hedgehog.createAttributes());
		FabricDefaultAttributeRegistry.register(CAPYBARA, Capybara.createAttributes());
		FabricDefaultAttributeRegistry.register(KODOK, Kodok.createAttributes());
		FabricDefaultAttributeRegistry.register(BEBEK, Bebek.createAttributes());
		FabricDefaultAttributeRegistry.register(KUCING, Kucing.createAttributes());
		FabricDefaultAttributeRegistry.register(KUKANG, Kukang.createAttributes());
		FabricDefaultAttributeRegistry.register(KUTILANG, Kutilang.createAttributes());

		SpawnPlacements.register(HAMSTER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
		SpawnPlacements.register(HEDGEHOG, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);
		// swamps and mangroves are mud, not grass, so capybaras only need solid ground
		SpawnPlacements.register(CAPYBARA, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
		SpawnPlacements.register(KODOK, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
		SpawnPlacements.register(BEBEK, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
		SpawnPlacements.register(KUCING, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
		SpawnPlacements.register(KUKANG, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
		SpawnPlacements.register(KUTILANG, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING, Mob::checkMobSpawnRules);

		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.CHERRY_GROVE),
				MobCategory.CREATURE, HAMSTER, 8, 2, 3);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.FOREST, Biomes.FLOWER_FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DARK_FOREST),
				MobCategory.CREATURE, HEDGEHOG, 6, 1, 2);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.SPARSE_JUNGLE, Biomes.RIVER),
				MobCategory.CREATURE, CAPYBARA, 6, 2, 4);
		// kodok come in groups, enough for a band
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.RIVER, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE),
				MobCategory.CREATURE, KODOK, 8, 3, 5);
		// bebek come in flocks by rivers, swamps and the open plains; kucing only live in kampungs (spawned by the village)
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.RIVER, Biomes.SWAMP, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MANGROVE_SWAMP),
				MobCategory.CREATURE, BEBEK, 8, 4, 7);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE),
				MobCategory.CREATURE, KUKANG, 5, 1, 2);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.FOREST, Biomes.FLOWER_FOREST,
				Biomes.BIRCH_FOREST, Biomes.CHERRY_GROVE, Biomes.SAVANNA, Biomes.SPARSE_JUNGLE), MobCategory.CREATURE, KUTILANG, 7, 3, 6);

		FabricDefaultAttributeRegistry.register(WARGA, Warga.createAttributes());
		FabricDefaultAttributeRegistry.register(SATPAM, Satpam.createAttributes());

		for (EntityType<?> type : new EntityType<?>[] {HAMSTER, HEDGEHOG, CAPYBARA, KODOK, BEBEK, KUCING, KUKANG, KUTILANG, WARGA, SATPAM}) {
			BlossomItems.register(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath() + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type));
		}
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Blossom.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}
}
