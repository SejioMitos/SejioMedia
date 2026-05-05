package com.mediascreen.client;

import com.mediascreen.block.MediaScreenBlockEntity;
import com.mediascreen.block.DisplayFrameBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class MediaScreenScreen extends Screen {

	private static final java.util.Map<net.minecraft.util.math.BlockPos, String> URL_CACHE = new java.util.HashMap<>();
	private static int targetFps = 36;
	private static int fpsCap = 60;

	private int panelW = 760;
	private int panelH = 430;
	private int pad = 20;
	private int topH = 38;
	private int previewH = 214;
	private int logH = 52;

	private final net.minecraft.block.entity.BlockEntity blockEntity;

	private TextFieldWidget urlField;

	private int panelX, panelY;
	private int urlX, urlY, urlW, urlH;
	private int playX, playY, pauseX, pauseY;
	private int searchX, searchY, searchW, searchH;
	private int previewX, previewY, previewW;
	private int logX, logY, logW;
	private int targetSliderX, targetSliderY, capSliderX, capSliderY, sliderW;
	private int activeSlider = 0;

	private VideoPlayer currentPlayer;

	private final List<String> debugLog = new ArrayList<>();
	private int logScroll = 0;
	private long playbackRequestId = 0;
	private boolean playbackStarting = false;

	public MediaScreenScreen(MediaScreenBlockEntity blockEntity) {
		super(Text.literal("MediaScreen"));
		this.blockEntity = blockEntity;
	}

	public MediaScreenScreen(DisplayFrameBlockEntity blockEntity) {
		super(Text.literal("DisplayFrame"));
		this.blockEntity = blockEntity;
	}

	@Override
	protected void init() {
		float scale = Math.min(1.0f, Math.min(width / 790.0f, height / 445.0f));
		scale = Math.max(0.65f, scale);
		panelW = Math.min(width - 8, (int) (790 * scale));
		panelH = Math.min(height - 8, (int) (445 * scale));
		pad = Math.max(12, (int) (20 * scale));
		topH = Math.max(30, (int) (38 * scale));
		previewH = Math.max(120, (int) (214 * scale));
		logH = Math.max(44, (int) (52 * scale));

		layoutWidgets();

		urlField = new TextFieldWidget(textRenderer, urlX + 42, urlY + (urlH - 12) / 2, urlW - 78, 16, Text.literal("URL"));
		urlField.setMaxLength(1024);
		urlField.setDrawsBackground(false);
		String cached = URL_CACHE.get(getBlockEntity().getPos());
		String saved = cached != null ? cached : getYoutubeUrl();
		urlField.setText(saved);
		urlField.setChangedListener(url -> URL_CACHE.put(getBlockEntity().getPos(), url));
		urlField.setFocused(true);

		addDrawableChild(urlField);
		addSelectableChild(urlField);

		checkFFmpeg();
	}

	private void layoutWidgets() {
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;

		urlX = panelX + pad;
		urlY = panelY + 10;
		urlW = Math.max(220, (int) (368 * (panelW / 790.0f)));
		urlH = topH;

		searchW = Math.max(96, (int) (114 * (panelW / 790.0f)));
		searchH = topH;
		searchX = panelX + panelW - pad - searchW;
		searchY = urlY;

		pauseX = searchX - 28;
		playX = pauseX - 28;
		playY = pauseY = urlY + (urlH - 16) / 2;

		previewX = panelX + pad;
		previewY = panelY + 67;
		previewW = panelW - pad * 2;

		logX = panelX + pad;
		logW = Math.min((int) (421 * (panelW / 790.0f)), panelW - pad * 2);
		logY = panelY + panelH - pad - logH;
		sliderW = Math.max(150, panelW - pad * 3 - logW);
		targetSliderX = logX + logW + pad;
		targetSliderY = logY + 5;
		capSliderX = targetSliderX;
		capSliderY = targetSliderY + 24;

		if (urlField != null) {
			urlField.setX(urlX + 42);
			urlField.setY(urlY + (urlH - 12) / 2);
			urlField.setWidth(urlW - 78);
			urlField.setHeight(16);
		}
	}

	private void checkFFmpeg() {
		String path = FFmpegManager.getFFmpegPath();
		if (path == null) {
			log("FFMPEG NOT FOUND - STARTING DOWNLOAD");
			if (!FFmpegManager.isInstalling()) {
				FFmpegManager.installFFmpeg(status -> {
					log(status);
				}).exceptionally(e -> {
					log("FFmpeg install failed: " + e.getMessage());
					return null;
				});
			}
		}
	}

	private void log(String msg) {
		debugLog.add(msg);
		MediaScreenLogger.log(msg);
	}

	private void onPlay() {
		if (playbackStarting) {
			log("Playback is already loading...");
			return;
		}
		String url = urlField.getText().trim();
		if (url.isEmpty()) { log("Error: No URL"); return; }
		playbackStarting = true;
		long requestId = ++playbackRequestId;

		// Check if it's a YouTube URL and normalize it
		if (YouTubeUrlHandler.isYouTubeUrl(url)) {
			String normalizedUrl = YouTubeUrlHandler.normalizeYouTubeUrl(url);
			if (normalizedUrl != null) {
				url = normalizedUrl;
				log("YouTube video detected - converting to standard format");
				urlField.setText(url);
			}
		}

		final String finalUrl = url;
		setYoutubeUrl(finalUrl);
		URL_CACHE.put(getBlockEntity().getPos(), finalUrl);

		VideoManager.stopPlayer(getBlockEntity().getPos());

		if (YtDlpManager.isDownloading() || FFmpegManager.isInstalling() || VLCManager.isInstalling()) {
			log("Downloads in progress, please wait...");
			playbackStarting = false;
			return;
		}

		if (!YtDlpManager.isDownloaded()) {
			log("Downloading yt-dlp first...");
			YtDlpManager.downloadYtDlp(s -> log(s))
				.thenRun(() -> MinecraftClient.getInstance().execute(() -> {
					if (requestId != playbackRequestId) return;
					if (!FFmpegManager.isInstalled()) {
						startFFmpegInstall(finalUrl, requestId);
					} else if (!VLCManager.isInstalled()) {
						startVlcInstall(finalUrl, requestId);
					} else {
						startPlayback(finalUrl, requestId);
					}
				})).exceptionally(e -> {
					log("yt-dlp download failed: " + e.getMessage());
					playbackStarting = false;
					return null;
				});
			return;
		}

		if (!FFmpegManager.isInstalled()) {
			startFFmpegInstall(finalUrl, requestId);
			return;
		}
		if (!VLCManager.isInstalled()) {
			startVlcInstall(finalUrl, requestId);
			return;
		}

		startPlayback(finalUrl, requestId);
	}

	private void startFFmpegInstall(String url, long requestId) {
		log("Installing FFmpeg (first time)...");
		FFmpegManager.installFFmpeg(status -> {
			log(status);
			if (status.equals("FFmpeg ready")) {
				if (requestId != playbackRequestId) return;
				if (!VLCManager.isInstalled()) {
					startVlcInstall(url, requestId);
				} else {
					startPlayback(url, requestId);
				}
			}
		}).exceptionally(e -> {
			log("FFmpeg install failed: " + e.getMessage());
			playbackStarting = false;
			return null;
		});
	}

	private void startVlcInstall(String url, long requestId) {
		log("Installing VLC (first time)...");
		VLCManager.installVlc(status -> {
			log(status);
			if (status.equals("VLC ready")) {
				if (requestId != playbackRequestId) return;
				startPlayback(url, requestId);
			}
		}).exceptionally(e -> {
			log("VLC install failed: " + e.getMessage());
			playbackStarting = false;
			return null;
		});
	}

	private void startPlayback(String url, long requestId) {
		// Provide context-specific messaging
		if (YouTubeUrlHandler.isYouTubeUrl(url)) {
			String videoId = YouTubeUrlHandler.extractVideoId(url);
			log("Processing YouTube video: " + videoId);
		} else {
			log("Resolving URL...");
		}
		
		YtDlpManager.resolveStreamUrl(url,
			s -> log(s),
			info -> {
				MinecraftClient.getInstance().execute(() -> {
					if (requestId != playbackRequestId) return;
					log("Starting playback...");
					VideoPlayer player = new VideoPlayer(info.url, info.width, info.height, getEffectiveFps(), err -> {
						MinecraftClient.getInstance().execute(() -> log("Error: " + err));
					});
					VideoManager.startPlayer(getBlockEntity().getPos(), player);
					log("Playing");
					setPlaying(true);
					playbackStarting = false;
				});
			},
			error -> {
				MinecraftClient.getInstance().execute(() -> {
					if (requestId != playbackRequestId) return;
					log("Error: " + error);
					setPlaying(false);
					playbackStarting = false;
				});
			}
		);
	}

	private void onStop() {
		playbackRequestId++;
		playbackStarting = false;
		VideoManager.stopPlayer(getBlockEntity().getPos());
		setPlaying(false);
		log("Stopped");
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public void removed() {
		super.removed();
		debugLog.clear();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 257 && urlField.isFocused()) {
			onPlay();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (isInside(mouseX, mouseY, searchX, searchY, searchW, searchH)) {
			onPlay();
			return true;
		}
		if (isInside(mouseX, mouseY, playX - 4, playY - 4, 20, 22)) {
			onPlay();
			return true;
		}
		if (isInside(mouseX, mouseY, pauseX - 4, pauseY - 4, 20, 22)) {
			onStop();
			return true;
		}
		if (isInside(mouseX, mouseY, targetSliderX, targetSliderY + 10, sliderW, 12)) {
			activeSlider = 1;
			updateSlider(mouseX);
			return true;
		}
		if (isInside(mouseX, mouseY, capSliderX, capSliderY + 10, sliderW, 12)) {
			activeSlider = 2;
			updateSlider(mouseX);
			return true;
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (activeSlider != 0) {
			updateSlider(mouseX);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		activeSlider = 0;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (isInside(mouseX, mouseY, logX, logY, logW, logH)) {
			int lineH = textRenderer.fontHeight + 2;
			int maxLines = Math.max(1, (logH - 12) / lineH);
			int maxScroll = Math.max(0, debugLog.size() - maxLines);
			logScroll = Math.max(0, Math.min(maxScroll, logScroll - (int) Math.signum(verticalAmount)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		layoutWidgets();

		currentPlayer = VideoManager.getPlayer(getBlockEntity().getPos());
		boolean isPlaying = currentPlayer != null && currentPlayer.isRunning();

		drawPanel(context);
		drawTopBar(context, mouseX, mouseY, delta, isPlaying || playbackStarting);
		drawPreviewArea(context, isPlaying);
		drawDebugLog(context);
		drawFpsSliders(context);

		syncLogFromPlayer();
	}

	private void drawPanel(DrawContext context) {
		context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFFE1E1E1);
	}

	private void drawTopBar(DrawContext context, int mouseX, int mouseY, float delta, boolean isPlaying) {
		context.fill(urlX, urlY, urlX + urlW, urlY + urlH, 0xFF817D7D);
		context.drawText(textRenderer, urlField.getText().isEmpty() ? "URL YOUTUBE VIDEOS GOES HERE" : "", urlX + 48, urlY + 14, 0xFFE8E8E8, false);
		drawEditIcon(context, urlX + urlW - 36, urlY + 11, 0xFF191A20);
		urlField.render(context, mouseX, mouseY, delta);

		drawPlayIcon(context, playX, playY, isPlaying ? 0xFF191A20 : 0xFF303035);
		drawPauseIcon(context, pauseX, pauseY, isPlaying ? 0xFF303035 : 0xFF191A20);

		context.fill(searchX, searchY, searchX + searchW, searchY + searchH, 0xFF4D4A4A);
		context.drawCenteredTextWithShadow(textRenderer, "Search", searchX + searchW / 2 - 10, searchY + 14, 0xFFE9E9E9);
		drawSearchIcon(context, searchX + searchW - 31, searchY + 10, 0xFF191A20);
	}

	private void drawPreviewArea(DrawContext context, boolean isPlaying) {
		context.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF927F7F);

		if (isPlaying && currentPlayer != null && currentPlayer.getTexture() != null) {
			VideoTexture tex = currentPlayer.getTexture();
			if (tex.getWidth() > 0 && tex.getHeight() > 0) {
				int texW = previewW;
				int texH = (int) (texW * ((float) tex.getHeight() / tex.getWidth()));
				if (texH > previewH) {
					texH = previewH;
					texW = (int) (texH * ((float) tex.getWidth() / tex.getHeight()));
				}
				int drawX = previewX + (previewW - texW) / 2;
				int drawY = previewY + (previewH - texH) / 2;
				drawTextureQuad(context, tex, drawX, drawY, texW, texH);
			}
		} else {
			context.drawCenteredTextWithShadow(textRenderer, "Video Preview", previewX + previewW / 2, previewY + previewH / 2 - 4, 0xFFFFFFFF);
		}
	}

	private void drawDebugLog(DrawContext context) {
		context.fill(logX, logY, logX + logW, logY + logH, 0xFF484343);

		int lineH = textRenderer.fontHeight + 2;
		int maxLines = Math.max(1, (logH - 14) / lineH);
		int total = debugLog.size();
		int maxScroll = Math.max(0, total - maxLines);
		logScroll = Math.max(0, Math.min(logScroll, maxScroll));
		int start = Math.max(0, total - maxLines - logScroll);
		if (total == 0) {
			context.drawText(textRenderer, "Debug log that's scrollable", logX + 74, logY + (logH - 8) / 2, 0xFFFFFFFF, false);
			return;
		}
		for (int i = start; i < total && (i - start) < maxLines; i++) {
			String line = debugLog.get(i);
			int y = logY + 7 + (i - start) * lineH;
			int color;
			if (line.contains("ERROR") || line.contains("FAILED")) {
				color = 0xFFe05050;
			} else if (line.contains("Warning") || line.contains("NOT FOUND")) {
				color = 0xFFf0a030;
			} else {
				color = 0xFFEDEDED;
			}
			context.drawText(textRenderer, line, logX + 8, y, color, false);
		}
	}

	private void drawFpsSliders(DrawContext context) {
		drawSlider(context, targetSliderX, targetSliderY, sliderW, "Target FPS", targetFps, 33, 120);
		drawSlider(context, capSliderX, capSliderY, sliderW, "FPS Cap", fpsCap, 33, 144);
		context.drawText(textRenderer, "Using " + getEffectiveFps() + " FPS", targetSliderX, capSliderY + 24, 0xFF303030, false);
	}

	private void drawSlider(DrawContext context, int x, int y, int w, String label, int value, int min, int max) {
		context.drawText(textRenderer, label + ": " + value, x, y, 0xFF303030, false);
		int trackY = y + 14;
		context.fill(x, trackY, x + w, trackY + 4, 0xFF817D7D);
		int knobX = x + Math.round(((value - min) / (float) (max - min)) * w);
		context.fill(knobX - 3, trackY - 4, knobX + 3, trackY + 8, 0xFF191A20);
	}

	private void updateSlider(double mouseX) {
		if (activeSlider == 1) {
			targetFps = sliderValue(mouseX, targetSliderX, sliderW, 33, 120);
			if (targetFps > fpsCap) fpsCap = targetFps;
		} else if (activeSlider == 2) {
			fpsCap = sliderValue(mouseX, capSliderX, sliderW, 33, 144);
			if (fpsCap < targetFps) targetFps = fpsCap;
		}
	}

	private int sliderValue(double mouseX, int x, int w, int min, int max) {
		float t = (float) ((mouseX - x) / Math.max(1.0, w));
		t = Math.max(0f, Math.min(1f, t));
		return min + Math.round(t * (max - min));
	}

	private int getEffectiveFps() {
		return Math.max(33, Math.min(targetFps, fpsCap));
	}

	private boolean isInside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
	}

	private void drawPlayIcon(DrawContext context, int x, int y, int color) {
		context.fill(x, y + 1, x + 2, y + 15, color);
		context.fill(x + 2, y + 3, x + 4, y + 13, color);
		context.fill(x + 4, y + 5, x + 6, y + 11, color);
		context.fill(x + 6, y + 7, x + 8, y + 9, color);
	}

	private void drawPauseIcon(DrawContext context, int x, int y, int color) {
		context.fill(x, y + 1, x + 4, y + 15, color);
		context.fill(x + 9, y + 1, x + 13, y + 15, color);
	}

	private void drawSearchIcon(DrawContext context, int x, int y, int color) {
		context.drawBorder(x, y, 11, 11, color);
		context.fill(x + 9, y + 9, x + 12, y + 12, color);
		context.fill(x + 12, y + 12, x + 15, y + 15, color);
	}

	private void drawEditIcon(DrawContext context, int x, int y, int color) {
		context.fill(x + 9, y, x + 13, y + 4, color);
		context.fill(x + 7, y + 2, x + 11, y + 6, color);
		context.fill(x + 5, y + 4, x + 9, y + 8, color);
		context.fill(x + 3, y + 6, x + 7, y + 10, color);
		context.fill(x + 1, y + 11, x + 5, y + 13, color);
	}

	private void drawTextureQuad(DrawContext context, VideoTexture tex, int x, int y, int w, int h) {
		Identifier texId = tex.getOrRegisterIdentifier("video_gui_" + getBlockEntity().getPos().toShortString());
		if (texId == null) return;
		RenderLayer layer = RenderLayer.getEntityTranslucent(texId);

		var entry = context.getMatrices().peek();
		var vc = context.getVertexConsumers().getBuffer(layer);
		int overlay = net.minecraft.client.render.OverlayTexture.DEFAULT_UV;
		vc.vertex(entry, x, y, 0).color(0xFFFFFFFF).texture(0, 0).overlay(overlay).light(0xF000F0).normal(0f, 0f, 1f);
		vc.vertex(entry, x + w, y, 0).color(0xFFFFFFFF).texture(1, 0).overlay(overlay).light(0xF000F0).normal(0f, 0f, 1f);
		vc.vertex(entry, x + w, y + h, 0).color(0xFFFFFFFF).texture(1, 1).overlay(overlay).light(0xF000F0).normal(0f, 0f, 1f);
		vc.vertex(entry, x, y + h, 0).color(0xFFFFFFFF).texture(0, 1).overlay(overlay).light(0xF000F0).normal(0f, 0f, 1f);
		context.draw();
	}

	private void syncLogFromPlayer() {
		VideoPlayer player = VideoManager.getPlayer(getBlockEntity().getPos());
		if (player != null) {
			for (String line : player.getDebugLog()) {
				if (!debugLog.contains(line)) {
					debugLog.add(line);
					MediaScreenLogger.log(line);
				}
			}
		}
	}

	public net.minecraft.block.entity.BlockEntity getBlockEntity() { 
		return blockEntity; 
	}
	
	private String getYoutubeUrl() {
		if (blockEntity instanceof MediaScreenBlockEntity mse) {
			return mse.getYoutubeUrl();
		} else if (blockEntity instanceof DisplayFrameBlockEntity dfe) {
			return dfe.getYoutubeUrl();
		}
		return "";
	}
	
	private void setYoutubeUrl(String url) {
		if (blockEntity instanceof MediaScreenBlockEntity mse) {
			mse.setYoutubeUrl(url);
		} else if (blockEntity instanceof DisplayFrameBlockEntity dfe) {
			dfe.setYoutubeUrl(url);
		}
	}
	
	private boolean isPlaying() {
		if (blockEntity instanceof MediaScreenBlockEntity mse) {
			return mse.isPlaying();
		} else if (blockEntity instanceof DisplayFrameBlockEntity dfe) {
			return dfe.isPlaying();
		}
		return false;
	}
	
	private void setPlaying(boolean playing) {
		if (blockEntity instanceof MediaScreenBlockEntity mse) {
			mse.setPlaying(playing);
		} else if (blockEntity instanceof DisplayFrameBlockEntity dfe) {
			dfe.setPlaying(playing);
		}
	}
}
