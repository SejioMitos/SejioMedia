package com.mediascreen.client;

import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class VideoManager {
	private static final Map<BlockPos, VideoPlayer> activePlayers = new HashMap<>();

	public static void startPlayer(BlockPos pos, VideoPlayer player) {
		stopPlayer(pos);
		activePlayers.put(pos, player);
		player.start();
	}

	public static void stopPlayer(BlockPos pos) {
		VideoPlayer player = activePlayers.remove(pos);
		if (player != null) {
			player.stop();
		}
		System.gc();
	}

	public static VideoPlayer getPlayer(BlockPos pos) {
		return activePlayers.get(pos);
	}

	public static java.util.Collection<VideoPlayer> getAllPlayers() {
		return activePlayers.values();
	}

	public static void cleanup() {
		activePlayers.entrySet().removeIf(e -> !e.getValue().isRunning());
	}
}
