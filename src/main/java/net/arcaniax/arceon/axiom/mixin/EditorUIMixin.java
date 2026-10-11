package net.arcaniax.arceon.axiom.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.axiom.editor.EditorUI;
import imgui.moulberry92.ImFontConfig;
import imgui.moulberry92.ImGuiIO;
import net.arcaniax.arceon.axiom.client.util.FontHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {EditorUI.class})
public abstract class EditorUIMixin {

    private static String FONT_NAME = "ArceonIcons";

    @Inject(method = "initFonts",
            at = @At(
                    value = "INVOKE",
                    target = "Limgui/moulberry92/ImFontConfig;setMergeMode(Z)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER),
            remap = false)
    private static void initFonts(String languageCode, CallbackInfo ci, @Local(ordinal = 0) ImGuiIO io,
                                  @Local(ordinal = 0) int size, @Local(ordinal = 0) ImFontConfig fontConfig) {
        short[] glyphRanges = new short[]{FontHelper.start, FontHelper.end, 0};
        fontConfig.setName(FONT_NAME);
        FontHelper.icons = io.getFonts().addFontFromMemoryTTF(FontHelper.loadFont("arceonicons.ttf"),  (float) (size * 2), fontConfig, glyphRanges);
    }
}
