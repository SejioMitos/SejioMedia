package com.mediascreen.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.nio.IntBuffer;

public class VideoTexture {

	private NativeImageBackedTexture nativeTexture;
	private Identifier identifier;
	private int width;
	private int height;

	private volatile byte[] pendingFrame;
	private volatile boolean needsUpload;

	private IntBuffer directIntBuf;
	private boolean bulkWriteSupported;

	public int getWidth() { return width; }
	public int getHeight() { return height; }

	private void ensureTexture(int w, int h) {
		if (nativeTexture != null && width == w && height == h) return;
		closeInternal();
		width = w;
		height = h;
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, true);
		nativeTexture = new NativeImageBackedTexture(image);

		bulkWriteSupported = initBulkWrite(image);
	}

	private boolean initBulkWrite(NativeImage image) {
		for (String name : new String[]{"colorInts", "intBuffer", "buffer", "data", "pixels", "colorData", "field_24975", "field_24976"}) {
			try {
				Field f = NativeImage.class.getDeclaredField(name);
				f.setAccessible(true);
				Object val = f.get(image);
				if (val instanceof IntBuffer ib) {
					directIntBuf = ib;
					return true;
				}
				if (val instanceof int[] arr) {
					directIntBuf = java.nio.IntBuffer.wrap(arr);
					return true;
				}
			} catch (Exception ignored) {}
		}
		return false;
	}

	public void uploadFrame(byte[] rgbBuffer, int width, int height, boolean isFirst) {
		pendingFrame = rgbBuffer;
		needsUpload = true;
		if (isFirst) {
			this.width = width;
			this.height = height;
		}
	}

	public void updateFrame(byte[] rgbBuffer, int width, int height) {
		uploadFrame(rgbBuffer, width, height, false);
	}

	public void tickUpload() {
		if (!needsUpload) return;

		byte[] frame = pendingFrame;
		if (frame == null) return;

		int w = width;
		int h = height;

		needsUpload = false;
		pendingFrame = null;

		ensureTexture(w, h);

		NativeImage image = nativeTexture.getImage();

		if (bulkWriteSupported && directIntBuf != null) {
			directIntBuf.clear();
			int pixelCount = w * h;
			int srcIdx = 0;
			for (int i = 0; i < pixelCount; i++) {
				int r = frame[srcIdx] & 0xFF;
				int g = frame[srcIdx + 1] & 0xFF;
				int b = frame[srcIdx + 2] & 0xFF;
				srcIdx += 3;
				directIntBuf.put((0xFF << 24) | (r << 16) | (g << 8) | b);
			}
		} else {
			int idx = 0;
			for (int y = h - 1; y >= 0; y--) {
				for (int x = 0; x < w; x++) {
					int r = frame[idx] & 0xFF;
					int g = frame[idx + 1] & 0xFF;
					int b = frame[idx + 2] & 0xFF;
					idx += 3;
					image.setColor(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
				}
			}
		}

		nativeTexture.upload();
	}

	public Identifier getOrRegisterIdentifier(String name) {
		if (nativeTexture == null) return null;
		if (identifier == null) {
			String safeName = name.replaceAll("[^a-z0-9/._-]", "_");
			identifier = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture(safeName, nativeTexture);
		}
		return identifier;
	}

	private void closeInternal() {
		if (nativeTexture != null) {
			nativeTexture.close();
			nativeTexture = null;
		}
		identifier = null;
		directIntBuf = null;
		bulkWriteSupported = false;
	}

	public void close() {
		closeInternal();
	}
}
