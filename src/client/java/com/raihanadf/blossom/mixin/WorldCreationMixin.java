package com.raihanadf.blossom.mixin;

import com.raihanadf.blossom.Blossom;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// a new world is a nusantara world unless you pick another type
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void blossom$nusantaraByDefault(Path savesFolder, WorldCreationContext settings, Optional<ResourceKey<WorldPreset>> preset, OptionalLong seed,
			CallbackInfo callback) {
		// the plain "create new world" button asks for the default preset, anything else (a world being re-created) keeps its own
		if (preset.isPresent() && preset.get() != WorldPresets.NORMAL) {
			return;
		}
		WorldCreationUiState state = (WorldCreationUiState) (Object) this;
		for (WorldCreationUiState.WorldTypeEntry entry : state.getNormalPresetList()) {
			if (entry.preset() != null && entry.preset().is(ResourceKey.create(Registries.WORLD_PRESET, Blossom.id("nusantara")))) {
				state.setWorldType(entry);
				return;
			}
		}
	}
}
