package com.mediascreen.block;

import com.mediascreen.MediaScreenMod;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {

	public static BlockEntityType<MediaScreenBlockEntity> MEDIA_SCREEN;

	public static void register() {
		MEDIA_SCREEN = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			id("media_screen"),
			BlockEntityType.Builder.create(
				MediaScreenBlockEntity::new,
				ModBlocks.MEDIA_SCREEN
			).build()
		);
	}

	private static Identifier id(String path) {
		return Identifier.of(MediaScreenMod.MOD_ID, path);
	}
}
