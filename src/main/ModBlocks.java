package com.mediascreen.block;

import com.mediascreen.MediaScreenMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {

	public static final MediaScreenBlock MEDIA_SCREEN = new MediaScreenBlock(
		AbstractBlock.Settings.create()
			.strength(3.0f)
			.nonOpaque()
	);

	public static void register() {
		Registry.register(Registries.BLOCK, id("media_screen"), MEDIA_SCREEN);
		Registry.register(Registries.ITEM, id("media_screen"),
			new BlockItem(MEDIA_SCREEN, new Item.Settings()));
	}

	private static Identifier id(String path) {
		return Identifier.of(MediaScreenMod.MOD_ID, path);
	}
}
