package com.mediascreen.block;

import com.mediascreen.MediaScreenMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {

	/*public static final MediaScreenBlock MEDIA_SCREEN = new MediaScreenBlock(
		AbstractBlock.Settings.create()
			.strength(3.0f)
			.nonOpaque()
			.luminance(state -> {
				if (!state.contains(MediaScreenBlock.PLAYING)) return 0;
				return state.get(MediaScreenBlock.PLAYING) ? 7 : 0;
			})
	);*/

	public static final DisplayFrameBlock DISPLAY_FRAME = new DisplayFrameBlock(
		AbstractBlock.Settings.create()
			.strength(3.0f)
			.nonOpaque()
	);

	public static void register() {
		//Registry.register(Registries.BLOCK, id("media_screen"), MEDIA_SCREEN);
		//Registry.register(Registries.ITEM, id("media_screen"),
		//	new BlockItem(MEDIA_SCREEN, new Item.Settings()));

		Registry.register(Registries.BLOCK, id("display_frame"), DISPLAY_FRAME);
		Registry.register(Registries.ITEM, id("display_frame"),
			new BlockItem(DISPLAY_FRAME, new Item.Settings()));

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			//entries.add(MEDIA_SCREEN.asItem());
			entries.add(DISPLAY_FRAME.asItem());
		});
	}

	private static Identifier id(String path) {
		return Identifier.of(MediaScreenMod.MOD_ID, path);
	}
}
