package com.mediascreen.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

public class VideoTexture {

    private NativeImageBackedTexture nativeTexture;
    private Identifier identifier;
    private int registeredWidth;
    private int registeredHeight;

    private volatile byte[] pendingFrame;
    private volatile int pendingWidth;
    private volatile int pendingHeight;
    private volatile boolean needsUpload;

    private volatile boolean hasError = false;
    private volatile String lastError = null;

    public int getWidth()  { return registeredWidth; }
    public int getHeight() { return registeredHeight; }
    public boolean hasError() { return hasError; }
    public String getLastError() { return lastError; }

    public void setError(String error) {
        hasError = true;
        lastError = error;
        MediaScreenLogger.log("[VideoTexture] ERROR: " + error);
    }

    public void clearError() {
        hasError = false;
        lastError = null;
    }

    public void uploadFrame(byte[] rgbBuffer, int width, int height, boolean isFirst) {
        if (rgbBuffer == null) {
            hasError = true;
            lastError = "Frame data is null";
            MediaScreenLogger.log("[VideoTexture] ERROR: Frame data is null");
            return;
        }
        if (width <= 0 || height <= 0) {
            hasError = true;
            lastError = "Invalid frame dimensions: " + width + "x" + height;
            MediaScreenLogger.log("[VideoTexture] ERROR: Invalid dimensions " + width + "x" + height);
            return;
        }
        if (rgbBuffer.length < width * height * 3) {
            hasError = true;
            lastError = "Frame buffer too small: " + rgbBuffer.length + " < " + (width * height * 3);
            MediaScreenLogger.log("[VideoTexture] ERROR: Buffer too small " + rgbBuffer.length + " vs " + (width * height * 3));
            return;
        }
        clearError();
        pendingFrame  = rgbBuffer;
        pendingWidth  = width;
        pendingHeight = height;
        needsUpload   = true;
    }

    public void updateFrame(byte[] rgbBuffer, int width, int height) {
        uploadFrame(rgbBuffer, width, height, false);
    }

    public void tickUpload() {
        if (!needsUpload) return;
        byte[] frame = pendingFrame;
        if (frame == null) return;

        int w = pendingWidth;
        int h = pendingHeight;
        needsUpload  = false;
        pendingFrame = null;

        if (w <= 0 || h <= 0) {
            hasError = true;
            lastError = "Invalid upload dimensions: " + w + "x" + h;
            MediaScreenLogger.log("[VideoTexture] ERROR: Invalid upload dimensions " + w + "x" + h);
            return;
        }

        if (nativeTexture == null || registeredWidth != w || registeredHeight != h) {
            closeInternal();
            registeredWidth  = w;
            registeredHeight = h;

            try {
                NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
                nativeTexture = new NativeImageBackedTexture(image);

                String safeName = "video_" + w + "x" + h + "_" + System.nanoTime();
                identifier = MinecraftClient.getInstance()
                        .getTextureManager()
                        .registerDynamicTexture(safeName, nativeTexture);
            } catch (Exception e) {
                hasError = true;
                lastError = "Failed to create texture: " + e.getMessage();
                MediaScreenLogger.log("[VideoTexture] ERROR: Failed to create texture - " + e.getMessage());
                return;
            }
        }

        NativeImage image = nativeTexture.getImage();
        if (image == null) {
            hasError = true;
            lastError = "NativeImage is null";
            MediaScreenLogger.log("[VideoTexture] ERROR: NativeImage is null");
            return;
        }

        try {
            int rowBytes = w * 3;
            float brightness = 1.3f;
            for (int y = 0; y < h; y++) {
                int srcIdx = y * rowBytes;
                for (int x = 0; x < w; x++) {
                    int r = Math.min(255, (int)((frame[srcIdx]     & 0xFF) * brightness));
                    int g = Math.min(255, (int)((frame[srcIdx + 1] & 0xFF) * brightness));
                    int b = Math.min(255, (int)((frame[srcIdx + 2] & 0xFF) * brightness));
                    srcIdx += 3;
                    image.setColor(x, y, (0xFF << 24) | (b << 16) | (g << 8) | r);
                }
            }

            nativeTexture.upload();
        } catch (Exception e) {
            hasError = true;
            lastError = "Upload failed: " + e.getMessage();
            MediaScreenLogger.log("[VideoTexture] ERROR: Upload failed - " + e.getMessage());
        }
    }

    public Identifier getOrRegisterIdentifier(String ignoredName) {
        return identifier;
    }

    private void closeInternal() {
        if (identifier != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(identifier);
            identifier = null;
        }
        if (nativeTexture != null) {
            nativeTexture.close();
            nativeTexture = null;
        }
        registeredWidth  = 0;
        registeredHeight = 0;
    }

    public void close() {
        closeInternal();
    }
}
