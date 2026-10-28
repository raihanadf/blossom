package com.raihanadf.blossom.client.render;

import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.kampung.Warga;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

// kampung folk use the player body (its outer layer parts ride along with the limbs), each with one of blossom's own skins
public class WargaRenderer extends HumanoidMobRenderer<Warga, WargaRenderer.State, HumanoidModel<WargaRenderer.State>> {
	public WargaRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
	}

	public static class State extends HumanoidRenderState {
		int skin;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Warga warga, State state, float partialTicks) {
		super.extractRenderState(warga, state, partialTicks);
		state.skin = warga.getSkin();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return Blossom.id("textures/entity/warga/warga_" + state.skin + ".png");
	}
}
