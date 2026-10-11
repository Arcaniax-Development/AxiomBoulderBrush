package net.arcaniax.arceon.axiom.client.gui;

import com.moulberry.axiom.editor.ImGuiHelper;
import imgui.moulberry92.ImGui;
import net.arcaniax.arceon.axiom.ArceonClient;
import net.arcaniax.arceon.axiom.client.network.Networking;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.util.Util;

import java.net.URI;

public class DefaultsGUI {



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
            //ImGui.textUnformatted("My version: " + ArceonInitializer.client.getGameVersion());
            //ImGui.textUnformatted("Server Version: " + Networking.maxVersion);
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
            ArceonClient.client.setScreen(new ConfirmLinkScreen((open) -> {
                if (open) {
                    try {
                        Util.getPlatform().openUri(new URI(page));
                    } catch (Exception var3) {
                    }
                }

                ArceonClient.client.getInstance().setScreen(null);
            }, page, true));
        }
    }

    private static void requestPerms(){
        if (ImGui.isItemClicked()) {
            Networking.requestPerms();
        }
    }
}
