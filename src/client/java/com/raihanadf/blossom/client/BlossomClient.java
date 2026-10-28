package com.raihanadf.blossom.client;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.client.render.CookingPotRenderer;
import com.raihanadf.blossom.client.render.CuttingBoardRenderer;
import com.raihanadf.blossom.client.render.WargaRenderer;
import com.raihanadf.blossom.critters.Critter;
import com.raihanadf.blossom.registry.BlossomBlocks;
import com.raihanadf.blossom.registry.BlossomEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.Minecraft;
import com.raihanadf.blossom.motor.Motor;
import com.raihanadf.blossom.client.render.RideRenderers;

public class BlossomClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockEntityRenderers.register(BlossomBlocks.CUTTING_BOARD_ENTITY, CuttingBoardRenderer::new);
		BlockEntityRenderers.register(BlossomBlocks.COOKING_POT_ENTITY, CookingPotRenderer::new);
		// seats are invisible, the player sitting on them is all you see
		EntityRenderers.register(BlossomEntities.SEAT, NoopRenderer::new);
		critter(BlossomEntities.HAMSTER, 0.25f);
		critter(BlossomEntities.HEDGEHOG, 0.3f);
		critter(BlossomEntities.CAPYBARA, 0.5f);
		EntityRenderers.register(BlossomEntities.WARGA, WargaRenderer::new);
		EntityRenderers.register(BlossomEntities.MOTOR, RideRenderers.motor());
		EntityRenderers.register(BlossomEntities.FOOTBALL, RideRenderers.football());
		EntityRenderers.register(BlossomEntities.SHUTTLECOCK, RideRenderers.shuttlecock());
		// every motor that shows up on this client gets its own engine sound
		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Motor motor) {
				Minecraft.getInstance().getSoundManager().play(new MotorSound(motor));
			}
		});
	}

	// geckolib model, texture and animations are all found by the entity's name under assets/blossom
	private static <T extends Critter> void critter(EntityType<T> type, float shadow) {
		DefaultedEntityGeoModel<T> model = new DefaultedEntityGeoModel<>(Blossom.id(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath()));
		EntityRendererProvider<T> provider = context -> new GeoEntityRenderer<T, LivingEntityRenderState>(context, model) {
			{
				shadowRadius = shadow;
			}
		};
		EntityRenderers.register(type, provider);
	}
}
