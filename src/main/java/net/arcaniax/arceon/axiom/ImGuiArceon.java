package net.arcaniax.arceon.axiom;

import com.moulberry.axiom.editor.ImGuiHelper;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Util;

import java.net.URI;

public class ImGuiArceon {

    public static void noPermission(){
        ImGuiHelper.setupBorder();
        ImGui.textColored(255, 114, 77, 255, "No permission to use this tool");
        ImGui.button("Refresh");
        requestPerms();
        ImGuiHelper.finishBorder();
    }

    public static void getArceon(){
        ImGuiHelper.setupBorder();
        if (!Networking.getArceonDetected()){
            ImGui.textColored(255, 114, 77, 255, "Could not connect to Arceon");
            ImGui.button("Get Arceon Plugin");
            openArceon();
            ImGuiHelper.finishBorder();

            ImGuiHelper.separatorWithText("Not an administrator?");

            ImGuiHelper.setupBorder();
            ImGui.textWrapped("Contact someone from the staff team to get Arceon installed!");
            ImGuiHelper.finishBorder();
        }
        else{
            ImGui.textColored(255, 114, 77, 255, "This version not supported!");
            ImGui.button("Update Arceon Plugin");
            openArceon();
            ImGuiHelper.finishBorder();

            ImGuiHelper.separatorWithText("Not an administrator?");

            ImGuiHelper.setupBorder();
            ImGui.textWrapped("Contact someone from the staff team to get Arceon updated!");
            ImGuiHelper.finishBorder();
        }

    }


    private static void openArceon(){
        if (ImGui.isItemClicked()) {
            String page = "https://arceon.gitbook.io/arceon-wiki";
            MinecraftClient.getInstance().setScreen(new ConfirmLinkScreen((open) -> {
                if (open) {
                    try {
                        Util.getOperatingSystem().open(new URI(page));
                    } catch (Exception var3) {
                    }
                }

                MinecraftClient.getInstance().setScreen((Screen)null);
            }, page, true));
        }
    }

    private static void requestPerms(){
        if (ImGui.isItemClicked()) {
            Networking.requestPerms();
        }
    }
}
