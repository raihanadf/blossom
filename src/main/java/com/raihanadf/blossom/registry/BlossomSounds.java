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
