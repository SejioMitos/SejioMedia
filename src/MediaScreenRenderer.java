package com.mediascreen.client;

import com.mediascreen.block.MediaScreenBlock;
import com.mediascreen.block.MediaScreenBlockEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

public class MediaScreenRenderer implements BlockEntityRenderer<MediaScreenBlockEntity> {

	public MediaScreenRenderer(BlockEntityRendererFactory.Context ctx) {}

	@Override
	public void render(MediaScreenBlockEntity entity, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {

		if (!entity.isPlaying()) return;

		VideoPlayer player = VideoManager.getPlayer(entity.getPos());
		if (player == null || player.getTexture() == null) return;

		VideoTexture tex = player.getTexture();
		if (tex.getWidth() == 0 || tex.getHeight() == 0) return;

		Direction facing = Direction.NORTH;
		if (entity.getCachedState().contains(MediaScreenBlock.FACING)) {
			facing = entity.getCachedState().get(MediaScreenBlock.FACING);
		}

		matrices.push();
		matrices.translate(0.5f, 0.5f, 0.5f);

		float yaw = switch (facing) {
			case NORTH -> 180f;
			case WEST -> -90f;
			case EAST -> 90f;
			default -> 0f;
		};
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		matrices.translate(-0.5f, -0.5f, 0.501f);

		float inset = 0.0625f;
		float sx = inset, sy = inset, sw = 1f - 2f * inset, sh = 1f - 2f * inset;

		String textureName = "video_" + entity.getPos().toShortString();
		Identifier texId = tex.getOrRegisterIdentifier(textureName);
		if (texId == null) {
			matrices.pop();
			return;
		}
		net.minecraft.client.render.RenderLayer layer = net.minecraft.client.render.RenderLayer.getEntityTranslucent(texId);

		VertexConsumer vc = vertexConsumers.getBuffer(layer);
		var entry = matrices.peek();

		vc.vertex(entry, sx, sy + sh, 0f).color(0xFFFFFFFF).texture(0f, 1f).overlay(overlay).light(light).normal(0f, 0f, 1f);
		vc.vertex(entry, sx + sw, sy + sh, 0f).color(0xFFFFFFFF).texture(1f, 1f).overlay(overlay).light(light).normal(0f, 0f, 1f);
		vc.vertex(entry, sx + sw, sy, 0f).color(0xFFFFFFFF).texture(1f, 0f).overlay(overlay).light(light).normal(0f, 0f, 1f);
		vc.vertex(entry, sx, sy, 0f).color(0xFFFFFFFF).texture(0f, 0f).overlay(overlay).light(light).normal(0f, 0f, 1f);

		matrices.pop();
	}
}
