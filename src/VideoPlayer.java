package com.mediascreen.client;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class VideoPlayer {
	private final String streamUrl;
	private final Consumer<String> errorCallback;
	private final int targetFps;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private Thread decodeThread;

	private VideoTexture texture;
	private volatile int videoWidth;
	private volatile int videoHeight;

	private Process videoProcess;
	private Process audioProcess;

	private byte[][] frameBuffers;

	private final CopyOnWriteArrayList<String> debugLog = new CopyOnWriteArrayList<>();

	private volatile float currentFps = 0f;
	private int fpsFrameCount = 0;
	private long fpsLastTimeNs = System.nanoTime();

	private volatile boolean failed = false;
	private volatile String failureReason = null;

	public VideoPlayer(String streamUrl, int width, int height, Consumer<String> errorCallback) {
		this(streamUrl, width, height, 36, errorCallback);
	}

	public VideoPlayer(String streamUrl, int width, int height, int targetFps, Consumer<String> errorCallback) {
		this.streamUrl = streamUrl;
		this.errorCallback = errorCallback;
		this.targetFps = Math.max(33, Math.min(144, targetFps));
		this.texture = new VideoTexture();
		this.videoWidth = 854;
		this.videoHeight = 480;
	}

	public void start() {
		running.set(true);
		decodeThread = new Thread(this::decodeLoop, "MediaScreen-VideoPlayer");
		decodeThread.setDaemon(true);
		decodeThread.start();
	}

	public void stop() {
		running.set(false);
		destroyProcessTree(videoProcess);
		videoProcess = null;
		destroyProcessTree(audioProcess);
		audioProcess = null;
		if (decodeThread != null) {
			try { decodeThread.join(2000); } catch (InterruptedException e) { decodeThread.interrupt(); }
		}
		frameBuffers = null;

		MinecraftClient.getInstance().execute(() -> {
			if (texture != null) {
				texture.close();
				texture = null;
			}
		});
	}

	public boolean isRunning() { return running.get(); }

	private void addLog(String line) {
		debugLog.add(line);
		MediaScreenLogger.log(line);
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
			addLog("FFmpeg: " + ffmpeg);
			addLog("URL: " + streamUrl);
			startVlcAudio();
			if (audioProcess != null) {
				addLog("Buffering VLC audio before video...");
				Thread.sleep(900);
			}

			ProcessBuilder pb = new ProcessBuilder(
				ffmpeg,
				"-re",
				"-fflags", "nobuffer",
				"-flags", "low_delay",
				"-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5",
				"-i", streamUrl,
				"-loglevel", "quiet",
				"-map", "0:v:0",
				"-vf", "scale=854:480,format=rgb24",
				"-f", "rawvideo",
				"-pix_fmt", "rgb24",
				"-fps_mode", "cfr",
				"-r", String.valueOf(targetFps),
				"pipe:1"
			);
			pb.redirectError(ProcessBuilder.Redirect.DISCARD);
			pb.redirectOutput(ProcessBuilder.Redirect.PIPE);

			addLog("Starting FFmpeg video at " + targetFps + " FPS...");
			videoProcess = pb.start();

			boolean firstFrame = true;
			int writeIdx = 0;
			frameBuffers = new byte[4][frameSize];
			boolean normalEnd = false;
			
			InputStream in = videoProcess.getInputStream();
			while (running.get()) {
				byte[] curBuf = frameBuffers[writeIdx];
int totalRead = 0;
			int consecutiveZeroReads = 0;
			boolean eof = false;
			
			while (totalRead < frameSize && running.get()) {
				int bytesRead = in.read(curBuf, totalRead, frameSize - totalRead);
				if (bytesRead == -1) { 
					eof = true; 
					break;
				}
				if (bytesRead == 0) {
					consecutiveZeroReads++;
					if (consecutiveZeroReads > 100) {
						addLog("No data after 100 tries, breaking");
						break;
					}
					try { Thread.sleep(10); } catch (InterruptedException ignored) {}
					continue;
				}
				consecutiveZeroReads = 0;
				totalRead += bytesRead;
			}
				
				if (totalRead == frameSize && running.get()) {
					byte[] frameCopy = new byte[frameSize];
					System.arraycopy(curBuf, 0, frameCopy, 0, frameSize);
					if (texture != null) {
						if (firstFrame) {
							addLog("Uploading first frame...");
							texture.uploadFrame(frameCopy, videoWidth, videoHeight, true);
						} else {
							texture.updateFrame(frameCopy, videoWidth, videoHeight);
						}
					}
					firstFrame = false;
					writeIdx = (writeIdx + 1) % frameBuffers.length;

					fpsFrameCount++;
					long fpsNow = System.nanoTime();
					if (fpsNow - fpsLastTimeNs >= 1_000_000_000L) {
						currentFps = (float)(fpsFrameCount * 1_000_000_000L) / (fpsNow - fpsLastTimeNs);
						fpsFrameCount = 0;
						fpsLastTimeNs = fpsNow;
						addLog("FPS: " + currentFps);
					}
				} else if (totalRead > 0) {
					addLog("Partial frame: " + totalRead + "/" + frameSize);
				}
				
				if (eof) {
					normalEnd = running.get();
					if (!normalEnd) addLog("Stream stopped by user");
					else addLog("EOF reached, frameSize: " + frameSize + ", read: " + totalRead);
					break;
				}
			}

			if (normalEnd) {
				addLog("Stream ended normally");
			}
		} catch (Exception e) {
			failed = true;
			failureReason = e.getMessage();
			addLog("ERROR: " + e.getMessage());
			e.printStackTrace();
			if (errorCallback != null && running.get()) errorCallback.accept("Error: " + e.getMessage());
		} finally {
			running.set(false);
			destroyProcessTree(videoProcess);
			destroyProcessTree(audioProcess);
			frameBuffers = null;
		}
	}

	private void startVlcAudio() {
		try {
			Optional<String> vlcPath = VLCManager.getVlcPath();
			if (vlcPath.isEmpty()) {
				addLog("VLC not found - audio disabled");
				return;
			}

			ProcessBuilder audioPb = new ProcessBuilder(
				vlcPath.get(),
				"--intf", "dummy",
				"--dummy-quiet",
				"--no-video",
				"--vout=dummy",
				"--no-embedded-video",
				"--no-video-deco",
				"--no-video-title-show",
				"--no-one-instance",
				"--no-playlist-enqueue",
				"--quiet",
				"--network-caching=350",
				"--file-caching=350",
				"--play-and-exit",
				streamUrl,
				"vlc://quit"
			);
			audioPb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			audioPb.redirectError(ProcessBuilder.Redirect.DISCARD);
			audioProcess = audioPb.start();
			addLog("VLC audio started");
		} catch (Exception e) {
			addLog("VLC audio failed: " + e.getMessage());
		}
	}

	private void destroyProcessTree(Process process) {
		if (process == null) return;
		ProcessHandle handle = process.toHandle();
		handle.descendants().forEach(child -> {
			try { child.destroyForcibly(); } catch (Exception ignored) {}
		});
		try { handle.destroyForcibly(); } catch (Exception ignored) {}
		try { process.waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
	}

	public VideoTexture getTexture() { return texture; }
	public int getVideoWidth() { return videoWidth; }
	public int getVideoHeight() { return videoHeight; }
	public float getFps() { return currentFps; }
	public boolean hasFailed() { return failed; }
	public String getFailureReason() { return failureReason; }
}
