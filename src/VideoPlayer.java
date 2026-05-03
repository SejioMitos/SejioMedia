package com.mediascreen.client;

import net.minecraft.client.MinecraftClient;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class VideoPlayer {

	private final String streamUrl;
	private final Consumer<String> errorCallback;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private Thread decodeThread;

	private VideoTexture texture;
	private volatile int videoWidth;
	private volatile int videoHeight;

	private Process videoProcess;
	private Process audioProcess;
	private SourceDataLine audioLine;

	private final CopyOnWriteArrayList<String> debugLog = new CopyOnWriteArrayList<>();

	public VideoPlayer(String streamUrl, int width, int height, Consumer<String> errorCallback) {
		this.streamUrl = streamUrl;
		this.errorCallback = errorCallback;
		this.texture = new VideoTexture();
		this.videoWidth = width;
		this.videoHeight = height;
	}

	public void start() {
		running.set(true);
		decodeThread = new Thread(this::decodeLoop, "MediaScreen-VideoPlayer");
		decodeThread.setDaemon(true);
		decodeThread.start();
	}

	public void stop() {
		running.set(false);
		if (videoProcess != null) videoProcess.destroy();
		if (audioProcess != null) audioProcess.destroy();
		if (decodeThread != null) {
			try { decodeThread.join(2000); } catch (InterruptedException e) { decodeThread.interrupt(); }
		}
		if (audioLine != null) { audioLine.stop(); audioLine.close(); audioLine = null; }

		MinecraftClient.getInstance().execute(() -> {
			if (texture != null) {
				texture.close();
				texture = null;
			}
		});
	}

	private void addLog(String line) {
		debugLog.add(line);
		if (debugLog.size() > 100) {
			debugLog.remove(0);
		}
	}

	public java.util.List<String> getDebugLog() {
		return debugLog;
	}

	private void decodeLoop() {
		try {
			String ffmpeg = FFmpegManager.getFFmpegPath();
			if (ffmpeg == null) throw new IOException("FFmpeg not found");

			int frameSize = videoWidth * videoHeight * 3;

			ProcessBuilder videoPb = new ProcessBuilder(
				ffmpeg,
				"-i", streamUrl,
				"-f", "rawvideo",
				"-pix_fmt", "rgb24",
				"-s", videoWidth + "x" + videoHeight,
				"-fps_mode", "cfr",
				"-r", "24",
				"pipe:1"
			);
			videoPb.redirectError(ProcessBuilder.Redirect.PIPE);
			videoPb.redirectOutput(ProcessBuilder.Redirect.PIPE);

			videoProcess = videoPb.start();

			new Thread(this::captureStderr, "MediaScreen-DebugLog").start();
			new Thread(() -> initAudio(ffmpeg), "MediaScreen-AudioInit").start();

			boolean firstFrame = true;
			int writeIdx = 0;
			byte[][] frameBuffers = new byte[2][frameSize];
			try (InputStream in = videoProcess.getInputStream()) {
				while (running.get()) {
					byte[] curBuf = frameBuffers[writeIdx];
					int totalRead = 0;
					boolean eof = false;
					while (totalRead < frameSize && running.get()) {
						int bytesRead = in.read(curBuf, totalRead, frameSize - totalRead);
						if (bytesRead == -1) { eof = true; break; }
						totalRead += bytesRead;
					}
					if (totalRead == frameSize && running.get()) {
						final byte[] frame = curBuf;
						final boolean isFirst = firstFrame;
						firstFrame = false;
						writeIdx = 1 - writeIdx;
						MinecraftClient.getInstance().execute(() -> {
							if (running.get() && texture != null) {
								if (isFirst) {
									texture.uploadFrame(frame, videoWidth, videoHeight, isFirst);
								} else {
									texture.updateFrame(frame, videoWidth, videoHeight);
								}
							}
						});
					}
					if (eof) break;
				}
			}
		} catch (Exception e) {
			addLog("ERROR: " + e.getMessage());
			if (errorCallback != null && running.get()) errorCallback.accept("Error: " + e.getMessage());
		} finally {
			running.set(false);
			if (videoProcess != null) videoProcess.destroy();
			if (audioProcess != null) audioProcess.destroy();
			if (audioLine != null) { audioLine.stop(); audioLine.close(); audioLine = null; }
		}
	}

	private void captureStderr() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(videoProcess.getErrorStream()))) {
			String line;
			while (running.get() && (line = reader.readLine()) != null) {
				final String logLine = line;
				MinecraftClient.getInstance().execute(() -> addLog(logLine));
			}
		} catch (IOException e) {
			addLog("stderr reader closed");
		}
	}

	private void initAudio(String ffmpeg) {
		try {
			ProcessBuilder audioPb = new ProcessBuilder(
				ffmpeg,
				"-i", streamUrl,
				"-f", "s16le",
				"-acodec", "pcm_s16le",
				"-ar", "44100",
				"-ac", "2",
				"pipe:1"
			);
			audioPb.redirectError(ProcessBuilder.Redirect.DISCARD);
			audioPb.redirectOutput(ProcessBuilder.Redirect.PIPE);
			audioProcess = audioPb.start();

			AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
			if (javax.sound.sampled.AudioSystem.isLineSupported(info)) {
				audioLine = (SourceDataLine) javax.sound.sampled.AudioSystem.getLine(info);
				audioLine.open(format);
				audioLine.start();
				new Thread(() -> {
					try (InputStream in = audioProcess.getInputStream()) {
						byte[] buf = new byte[8192];
						int read;
						while (running.get() && (read = in.read(buf)) != -1) audioLine.write(buf, 0, read);
					} catch (Exception ignored) {}
				}, "MediaScreen-Audio").start();
			}
		} catch (Exception e) { audioLine = null; }
	}

	public VideoTexture getTexture() { return texture; }
	public int getVideoWidth() { return videoWidth; }
	public int getVideoHeight() { return videoHeight; }
}
