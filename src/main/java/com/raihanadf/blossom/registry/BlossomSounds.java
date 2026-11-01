package com.raihanadf.blossom.registry;

import com.raihanadf.blossom.Blossom;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

// blossom's own sounds, all synthesized for the mod. files and subtitles: assets/blossom/sounds.json
public final class BlossomSounds {
	// in registration order, the lang providers give their subtitles in this same order
	public static final List<SoundEvent> ALL = new ArrayList<>();

	public static final SoundEvent HAMSTER_AMBIENT = register("entity.hamster.ambient");
	public static final SoundEvent HAMSTER_HURT = register("entity.hamster.hurt");
	public static final SoundEvent HAMSTER_DEATH = register("entity.hamster.death");
	public static final SoundEvent HAMSTER_STEP = register("entity.hamster.step");
	public static final SoundEvent HAMSTER_MUNCH = register("entity.hamster.munch");
	public static final SoundEvent HAMSTER_HAPPY = register("entity.hamster.happy");

	public static final SoundEvent HEDGEHOG_AMBIENT = register("entity.hedgehog.ambient");
	public static final SoundEvent HEDGEHOG_HURT = register("entity.hedgehog.hurt");
	public static final SoundEvent HEDGEHOG_DEATH = register("entity.hedgehog.death");
	public static final SoundEvent HEDGEHOG_STEP = register("entity.hedgehog.step");
	public static final SoundEvent HEDGEHOG_CURL = register("entity.hedgehog.curl");
	public static final SoundEvent HEDGEHOG_DIG = register("entity.hedgehog.dig");
	public static final SoundEvent HEDGEHOG_HAPPY = register("entity.hedgehog.happy");

	public static final SoundEvent CAPYBARA_AMBIENT = register("entity.capybara.ambient");
	public static final SoundEvent CAPYBARA_HURT = register("entity.capybara.hurt");
	public static final SoundEvent CAPYBARA_DEATH = register("entity.capybara.death");
	public static final SoundEvent CAPYBARA_STEP = register("entity.capybara.step");
	public static final SoundEvent CAPYBARA_CHEW = register("entity.capybara.chew");
	public static final SoundEvent CAPYBARA_HAPPY = register("entity.capybara.happy");

	public static final SoundEvent KODOK_AMBIENT = register("entity.kodok.ambient");
	public static final SoundEvent KODOK_HURT = register("entity.kodok.hurt");
	public static final SoundEvent KODOK_DEATH = register("entity.kodok.death");
	public static final SoundEvent KODOK_STEP = register("entity.kodok.step");
	public static final SoundEvent KODOK_HAPPY = register("entity.kodok.happy");
	public static final SoundEvent KODOK_TONGUE = register("entity.kodok.tongue");
	public static final SoundEvent KODOK_CAST = register("entity.kodok.cast");
	public static final SoundEvent KODOK_CATCH = register("entity.kodok.catch");
	// the band's stems: one bar loop each, all in the same key and tempo, started together on the loop grid
	public static final SoundEvent KODOK_BIOLA = register("music.kodok.biola");
	public static final SoundEvent KODOK_ANGKLUNG = register("music.kodok.angklung");
	public static final SoundEvent KODOK_KENDANG = register("music.kodok.kendang");

	private BlossomSounds() {
	}

	public static void init() {
	}

	private static SoundEvent register(String name) {
		SoundEvent event = Registry.register(BuiltInRegistries.SOUND_EVENT, Blossom.id(name), SoundEvent.createVariableRangeEvent(Blossom.id(name)));
		ALL.add(event);
		return event;
	}
}
