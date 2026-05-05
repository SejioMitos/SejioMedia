package com.mediascreen.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class VLCManager {
	private static final String VLC_ZIP_URL = "https://get.videolan.org/vlc/last/win64/vlc-3.0.23-win64.zip";
	private static Path vlcPath;
	private static volatile boolean installing = false;

	public static synchronized Optional<String> getVlcPath() {
		if (vlcPath != null && Files.exists(vlcPath)) {
			return Optional.of(vlcPath.toString());
		}

		String os = System.getProperty("os.name").toLowerCase();
		if (!os.contains("windows")) {
			return Optional.of("vlc");
		}

		Path mediaDir = FabricLoader.getInstance().getGameDir().resolve("mediascreen");
		try { Files.createDirectories(mediaDir); } catch (IOException ignored) {}

		Path[] candidates = {
			mediaDir.resolve("vlc").resolve("vlc.exe"),
			mediaDir.resolve("vlc-3.0.23").resolve("vlc.exe"),
			pathFromEnv("ProgramFiles", "VideoLAN\\VLC\\vlc.exe"),
			pathFromEnv("ProgramFiles(x86)", "VideoLAN\\VLC\\vlc.exe"),
			pathFromEnv("LOCALAPPDATA", "Programs\\VideoLAN\\VLC\\vlc.exe")
		};

		for (Path candidate : candidates) {
			if (candidate != null && Files.exists(candidate)) {
				vlcPath = candidate;
				return Optional.of(candidate.toString());
			}
		}
		return Optional.empty();
	}

	public static boolean isInstalled() {
		return getVlcPath().isPresent();
	}

	public static boolean isInstalling() {
		return installing;
	}

	public static CompletableFuture<Void> installVlc(Consumer<String> statusUpdater) {
		return CompletableFuture.runAsync(() -> {
			String os = System.getProperty("os.name").toLowerCase();
			if (!os.contains("windows")) {
				statusUpdater.accept("VLC install skipped (Mac/Linux please install VLC)");
				MediaScreenLogger.log("VLC install skipped (Mac/Linux please install VLC)");
				return;
			}

			synchronized (VLCManager.class) {
				if (isInstalled()) return;
				installing = true;
			}

			try {
				Path mediaDir = FabricLoader.getInstance().getGameDir().resolve("mediascreen");
				Files.createDirectories(mediaDir);
				Path zipPath = mediaDir.resolve("vlc.zip");

				URL url = new URI(VLC_ZIP_URL).toURL();
				URLConnection connection = url.openConnection();
				connection.connect();
				int fileSize = connection.getContentLength();

				statusUpdater.accept("Downloading VLC...");
				MediaScreenLogger.log("Downloading VLC...");

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
							statusUpdater.accept("Downloading VLC: " + df.format(mb) + "/" + df.format(totalMB) + " MB (" + df.format(speed) + " KB/s) " + pct + "%");
						}
					}
				}

				statusUpdater.accept("Extracting VLC...");
				MediaScreenLogger.log("Extracting VLC...");
				extractVlc(zipPath, mediaDir);
				Files.deleteIfExists(zipPath);

				if (getVlcPath().isEmpty()) {
					throw new IOException("Could not find vlc.exe after extraction.");
				}

				statusUpdater.accept("VLC ready");
				MediaScreenLogger.log("VLC ready");
			} catch (Exception e) {
				throw new RuntimeException("Failed to install VLC: " + e.getMessage(), e);
			} finally {
				synchronized (VLCManager.class) {
					installing = false;
				}
			}
		});
	}

	private static void extractVlc(Path zipPath, Path mediaDir) throws IOException {
		try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
			ZipEntry entry;
			byte[] buffer = new byte[65536];
			while ((entry = zis.getNextEntry()) != null) {
				String name = entry.getName();
				String normalized = name.replace('\\', '/');
				int slash = normalized.indexOf('/');
				String relative = slash >= 0 ? normalized.substring(slash + 1) : normalized;
				if (relative.isEmpty()) {
					zis.closeEntry();
					continue;
				}

				Path outPath = mediaDir.resolve("vlc").resolve(relative).normalize();
				if (!outPath.startsWith(mediaDir.resolve("vlc").normalize())) {
					throw new IOException("Blocked unsafe VLC zip entry: " + name);
				}

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
				}
				zis.closeEntry();
			}
		}
	}

	private static Path pathFromEnv(String env, String suffix) {
		String value = System.getenv(env);
		if (value == null || value.isBlank()) return null;
		return Path.of(value).resolve(suffix);
	}
}
