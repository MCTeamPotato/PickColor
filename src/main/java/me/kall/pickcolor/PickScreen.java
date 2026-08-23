package me.kall.pickcolor;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Consumer;

@SuppressWarnings("DataFlowIssue")
public class PickScreen extends Screen {
    private static final Component TITLE = Component.translatable("screen.pickcolor.title");

    private final @Nullable Screen lastScreen;
    private final Consumer<Color> colorConsumer;

    private ResourceLocation colorsLocation;
    private DynamicTexture colors;
    private int wheelX, wheelY, wheelSize;

    private float hue = 0f;
    private float saturation = 0f;
    private float brightness = 1f;
    private int alpha = 255;

    private Color colorNow = new Color(255, 255, 255, 255);

    private EditBox redEdit, greenEdit, blueEdit, alphaEdit;

    private int indicatorX, indicatorY;

    private int brightX, brightY, brightWidth, brightHeight;
    private boolean brightSetting = false;

    private boolean suppressResponder = false;

    private PickScreen(@Nullable Screen lastScreen, Consumer<Color> colorConsumer) {
        super(TITLE);
        this.lastScreen = lastScreen;
        this.colorConsumer = colorConsumer;
    }

    @Override
    public void init() {
        this.wheelSize = Math.min(this.width / 3, this.height / 2) - 20;
        if (this.wheelSize < 100) this.wheelSize = 100;

        this.brightWidth = 20;
        int editWidth = 50;

        int totalWidth = this.wheelSize + 15 + this.brightWidth + 25 + editWidth;
        this.wheelX = (this.width - totalWidth) / 2;
        if (this.wheelX < 5) this.wheelX = 5;

        this.wheelY = this.height / 2 - this.wheelSize / 2;

        NativeImage image = new NativeImage(256, 256, false);

        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                int dx = x - 128;
                int dy = y - 128;

                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance > 128) {
                    image.setPixelRGBA(x, y, 0x00000000);
                    continue;
                }

                float hue = (float) Math.toDegrees(Math.atan2(dy, dx));
                if (hue < 0F) hue += 360F;

                float saturation = (float) (distance / 128);

                int rgb = Color.HSBtoRGB(hue / 360F, saturation, 1.0F);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                image.setPixelRGBA(x, y, ((0xFF << 24) | (b << 16) | (g << 8) | r));
            }
        }

        this.colors = new DynamicTexture(image);
        this.colorsLocation = ResourceLocation.fromNamespaceAndPath(PickColor.MOD_ID, "color_wheel_" + System.nanoTime());
        this.minecraft.getTextureManager().register(this.colorsLocation, this.colors);

        float[] hsb = Color.RGBtoHSB(this.colorNow.getRed(), this.colorNow.getGreen(), this.colorNow.getBlue(), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
        this.alpha = this.colorNow.getAlpha();

        this.updateIndicator();

        this.brightHeight = this.wheelSize;
        this.brightX = this.wheelX + this.wheelSize + 15;
        this.brightY = this.wheelY;

        int editHeight = 20;

        int rightPanelX = this.brightX + this.brightWidth + 25;
        int startY = this.height / 2 - 60;
        int spacing = 25;

        this.redEdit = new EditBox(this.font, rightPanelX, startY, editWidth, editHeight, Component.literal("R"));
        this.greenEdit = new EditBox(this.font, rightPanelX, startY + spacing, editWidth, editHeight, Component.literal("G"));
        this.blueEdit = new EditBox(this.font, rightPanelX, startY + spacing * 2, editWidth, editHeight, Component.literal("B"));
        this.alphaEdit = new EditBox(this.font, rightPanelX, startY + spacing * 3, editWidth, editHeight, Component.literal("A"));

        this.setupEditBox(this.redEdit, this.colorNow.getRed());
        this.setupEditBox(this.greenEdit, this.colorNow.getGreen());
        this.setupEditBox(this.blueEdit, this.colorNow.getBlue());
        this.setupEditBox(this.alphaEdit, this.colorNow.getAlpha());

        this.addRenderableWidget(this.redEdit);
        this.addRenderableWidget(this.greenEdit);
        this.addRenderableWidget(this.blueEdit);
        this.addRenderableWidget(this.alphaEdit);

        int buttonWidth = 100;
        int buttonY = this.height - 30;

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_YES, button -> this.onDone()).bounds(this.width / 2 - buttonWidth - 5, buttonY, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_NO, button -> this.onClose()).bounds(this.width / 2 + 5, buttonY, buttonWidth, 20).build());
    }

    private void setupEditBox(@NotNull EditBox editBox, int initialValue) {
        editBox.setValue(String.valueOf(initialValue));
        editBox.setFilter(s -> {
            if (s.isEmpty()) return true;
            try {
                int val = Integer.parseInt(s);
                return val >= 0 && val <= 255;
            } catch (NumberFormatException e) {
                return false;
            }
        });
        editBox.setResponder(s -> {
            if (this.suppressResponder) return;
            try {
                int r = this.clampColor(this.redEdit.getValue());
                int g = this.clampColor(this.greenEdit.getValue());
                int b = this.clampColor(this.blueEdit.getValue());
                int a = this.clampColor(this.alphaEdit.getValue());
                this.colorNow = new Color(r, g, b, a);
                this.alpha = a;

                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                this.hue = hsb[0];
                this.saturation = hsb[1];
                this.brightness = hsb[2];

                this.updateIndicator();
            } catch (NumberFormatException ignored) {}
        });
    }

    private void updateIndicator() {
        int centerX = this.wheelX + this.wheelSize / 2;
        int centerY = this.wheelY + this.wheelSize / 2;
        double angle = Math.toRadians(this.hue * 360.0);
        int radius = this.wheelSize / 2;
        double dx = Math.cos(angle) * this.saturation * radius;
        double dy = Math.sin(angle) * this.saturation * radius;

        this.indicatorX = (int) Math.round(centerX + dx);
        this.indicatorY = (int) Math.round(centerY + dy);
    }

    private int clampColor(String s) {
        if (s == null || s.isEmpty()) return 255;
        try {
            return Math.clamp(Integer.parseInt(s), 0, 255);
        } catch (NumberFormatException e) {
            return 255;
        }
    }

    private void onDone() {
        this.onClose();
        this.colorConsumer.accept(this.colorNow);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.blit(this.colorsLocation, this.wheelX, this.wheelY, this.wheelSize, this.wheelSize, 0, 0, 256, 256, 256, 256);

        int shadeAlpha = (int) ((1f - this.brightness) * 255f);
        if (shadeAlpha > 0) {
            int shadeColor = (shadeAlpha << 24);
            guiGraphics.fill(this.wheelX, this.wheelY, this.wheelX + this.wheelSize, this.wheelY + this.wheelSize, shadeColor);
        }

        guiGraphics.fill(this.indicatorX - 3, this.indicatorY - 3, this.indicatorX + 3, this.indicatorY + 3, 0xFFFFFFFF);
        guiGraphics.fill(this.indicatorX - 2, this.indicatorY - 2, this.indicatorX + 2, this.indicatorY + 2, 0xFF000000);

        int pureRgb = Color.HSBtoRGB(this.hue, this.saturation, 1.0f);
        int pr = (pureRgb >> 16) & 0xFF;
        int pg = (pureRgb >> 8) & 0xFF;
        int pb = pureRgb & 0xFF;

        for (int row = 0; row < this.brightHeight; row++) {
            float t = 1f - (row / (float) this.brightHeight);
            int r = (int) (pr * t);
            int g = (int) (pg * t);
            int b = (int) (pb * t);
            int color = 0xFF000000 | (r << 16) | (g << 8) | b;
            guiGraphics.fill(this.brightX, this.brightY + row, this.brightX + this.brightWidth, this.brightY + row + 1, color);
        }

        int indicatorRow = (int) ((1f - this.brightness) * this.brightHeight);
        guiGraphics.fill(this.brightX - 2, this.brightY + indicatorRow - 1, this.brightX + this.brightWidth + 2, this.brightY + indicatorRow + 1, 0xFFFFFFFF);

        int previewX = this.brightX + this.brightWidth + 25;
        int previewY = this.height / 2 + 60;
        int previewSize = 40;
        int argb = (this.colorNow.getAlpha() << 24) | (this.colorNow.getRed() << 16) | (this.colorNow.getGreen() << 8) | this.colorNow.getBlue();
        guiGraphics.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, argb);

        int argX = this.brightX + this.brightWidth + 13;
        guiGraphics.drawString(this.font, "R:", argX, this.height / 2 - 55, 0xFFFFFF);
        guiGraphics.drawString(this.font, "G:", argX, this.height / 2 - 30, 0xFFFFFF);
        guiGraphics.drawString(this.font, "B:", argX, this.height / 2 - 5, 0xFFFFFF);
        guiGraphics.drawString(this.font, "A:", argX, this.height / 2 + 20, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= this.wheelX && mouseX <= this.wheelX + this.wheelSize && mouseY >= this.wheelY && mouseY <= this.wheelY + this.wheelSize) {
            this.selectColor(mouseX, mouseY);
            return true;
        }

        if (mouseX >= this.brightX && mouseX <= this.brightX + this.brightWidth && mouseY >= this.brightY && mouseY <= this.brightY + this.brightHeight) {
            this.brightSetting = true;
            this.selectBrightness(mouseY);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.brightSetting) {
            this.selectBrightness(mouseY);
            return true;
        }

        if (mouseX >= this.wheelX && mouseX <= this.wheelX + this.wheelSize && mouseY >= this.wheelY && mouseY <= this.wheelY + this.wheelSize) {
            this.selectColor(mouseX, mouseY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.brightSetting = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void selectColor(double mouseX, double mouseY) {
        int centerX = this.wheelX + this.wheelSize / 2;
        int centerY = this.wheelY + this.wheelSize / 2;
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        int radius = this.wheelSize / 2;

        float newHue;
        float newSaturation;

        if (distance < 0.0001) {
            newHue = this.hue;
            newSaturation = 0f;
        } else {
            if (distance > radius) {
                double scale = radius / distance;
                dx *= scale;
                dy *= scale;
                distance = radius;
            }

            float hueDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
            if (hueDeg < 0) hueDeg += 360f;
            newHue = hueDeg / 360f;
            newSaturation = (float) (distance / radius);
        }

        this.hue = newHue;
        this.saturation = newSaturation;
        this.hsb2Color();
    }

    private void selectBrightness(double mouseY) {
        double clampedY = Math.clamp(mouseY, this.brightY, this.brightY + this.brightHeight);
        float t = (float) ((clampedY - this.brightY) / this.brightHeight);
        this.brightness = 1f - t;
        this.hsb2Color();
    }

    private void hsb2Color() {
        int rgb = Color.HSBtoRGB(this.hue, this.saturation, this.brightness);
        int newR = (rgb >> 16) & 0xFF;
        int newG = (rgb >> 8) & 0xFF;
        int newB = rgb & 0xFF;

        this.colorNow = new Color(newR, newG, newB, this.alpha);

        this.suppressResponder = true;
        this.redEdit.setValue(String.valueOf(newR));
        this.greenEdit.setValue(String.valueOf(newG));
        this.blueEdit.setValue(String.valueOf(newB));
        this.suppressResponder = false;

        this.updateIndicator();
    }

    @Override
    public void removed() {
        if (this.colors != null) {
            this.minecraft.getTextureManager().release(this.colorsLocation);
            this.colors.close();
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        this.removed();
        this.minecraft.setScreen(this.lastScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}