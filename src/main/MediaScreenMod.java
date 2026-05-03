package com.mediascreen;

import net.fabricmc.api.ModInitializer;
import com.mediascreen.block.ModBlockEntities;
import com.mediascreen.block.ModBlocks;

public class MediaScreenMod implements ModInitializer {

	public static final String MOD_ID = "mediascreen";

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModBlockEntities.register();
	}
}
