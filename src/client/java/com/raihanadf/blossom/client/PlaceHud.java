package com.raihanadf.blossom.client;

import com.raihanadf.blossom.Blossom;
import com.raihanadf.blossom.nusantara.PlacePayload;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// a little wooden tag in the corner: where you are (station, desa or kota) and the time of day, like a station clock
public final class PlaceHud {
	private static final Identifier TAG = Blossom.id("hud/place_tag");
	private static PlacePayload place = new PlacePayload("", "");

	private PlaceHud() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(PlacePayload.TYPE, (payload, context) -> place = payload);
		HudElementRegistry.addLast(Blossom.id("place"), PlaceHud::draw);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.gui.hud.isHidden() || minecraft.getDebugOverlay().showDebugScreen()) {
			return;
		}
		long day = minecraft.level.getOverworldClockTime() % 24000;
		int minutes = (int) (((day + 6000) % 24000) * 1440 / 24000);
		String clock = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
		Component text = place.key().isEmpty() ? Component.literal(clock)
				: Component.translatable(place.key(), place.name()).append("  " + clock);
		int width = minecraft.font.width(text) + 12;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TAG, 4, 4, width, 15);
		graphics.text(minecraft.font, text, 10, 8, 0xFFF3E6C4, true);
	}
}
