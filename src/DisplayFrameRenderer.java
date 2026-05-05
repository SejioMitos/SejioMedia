package com.mediascreen.client;

import com.mediascreen.block.DisplayFrameBlock;
import com.mediascreen.block.DisplayFrameBlockEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

public class DisplayFrameRenderer implements BlockEntityRenderer<DisplayFrameBlockEntity> {

	public DisplayFrameRenderer(BlockEntityRendererFactory.Context ctx) {}

	private static final float SCREEN_MIN_X = 7.0f / 16.0f;
	private static final float SCREEN_MAX_X = 8.0f / 16.0f;
	private static final float SCREEN_MIN_Y = 0.0f;
	private static final float SCREEN_MIN_Z = -3.5f / 16.0f;
	private static final float SCREEN_WIDTH = 24.0f / 16.0f;
	private static final float SCREEN_HEIGHT = 14.0f / 16.0f;
	private static final float SURFACE_OFFSET = 0.01f;

	@Override
	public void render(DisplayFrameBlockEntity entity, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {

		if (!entity.isPlaying()) return;

		if (entity.getWorld().getBlockState(entity.getPos()).isOf(net.minecraft.block.Blocks.AIR)) {
			VideoManager.stopPlayer(entity.getPos());
			return;
		}

		VideoPlayer player = VideoManager.getPlayer(entity.getPos());
		if (player == null || player.getTexture() == null) return;

		VideoTexture tex = player.getTexture();
		if (tex.getWidth() == 0 || tex.getHeight() == 0) return;

		Direction facing = Direction.NORTH;
		if (entity.getCachedState().contains(DisplayFrameBlock.FACING)) {
			facing = entity.getCachedState().get(DisplayFrameBlock.FACING);
		}

		matrices.push();
		matrices.translate(0.5f, 0.5f, 0.5f);

		float yaw = switch (facing) {
			case NORTH -> 270f;
			case SOUTH -> 90f;
			case WEST -> 180f;
			default -> 0f;
		};
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));

		String textureName = "video_" + entity.getPos().toShortString();
		Identifier texId = tex.getOrRegisterIdentifier(textureName);
		if (texId == null) {
			matrices.pop();
			return;
		}
		net.minecraft.client.render.RenderLayer layer = net.minecraft.client.render.RenderLayer.getEntityTranslucent(texId);

		VertexConsumer vc = vertexConsumers.getBuffer(layer);
		var entry = matrices.peek();
		ScreenUvMapper.UvRange uv = ScreenUvMapper.forDisplayFrame(entity, facing);
		float uLeft = uv.maxU();
		float uRight = uv.minU();

		float frontX = SCREEN_MAX_X - 0.5f + SURFACE_OFFSET;
		float backX = SCREEN_MIN_X - 0.5f - SURFACE_OFFSET;
		float minY = SCREEN_MIN_Y - 0.5f;
		float maxY = minY + SCREEN_HEIGHT;
		float minZ = SCREEN_MIN_Z - 0.5f;
		float maxZ = minZ + SCREEN_WIDTH;

		vc.vertex(entry, frontX, minY, minZ).color(0xFFFFFFFF).texture(uLeft, 1f).overlay(overlay).light(light).normal(1f, 0f, 0f);
		vc.vertex(entry, frontX, maxY, minZ).color(0xFFFFFFFF).texture(uLeft, 0f).overlay(overlay).light(light).normal(1f, 0f, 0f);
		vc.vertex(entry, frontX, maxY, maxZ).color(0xFFFFFFFF).texture(uRight, 0f).overlay(overlay).light(light).normal(1f, 0f, 0f);
		vc.vertex(entry, frontX, minY, maxZ).color(0xFFFFFFFF).texture(uRight, 1f).overlay(overlay).light(light).normal(1f, 0f, 0f);

		vc.vertex(entry, backX, minY, minZ).color(0xFFFFFFFF).texture(uRight, 1f).overlay(overlay).light(light).normal(-1f, 0f, 0f);
		vc.vertex(entry, backX, minY, maxZ).color(0xFFFFFFFF).texture(uLeft, 1f).overlay(overlay).light(light).normal(-1f, 0f, 0f);
		vc.vertex(entry, backX, maxY, maxZ).color(0xFFFFFFFF).texture(uLeft, 0f).overlay(overlay).light(light).normal(-1f, 0f, 0f);
		vc.vertex(entry, backX, maxY, minZ).color(0xFFFFFFFF).texture(uRight, 0f).overlay(overlay).light(light).normal(-1f, 0f, 0f);

		matrices.pop();
	}
}
