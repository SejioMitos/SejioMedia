package com.mediascreen.client;

import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class VideoManager {
	private static final Map<BlockPos, VideoPlayer> activePlayers = new HashMap<>();

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(VideoManager::stopAll, "MediaScreen-Shutdown"));
	}

	public static synchronized void startPlayer(BlockPos pos, VideoPlayer player) {
		stopPlayer(pos);
		try { Thread.sleep(500); } catch (InterruptedException ignored) {}
		activePlayers.put(pos, player);
		player.start();
	}

	public static synchronized void stopPlayer(BlockPos pos) {
		VideoPlayer player = activePlayers.remove(pos);
		if (player != null) {
			player.stop();
		}
		System.gc();
	}

	public static synchronized VideoPlayer getPlayer(BlockPos pos) {
		return activePlayers.get(pos);
	}

	public static synchronized java.util.Collection<VideoPlayer> getAllPlayers() {
		return java.util.List.copyOf(activePlayers.values());
	}

	public static synchronized void cleanup() {
		activePlayers.entrySet().removeIf(e -> !e.getValue().isRunning());
	}

	public static synchronized void stopAll() {
		for (VideoPlayer player : activePlayers.values()) {
			player.stop();
		}
		activePlayers.clear();
	}
}
