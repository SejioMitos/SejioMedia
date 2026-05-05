package com.mediascreen.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class YtDlpManager {

	private static final String YTDLP_RELEASE_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
	private static Path ytDlpPath;
	private static volatile boolean downloading = false;

	public static synchronized Path getYtDlpPath() {
		if (ytDlpPath != null) return ytDlpPath;

		Path gameDir = FabricLoader.getInstance().getGameDir();
		Path mediaDir = gameDir.resolve("mediascreen");
		try { Files.createDirectories(mediaDir); } catch (IOException ignored) {}

		String os = System.getProperty("os.name").toLowerCase();
		String fileName = os.contains("windows") ? "yt-dlp.exe" : "yt-dlp";
		ytDlpPath = mediaDir.resolve(fileName);
		return ytDlpPath;
	}

	public static boolean isDownloaded() {
		return getYtDlpPath().toFile().exists();
	}

	public static CompletableFuture<Void> forceUpdateYtDlp(Consumer<String> statusUpdater) {
		return CompletableFuture.runAsync(() -> {
			try {
				Files.deleteIfExists(getYtDlpPath());
				downloadYtDlp(statusUpdater).join();
			} catch (IOException e) {
				throw new RuntimeException("Failed to delete old yt-dlp", e);
			}
		});
	}

	public static CompletableFuture<Void> downloadYtDlp(Consumer<String> statusUpdater) {
		return CompletableFuture.runAsync(() -> {
			synchronized (YtDlpManager.class) { downloading = true; }
			Path target = getYtDlpPath();
			if (target.toFile().exists()) return;

			String os = System.getProperty("os.name").toLowerCase();
			String downloadUrl = YTDLP_RELEASE_URL + (os.contains("windows") ? "yt-dlp.exe" : "yt-dlp_macos");
			if (os.contains("linux")) downloadUrl = YTDLP_RELEASE_URL + "yt-dlp_linux";

			statusUpdater.accept("Downloading yt-dlp...");
			MediaScreenLogger.log("Downloading yt-dlp...");

			try {
				URL url = new URI(downloadUrl).toURL();
				URLConnection connection = url.openConnection();
				connection.connect();
				int fileSize = connection.getContentLength();

				try (ReadableByteChannel rbc = Channels.newChannel(url.openStream());
					 FileOutputStream fos = new FileOutputStream(target.toFile())) {

					byte[] buffer = new byte[65536];
					long totalRead = 0;
					long startTime = System.currentTimeMillis();
					int read;
					while ((read = rbc.read(java.nio.ByteBuffer.wrap(buffer))) != -1) {
						fos.getChannel().write(java.nio.ByteBuffer.wrap(buffer, 0, read));
						totalRead += read;
						if (fileSize > 0) {
							int pct = (int) ((totalRead * 100) / fileSize);
							double mb = totalRead / (1024.0 * 1024.0);
							double totalMB = fileSize / (1024.0 * 1024.0);
							double speed = (totalRead / 1024.0) / ((System.currentTimeMillis() - startTime) / 1000.0);
							DecimalFormat df = new DecimalFormat("#.#");
							statusUpdater.accept("Downloading yt-dlp: " + df.format(mb) + "/" + df.format(totalMB) + " MB (" + df.format(speed) + " KB/s) " + pct + "%");
						MediaScreenLogger.log("Downloading yt-dlp: " + df.format(mb) + "/" + df.format(totalMB) + " MB (" + df.format(speed) + " KB/s) " + pct + "%");
						}
					}
				}

				if (!os.contains("windows")) {
					try {
						Set<PosixFilePermission> perms = new HashSet<>();
						perms.add(PosixFilePermission.OWNER_READ);
						perms.add(PosixFilePermission.OWNER_WRITE);
						perms.add(PosixFilePermission.OWNER_EXECUTE);
						Files.setPosixFilePermissions(target, perms);
					} catch (UnsupportedOperationException e) {
						target.toFile().setExecutable(true);
					}
				}

				statusUpdater.accept("yt-dlp ready");
				MediaScreenLogger.log("yt-dlp ready");
			} catch (Exception e) {
				throw new RuntimeException("Failed to download yt-dlp: " + e.getMessage(), e);
			} finally {
				synchronized (YtDlpManager.class) { downloading = false; }
			}
		});
	}

	public static boolean isDownloading() { return downloading; }

	public static CompletableFuture<StreamInfo> resolveStreamUrl(String youtubeUrl, Consumer<String> statusUpdater, Consumer<StreamInfo> onSuccess, Consumer<String> onError) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				if (!isDownloaded()) {
					downloadYtDlp(statusUpdater).join();
					Thread.sleep(1000);
				}

				Path ytDlp = getYtDlpPath();
				Process infoProcess = null;
				int attempts = 0;
				
				while (attempts < 5) {
					try {
						ProcessBuilder infoPb = new ProcessBuilder(
						ytDlp.toString(),
						"--no-playlist",
						"--print", "%(url)s",
						"--print", "%(width)s",
						"--print", "%(height)s",
						"--format", "best[height<=720]/best",
						"--no-warnings",
						 "--extractor-args", "youtube:player_client=android",
						youtubeUrl
					);
						infoPb.redirectError(ProcessBuilder.Redirect.PIPE);
						infoProcess = infoPb.start();
						break;
					} catch (IOException e) {
						attempts++;
						if (attempts >= 5) throw e;
						Thread.sleep(500);
					}
				}

				// Read stderr in background to prevent process blocking
				StringBuilder errSb = new StringBuilder();
				Process finalInfoProcess = infoProcess;
				Thread errThread = new Thread(() -> {
					try (BufferedReader er = new BufferedReader(new InputStreamReader(finalInfoProcess.getErrorStream()))) {
						String l;
						while ((l = er.readLine()) != null) errSb.append(l).append("\n");
					} catch (IOException ignored) {}
				});
				errThread.setDaemon(true);
				errThread.start();

				// Read stdout line by line — each --print arg produces one line
				String directUrl = "";
				int width = 1280;
				int height = 720;
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(infoProcess.getInputStream()))) {
					String urlLine = reader.readLine();
					String widthLine = reader.readLine();
					String heightLine = reader.readLine();
					if (urlLine != null) directUrl = urlLine.trim();
					if (widthLine != null && !widthLine.trim().equals("NA")) {
						try { width = Integer.parseInt(widthLine.trim()); } catch (NumberFormatException ignored) {}
					}
					if (heightLine != null && !heightLine.trim().equals("NA")) {
						try { height = Integer.parseInt(heightLine.trim()); } catch (NumberFormatException ignored) {}
					}
				}

				errThread.join(3000);
				int exitCode = infoProcess.waitFor();
				String err = errSb.toString();

				if (exitCode != 0 || directUrl.isEmpty()) {
					MediaScreenLogger.log("yt-dlp stderr: " + err);
					if (err.contains("429") || err.contains("HTTP Error 429")) {
						throw new RuntimeException("YouTube rate limited (429). Try again in a moment.");
					}
					throw new RuntimeException("yt-dlp failed: " + err.trim());
				}

				MediaScreenLogger.log("Resolved URL: " + directUrl.substring(0, Math.min(80, directUrl.length())) + "...");
				MediaScreenLogger.log("Resolution: " + width + "x" + height);

				return new StreamInfo(directUrl, width, height);
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage(), e);
			}
		}).thenApply(info -> {
			onSuccess.accept(info);
			return info;
		}).exceptionally(e -> {
			onError.accept(e.getMessage());
			return null;
		});
	}

	public static class StreamInfo {
		public final String url;
		public final int width;
		public final int height;
		public StreamInfo(String url, int width, int height) {
			this.url = url; this.width = width; this.height = height;
		}
	}
}
