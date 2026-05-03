package com.mediascreen.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FFmpegManager {

	private static final String FFMPEG_ZIP_URL = "https://github.com/GyanD/codexffmpeg/releases/download/8.0/ffmpeg-8.0-full_build.zip";
	private static Path ffmpegPath;
	private static volatile boolean installing = false;

	public static synchronized String getFFmpegPath() {
		String os = System.getProperty("os.name").toLowerCase();
		boolean isWindows = os.contains("windows");

		if (!isWindows) {
			return "ffmpeg";
		}

		if (ffmpegPath != null && Files.exists(ffmpegPath)) return ffmpegPath.toString();

		Path gameDir = FabricLoader.getInstance().getGameDir();
		Path mediaDir = gameDir.resolve("mediascreen");
		try { Files.createDirectories(mediaDir); } catch (IOException ignored) {}

		Path[] candidates = {
			mediaDir.resolve("ffmpeg.exe"),
			mediaDir.resolve("ffmpeg-8.0-full_build").resolve("bin").resolve("ffmpeg.exe")
		};
		for (Path p : candidates) {
			if (Files.exists(p)) {
				ffmpegPath = p;
				return p.toString();
			}
		}
		return null;
	}

	public static boolean isInstalled() {
		String os = System.getProperty("os.name").toLowerCase();
		if (!os.contains("windows")) return true;
		return getFFmpegPath() != null;
	}

	public static boolean isInstalling() {
		return installing;
	}

	public static CompletableFuture<Void> installFFmpeg(Consumer<String> statusUpdater) {
		return CompletableFuture.runAsync(() -> {
			String os = System.getProperty("os.name").toLowerCase();
			if (!os.contains("windows")) {
				statusUpdater.accept("FFmpeg install skipped (Mac/Linux please use APT/Brew)");
				return;
			}

			synchronized (FFmpegManager.class) {
				if (isInstalled()) return;
				installing = true;
			}
			try {
				Path gameDir = FabricLoader.getInstance().getGameDir();
				Path mediaDir = gameDir.resolve("mediascreen");
				Files.createDirectories(mediaDir);

				Path zipPath = mediaDir.resolve("ffmpeg.zip");

				URL url = new java.net.URI(FFMPEG_ZIP_URL).toURL();
				URLConnection connection = url.openConnection();
				connection.connect();
				int fileSize = connection.getContentLength();

				statusUpdater.accept("Downloading FFmpeg...");

				try (ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream());
					 FileOutputStream fos = new FileOutputStream(zipPath.toFile())) {

					byte[] buffer = new byte[65536];
					long totalRead = 0;
					long startTime = System.currentTimeMillis();
					int read;
					while ((read = rbc.read(ByteBuffer.wrap(buffer))) != -1) {
						fos.getChannel().write(ByteBuffer.wrap(buffer, 0, read));
						totalRead += read;
						if (fileSize > 0) {
							int pct = (int) ((totalRead * 100) / fileSize);
							double mb = totalRead / (1024.0 * 1024.0);
							double totalMB = fileSize / (1024.0 * 1024.0);
							double speed = (totalRead / 1024.0) / ((System.currentTimeMillis() - startTime) / 1000.0);
							DecimalFormat df = new DecimalFormat("#.#");
							statusUpdater.accept("Downloading FFmpeg: " + df.format(mb) + "/" + df.format(totalMB) + " MB (" + df.format(speed) + " KB/s) " + pct + "%");
						}
					}
				}

				statusUpdater.accept("Extracting FFmpeg...");

				try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
					ZipEntry entry;
					byte[] buffer = new byte[65536];
					int fileCount = 0;
					while ((entry = zis.getNextEntry()) != null) {
						Path outPath = mediaDir.resolve(entry.getName());
						if (entry.isDirectory()) {
							Files.createDirectories(outPath);
						} else {
							Files.createDirectories(outPath.getParent());
							try (OutputStream osStream = Files.newOutputStream(outPath)) {
								int len;
								while ((len = zis.read(buffer)) > 0) {
									osStream.write(buffer, 0, len);
								}
							}
							fileCount++;
							if (fileCount % 10 == 0) {
								statusUpdater.accept("Extracting FFmpeg... (" + fileCount + " files)");
							}
						}
						zis.closeEntry();
					}
				}

				Files.deleteIfExists(zipPath);

				String found = getFFmpegPath();
				if (found == null) {
					throw new IOException("Could not find ffmpeg.exe after extraction.");
				}

				statusUpdater.accept("FFmpeg ready");
			} catch (Exception e) {
				throw new RuntimeException("Failed to install FFmpeg: " + e.getMessage(), e);
			} finally {
				synchronized (FFmpegManager.class) {
					installing = false;
				}
			}
		});
	}
}
