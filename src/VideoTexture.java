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

    public int getWidth()  { return registeredWidth; }
    public int getHeight() { return registeredHeight; }

    public void uploadFrame(byte[] rgbBuffer, int width, int height, boolean isFirst) {
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

        if (nativeTexture == null || registeredWidth != w || registeredHeight != h) {
            closeInternal();
            registeredWidth  = w;
            registeredHeight = h;

            NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
            nativeTexture = new NativeImageBackedTexture(image);

            String safeName = "video_" + w + "x" + h + "_" + System.nanoTime();
            identifier = MinecraftClient.getInstance()
                    .getTextureManager()
                    .registerDynamicTexture(safeName, nativeTexture);
        }

        NativeImage image = nativeTexture.getImage();
        if (image == null) return;

        int idx = 0;
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int r = frame[idx]     & 0xFF;
                int g = frame[idx + 1] & 0xFF;
                int b = frame[idx + 2] & 0xFF;
                idx += 3;
                image.setColor(x, y, (0xFF << 24) | (b << 16) | (g << 8) | r);
            }
        }

        nativeTexture.upload();
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
