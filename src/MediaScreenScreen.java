package com.mediascreen.client;

import com.mediascreen.block.MediaScreenBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class MediaScreenScreen extends Screen {

	private static final java.util.Map<net.minecraft.util.math.BlockPos, String> URL_CACHE = new java.util.HashMap<>();

	private static final int PANEL_W = 500;
	private static final int PANEL_H = 340;

	private final MediaScreenBlockEntity blockEntity;

	private TextFieldWidget urlField;
	private ButtonWidget playButton;
	private ButtonWidget stopButton;

	private int previewX, previewY, previewW, previewH;
	private int logX, logY, logW, logH;

	private final List<String> debugLog = new ArrayList<>();
	private int logScroll = 0;

	public MediaScreenScreen(MediaScreenBlockEntity blockEntity) {
		super(Text.literal("MediaScreen"));
		this.blockEntity = blockEntity;
	}

	@Override
	protected void init() {
		int panelX = (width - PANEL_W) / 2;
		int panelY = (height - PANEL_H) / 2;
		int pad = 14;
		int innerW = PANEL_W - pad * 2;

		urlField = new TextFieldWidget(textRenderer, panelX + pad, panelY + pad, innerW, 20, Text.literal("URL HERE"));
		urlField.setMaxLength(1024);
		String cached = URL_CACHE.get(blockEntity.getPos());
		String saved = cached != null ? cached : blockEntity.getYoutubeUrl();
		urlField.setText(saved);
		urlField.setChangedListener(url -> URL_CACHE.put(blockEntity.getPos(), url));
		urlField.setFocused(true);

		previewX = panelX + pad;
		previewY = panelY + pad + 26;
		previewW = innerW;
		previewH = 170;

		int bottomY = previewY + previewH + 8;
		int bottomH = panelY + PANEL_H - pad - bottomY;
		int btnColW = 80;
		int btnGap = 8;
		int btnX = panelX + PANEL_W - pad - btnColW;
		logX = panelX + pad;
		logW = btnX - logX - btnGap;
		logY = bottomY;
		logH = bottomH;

		int btnTotalH = 44;
		int btnOffsetY = (bottomH - btnTotalH) / 2;
		int btnY = bottomY + btnOffsetY;

		playButton = ButtonWidget.builder(Text.literal("PLAY"), btn -> onPlay())
			.dimensions(btnX, btnY, btnColW, 20).build();
		stopButton = ButtonWidget.builder(Text.literal("STOP"), btn -> onStop())
			.dimensions(btnX, btnY + 24, btnColW, 20).build();

		addDrawableChild(urlField);
		addDrawableChild(playButton);
		addDrawableChild(stopButton);
		addSelectableChild(urlField);

		if (blockEntity.isPlaying() && VideoManager.getPlayer(blockEntity.getPos()) != null) {
			playButton.active = false;
		}

		checkFFmpeg();
	}

	private void checkFFmpeg() {
		String path = FFmpegManager.getFFmpegPath();
		if (path == null) {
			debugLog.add("FFMPEG NOT FOUND - STARTING DOWNLOAD");
			if (!FFmpegManager.isInstalling()) {
				FFmpegManager.installFFmpeg(status -> {
					debugLog.add(status);
				}).exceptionally(e -> {
					debugLog.add("FFmpeg install failed: " + e.getMessage());
					return null;
				});
			}
		}
	}

	private void onPlay() {
		String url = urlField.getText().trim();
		if (url.isEmpty()) { debugLog.add("Error: No URL"); return; }

		blockEntity.setYoutubeUrl(url);
		URL_CACHE.put(blockEntity.getPos(), url);
		playButton.active = false;
		VideoManager.stopPlayer(blockEntity.getPos());

		if (YtDlpManager.isDownloading() || FFmpegManager.isInstalling()) {
			debugLog.add("Downloads in progress, please wait...");
			playButton.active = true;
			return;
		}

		if (!YtDlpManager.isDownloaded()) {
			debugLog.add("Downloading yt-dlp first...");
			YtDlpManager.downloadYtDlp(s -> debugLog.add(s))
				.thenRun(() -> MinecraftClient.getInstance().execute(() -> {
					if (!FFmpegManager.isInstalled()) {
						startFFmpegInstall(url);
					} else {
						startPlayback(url);
					}
				}));
			return;
		}

		if (!FFmpegManager.isInstalled()) {
			startFFmpegInstall(url);
			return;
		}

		startPlayback(url);
	}

	private void startFFmpegInstall(String url) {
		debugLog.add("Installing FFmpeg (first time)...");
		FFmpegManager.installFFmpeg(status -> {
			debugLog.add(status);
			if (status.equals("FFmpeg ready")) {
				startPlayback(url);
			}
		}).exceptionally(e -> {
			debugLog.add("FFmpeg install failed: " + e.getMessage());
			playButton.active = true;
			return null;
		});
	}

	private void startPlayback(String url) {
		debugLog.add("Resolving URL...");
		YtDlpManager.resolveStreamUrl(url,
			s -> debugLog.add(s),
			info -> {
				MinecraftClient.getInstance().execute(() -> {
					debugLog.add("Starting playback...");
					VideoPlayer player = new VideoPlayer(info.url, info.width, info.height, err -> {
						MinecraftClient.getInstance().execute(() -> {
							debugLog.add("Error: " + err);
							playButton.active = true;
						});
					});
					VideoManager.startPlayer(blockEntity.getPos(), player);
					debugLog.add("Playing");
					blockEntity.setPlaying(true);
				});
			},
			error -> {
				MinecraftClient.getInstance().execute(() -> {
					debugLog.add("Error: " + error);
					blockEntity.setPlaying(false);
					playButton.active = true;
				});
			}
		);
	}

	private void onStop() {
		VideoManager.stopPlayer(blockEntity.getPos());
		blockEntity.setPlaying(false);
		playButton.active = true;
		debugLog.add("Stopped");
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		int lineH = textRenderer.fontHeight + 2;
		int maxLines = Math.max(1, logH / lineH);
		int total = debugLog.size();
		int maxScroll = Math.max(0, total - maxLines);
		if (mouseX >= logX && mouseX <= logX + logW && mouseY >= logY && mouseY <= logY + logH) {
			logScroll -= (int) vertical;
			logScroll = Math.max(0, Math.min(logScroll, maxScroll));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

		int panelX = (width - PANEL_W) / 2;
		int panelY = (height - PANEL_H) / 2;

		drawPanel(context, panelX, panelY, PANEL_W, PANEL_H);

		VideoPlayer currentPlayer = VideoManager.getPlayer(blockEntity.getPos());
		if (currentPlayer != null && currentPlayer.getTexture() != null) {
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

				context.drawCenteredTextWithShadow(textRenderer, "PREVIEW", previewX + previewW / 2, previewY - 12, 0x888888);
			}
		} else {
			context.drawCenteredTextWithShadow(textRenderer, "PREVIEW", previewX + previewW / 2, previewY + previewH / 2 - 4, 0x555555);
		}

		int logBgX = logX - 2;
		int logBgY = logY - 2;
		int logBgW = logW + 4;
		int logBgH = logH + 4;
		context.fill(logBgX, logBgY, logBgX + logBgW, logBgY + logBgH, 0xFF1A1A1A);
		context.drawBorder(logBgX, logBgY, logBgW, logBgH, 0xFF3A3A3A);

		context.enableScissor(logX, logY, logX + logW, logY + logH);

		int lineH = textRenderer.fontHeight + 2;
		int maxLines = Math.max(1, logH / lineH);
		int total = debugLog.size();
		int maxScroll = Math.max(0, total - maxLines);
		logScroll = Math.min(logScroll, maxScroll);
		int start = Math.max(0, total - maxLines + logScroll);
		for (int i = start; i < total && (i - start) < maxLines; i++) {
			String line = debugLog.get(i);
			int y = logY + (i - start) * lineH;
			int color = line.startsWith("Error") || line.startsWith("FFMPEG NOT FOUND") ? 0xFF5555 :
					   line.startsWith("Playing") ? 0x55FF55 : 0xDDDDDD;
			context.drawText(textRenderer, line, logX + 2, y, color, false);
		}

		context.disableScissor();

		urlField.render(context, mouseX, mouseY, delta);
		playButton.render(context, mouseX, mouseY, delta);
		stopButton.render(context, mouseX, mouseY, delta);

		syncLogFromPlayer();
	}

	private void drawTextureQuad(DrawContext context, VideoTexture tex, int x, int y, int w, int h) {
		Identifier texId = tex.getOrRegisterIdentifier("video_gui_" + blockEntity.getPos().toShortString());
		if (texId == null) return;
		RenderLayer layer = RenderLayer.getEntityTranslucent(texId);

		var entry = context.getMatrices().peek();
		var vc = context.getVertexConsumers().getBuffer(layer);
		int overlay = net.minecraft.client.render.OverlayTexture.DEFAULT_UV;
		vc.vertex(entry, x, y + h, 0).color(0xFFFFFFFF).texture(0, 1).overlay(overlay).light(0xF000F0).normal(0f, 0f, 1f);
		vc.vertex(entry, x + w, y + h, 0).color(0xFFFFFFFF).texture(1, 1).overlay(overlay).light(0xF000F0).normal(0f, 0f, 1f);
		vc.vertex(entry, x + w, y, 0).color(0xFFFFFFFF).texture(1, 0).overlay(overlay).light(0xF000F0).normal(0f, 0f, 1f);
		vc.vertex(entry, x, y, 0).color(0xFFFFFFFF).texture(0, 0).overlay(overlay).light(0xF000F0).normal(0f, 0f, 1f);
		context.draw();
	}

	private void syncLogFromPlayer() {
		VideoPlayer player = VideoManager.getPlayer(blockEntity.getPos());
		if (player != null) {
			for (String line : player.getDebugLog()) {
				if (!debugLog.contains(line)) {
					debugLog.add(line);
				}
			}
		}
	}

	private void drawPanel(DrawContext context, int x, int y, int w, int h) {
		int corner = 8;
		int bg = 0xEE202020;
		int border = 0xFF505050;
		int mid = 0xEE2A2A2A;

		context.fill(x + corner, y, x + w - corner, y + h, bg);
		context.fill(x, y + corner, x + w, y + h - corner, bg);

		context.fill(x + corner, y, x + w - corner, y + 1, border);
		context.fill(x + corner, y + h - 1, x + w - corner, y + h, border);
		context.fill(x, y + corner, x + 1, y + h - corner, border);
		context.fill(x + w - 1, y + corner, x + w, y + h - corner, border);

		context.fill(x, y, x + corner, y + corner, mid);
		context.fill(x + w - corner, y, x + w, y + corner, mid);
		context.fill(x, y + h - corner, x + corner, y + h, mid);
		context.fill(x + w - corner, y + h - corner, x + w, y + h, mid);
	}

	public MediaScreenBlockEntity getBlockEntity() { return blockEntity; }
}
