package com.mediascreen;

import com.mediascreen.block.MediaScreenBlock;
import com.mediascreen.block.MediaScreenBlockEntity;
import com.mediascreen.block.ModBlockEntities;
import com.mediascreen.client.MediaScreenRenderer;
import com.mediascreen.client.MediaScreenScreen;
import com.mediascreen.client.VideoManager;
import com.mediascreen.client.VideoPlayer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.MinecraftClient;

public class MediaScreenClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		BlockEntityRendererRegistry.register(ModBlockEntities.MEDIA_SCREEN, MediaScreenRenderer::new);
		MediaScreenBlock.openScreenClient = mse -> {
			MinecraftClient.getInstance().setScreen(new MediaScreenScreen(mse));
		};

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			for (VideoPlayer player : VideoManager.getAllPlayers()) {
				if (player.getTexture() != null) {
					player.getTexture().tickUpload();
				}
			}
		});
	}
}
