package com.raihanadf.blossom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;

// what a player says in chat pops up in a little pixel speech bubble over their head for a few seconds
public final class ChatBubbles {
	private static final int WIDTH = 150;
	private static final int LINES = 4;
	private static final int OUTLINE = 0xFF1E1E24;
	private static final int FILL = 0xFFF4F1EA;
	private static final int TEXT = 0xFF26242C;
	private static final int BRIGHT = 15728880;

	private record Bubble(List<FormattedCharSequence> lines, long until) {
	}

	private static final Map<UUID, Bubble> SAID = new ConcurrentHashMap<>();

	private ChatBubbles() {
	}

	public static void init() {
		// player chat always carries the signed message; anything else (server notices, /say from the console) has no speaker
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, receivedAt) -> {
			if (signed == null) {
				return;
			}
			Font font = Minecraft.getInstance().font;
			// at most four lines; a longer message is cut at a word and ends in an ellipsis
			String text = signed.signedContent();
			List<FormattedCharSequence> lines = font.split(Component.literal(text), WIDTH);
			while (lines.size() > LINES && text.contains(" ")) {
				text = text.substring(0, text.lastIndexOf(' '));
				lines = font.split(Component.literal(text + "…"), WIDTH);
			}
			// a short line stays up 4 seconds, a long one up to 10
			long shown = Math.min(10_000, 4_000 + signed.signedContent().length() * 60L);
			SAID.put(signed.sender(), new Bubble(lines.subList(0, Math.min(LINES, lines.size())), Util.getMillis() + shown));
		});
	}

	// called while a player is drawn, with the pose at their feet: the bubble floats over their name tag
	public static void submit(int entityId, boolean hasNameTag, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		Entity entity = Minecraft.getInstance().level.getEntity(entityId);
		if (entity == null) {
			return;
		}
		Bubble bubble = SAID.get(entity.getUUID());
		if (bubble == null) {
			return;
		}
		if (Util.getMillis() > bubble.until()) {
			SAID.remove(entity.getUUID());
			return;
		}
		Font font = Minecraft.getInstance().font;
		Vec3 head = entity.getAttachments().get(EntityAttachment.NAME_TAG, 0, entity.getYRot());
		int width = 0;
		for (FormattedCharSequence line : bubble.lines()) {
			width = Math.max(width, font.width(line));
		}
		// box in name tag pixels, the tail at its bottom pointing down at the name
		float x0 = -width / 2f - 3;
		float x1 = width / 2f + 3;
		float y1 = 0;
		float y0 = y1 - bubble.lines().size() * 9 - 4;

		pose.pushPose();
		pose.translate(head.x, head.y + (hasNameTag ? 0.85 : 0.6), head.z);
		pose.rotate(camera.orientation);
		pose.scale(0.025f, -0.025f, 0.025f);
		// the dark outline with its corners cut, so the box reads as round, then the tail
		collector.submitTextBackground(pose, x0 - 1, y0, x1 + 1, y1, OUTLINE, Font.DisplayMode.NORMAL, BRIGHT);
		collector.submitTextBackground(pose, x0, y0 - 1, x1, y1 + 1, OUTLINE, Font.DisplayMode.NORMAL, BRIGHT);
		collector.submitTextBackground(pose, -3, y1 + 1, 3, y1 + 2, OUTLINE, Font.DisplayMode.NORMAL, BRIGHT);
		collector.submitTextBackground(pose, -2, y1 + 2, 2, y1 + 3, OUTLINE, Font.DisplayMode.NORMAL, BRIGHT);
		collector.submitTextBackground(pose, -1, y1 + 3, 1, y1 + 4, OUTLINE, Font.DisplayMode.NORMAL, BRIGHT);
		// the paper on top, a hair nearer the camera
		pose.translate(0, 0, 0.5f);
		collector.submitTextBackground(pose, x0, y0, x1, y1, FILL, Font.DisplayMode.NORMAL, BRIGHT);
		collector.submitTextBackground(pose, -2, y1, 2, y1 + 2, FILL, Font.DisplayMode.NORMAL, BRIGHT);
		collector.submitTextBackground(pose, -1, y1 + 2, 1, y1 + 3, FILL, Font.DisplayMode.NORMAL, BRIGHT);
		pose.translate(0, 0, 0.5f);
		for (int i = 0; i < bubble.lines().size(); i++) {
			FormattedCharSequence line = bubble.lines().get(i);
			collector.submitText(pose, -font.width(line) / 2f, y0 + 3 + i * 9, line, false, Font.DisplayMode.NORMAL, BRIGHT, TEXT, 0, 0);
		}
		pose.popPose();
	}
}
