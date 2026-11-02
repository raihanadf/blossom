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

	public static final SoundEvent BEBEK_AMBIENT = register("entity.bebek.ambient");
	public static final SoundEvent BEBEK_HURT = register("entity.bebek.hurt");
	public static final SoundEvent BEBEK_DEATH = register("entity.bebek.death");
	public static final SoundEvent BEBEK_STEP = register("entity.bebek.step");
	public static final SoundEvent BEBEK_HAPPY = register("entity.bebek.happy");
	public static final SoundEvent BEBEK_SPLASH = register("entity.bebek.splash");
	public static final SoundEvent BEBEK_FLAP = register("entity.bebek.flap");

	public static final SoundEvent KUCING_AMBIENT = register("entity.kucing.ambient");
	public static final SoundEvent KUCING_HURT = register("entity.kucing.hurt");
	public static final SoundEvent KUCING_DEATH = register("entity.kucing.death");
	public static final SoundEvent KUCING_STEP = register("entity.kucing.step");
	public static final SoundEvent KUCING_HAPPY = register("entity.kucing.happy");
	public static final SoundEvent KUCING_PURR = register("entity.kucing.purr");
	public static final SoundEvent KUCING_CHIRP = register("entity.kucing.chirp");

	public static final SoundEvent KUKANG_AMBIENT = register("entity.kukang.ambient");
	public static final SoundEvent KUKANG_HURT = register("entity.kukang.hurt");
	public static final SoundEvent KUKANG_DEATH = register("entity.kukang.death");
	public static final SoundEvent KUKANG_STEP = register("entity.kukang.step");
	public static final SoundEvent KUKANG_HAPPY = register("entity.kukang.happy");
	public static final SoundEvent KUKANG_LICK = register("entity.kukang.lick");

	public static final SoundEvent KUTILANG_AMBIENT = register("entity.kutilang.ambient");
	public static final SoundEvent KUTILANG_HURT = register("entity.kutilang.hurt");
	public static final SoundEvent KUTILANG_DEATH = register("entity.kutilang.death");
	public static final SoundEvent KUTILANG_STEP = register("entity.kutilang.step");
	public static final SoundEvent KUTILANG_HAPPY = register("entity.kutilang.happy");
	public static final SoundEvent KUTILANG_SONG = register("entity.kutilang.song");
	public static final SoundEvent KUTILANG_FLAP = register("entity.kutilang.flap");

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
