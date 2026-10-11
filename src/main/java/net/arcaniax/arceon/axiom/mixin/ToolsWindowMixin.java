package net.arcaniax.arceon.axiom.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.axiom.editor.windows.ToolsWindow;
import com.moulberry.axiom.tools.Tool;
import imgui.moulberry92.ImGui;
import net.arcaniax.arceon.axiom.client.util.FontHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({ToolsWindow.class})
public class ToolsWindowMixin {
    @Inject(method = {"render"}, at = {@At(value = "INVOKE", target = "Lcom/moulberry/axiom/tools/Tool;iconChar()C", shift = At.Shift.AFTER)}, remap = false)
    private static void renderAfterIconChar(CallbackInfo ci, @Local(ordinal = 0) @NotNull Tool tool) {
        if (FontHelper.isOurIcon(tool.iconChar())) {
            ImGui.popFont();
            ImGui.pushFont(FontHelper.icons, FontHelper.icons.getLegacySize());
        }
    }

    @Inject(method = {"render"}, at = {@At(value = "INVOKE", target = "Limgui/moulberry92/ImGui;pushFont(Limgui/moulberry92/ImFont;F)V", ordinal = 1, shift = At.Shift.AFTER)}, remap = false)
    private static void renderAfterPushFont1(CallbackInfo ci, @Local(ordinal = 0) @NotNull Tool tool) {
        if (FontHelper.isOurIcon(tool.iconChar())) {
            ImGui.popFont();
            ImGui.pushFont(FontHelper.icons, FontHelper.icons.getLegacySize());
        }
    }

    @Inject(method = {"render"}, at = {@At(value = "INVOKE", target = "Limgui/moulberry92/ImGui;pushFont(Limgui/moulberry92/ImFont;F)V", ordinal = 2, shift = At.Shift.AFTER)}, remap = false)
    private static void renderAfterPushFont2(CallbackInfo ci, @Local(ordinal = 0) @NotNull Tool tool) {
        if (FontHelper.isOurIcon(tool.iconChar())) {
            ImGui.popFont();
            ImGui.pushFont(FontHelper.icons, FontHelper.icons.getLegacySize());
        }
    }
}
