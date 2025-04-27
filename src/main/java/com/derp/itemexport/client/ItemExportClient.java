package com.derp.itemexport.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL40C;

import java.io.File;
import java.io.IOException;

public class ItemExportClient implements ClientModInitializer {
    private static KeyBinding captureKey;

    @Override
    public void onInitializeClient() {
        captureKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.itemscreenshot.capture_item",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.itemscreenshot"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (captureKey.wasPressed()) {
                captureHeldItem(client);
            }
        });
    }

    private void captureHeldItem(MinecraftClient client) {
        if (client.player == null) return;
        ItemStack stack = client.player.getMainHandStack();
        if (stack.isEmpty()) return;

        File outputDir = new File(client.runDirectory, "exported_items");
        if (!outputDir.exists()) outputDir.mkdirs();
        File outputFile = new File(outputDir, "item_" + System.currentTimeMillis() + ".png");

        int size = 256;
        Framebuffer framebuffer = new SimpleFramebuffer(size, size, true, MinecraftClient.IS_SYSTEM_MAC);

        framebuffer.beginWrite(true);
        RenderSystem.clearColor(0, 0, 0, 0);
        RenderSystem.clear(GL40C.GL_COLOR_BUFFER_BIT | GL40C.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);

        Matrix4f projectionMatrix = new Matrix4f()
                .setOrtho(-size / 2f, size / 2f, -size / 2f, size / 2f, -1000, 1000);
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorter.BY_Z);
        RenderSystem.viewport(0, 0, size, size);

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.push();
        matrixStack.translate(0, 0, 800);
        matrixStack.scale((float) size, (float) size, (float) size);

        DiffuseLighting.enableGuiDepthLighting();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        DiffuseLighting.disableGuiDepthLighting();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        ItemRenderer itemRenderer = client.getItemRenderer();
        BakedModel model = itemRenderer.getModel(stack, null, client.player, 0);

        int light = 0xF000F0;
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();

        itemRenderer.renderItem(
                stack,
                ModelTransformationMode.GUI,
                false,
                matrixStack,
                consumers,
                light,
                OverlayTexture.DEFAULT_UV,
                model
        );
        consumers.draw();

        matrixStack.pop();
        framebuffer.endWrite();

        NativeImage image = takeFramebufferScreenshotTransparent(framebuffer);
        framebuffer.delete();

        try {
            image.writeTo(outputFile);
            client.player.sendMessage(Text.literal("Saved item to: " + outputFile.getName()), false);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            image.close();
        }
    }

    private NativeImage takeFramebufferScreenshotTransparent(Framebuffer framebuffer) {
        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        RenderSystem.bindTexture(framebuffer.getColorAttachment());

        image.loadFromTextureImage(0, false);

        flipNativeImageY(image);

        return image;
    }

    private void flipNativeImageY(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height / 2; y++) {
            for (int x = 0; x < width; x++) {
                int topPixel = image.getColor(x, y);
                int bottomPixel = image.getColor(x, height - 1 - y);

                image.setColor(x, y, bottomPixel);
                image.setColor(x, height - 1 - y, topPixel);
            }
        }
    }
}