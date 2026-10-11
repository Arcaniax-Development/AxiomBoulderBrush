package net.arcaniax.arceon.axiom.client.gui;

import com.moulberry.axiom.editor.ImGuiHelper;
import imgui.moulberry92.ImGui;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.arcaniax.arceon.axiom.ArceonClient;
import net.arcaniax.arceon.axiom.client.network.Networking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import java.awt.Desktop;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;

public class DefaultsGUI {



    public static void noPermission(){
        ImGuiHelper.setupBorder();
        ImGui.textColored(255, 114, 77, 255, "No permission to use this tool");
        ImGui.button("Refresh");
        requestPerms();
        ImGuiHelper.finishBorder();
        ImGui.dummy(1.0f, 1.0f);
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
        ImGui.dummy(1.0f, 1.0f);

    }


    private static void openArceon(){
        if (ImGui.isItemClicked()) {
            String page = "https://arceon.gitbook.io/arceon-wiki";
            URI uri = URI.create(page);
            BooleanConsumer callback = open -> {
                if (open) {
                    try {
                        Desktop.getDesktop().browse(uri);
                    } catch (Exception ignored) {
                    }
                }

                setScreen(null);
            };

            try {
                Constructor<ConfirmLinkScreen> constructor;
                Object destination;
                try {
                    constructor = ConfirmLinkScreen.class.getConstructor(BooleanConsumer.class, URI.class, boolean.class);
                    destination = uri;
                } catch (NoSuchMethodException ignored) {
                    constructor = ConfirmLinkScreen.class.getConstructor(BooleanConsumer.class, String.class, boolean.class);
                    destination = page;
                }
                setScreen(constructor.newInstance(callback, destination, true));
            } catch (ReflectiveOperationException ignored) {
                callback.accept(true);
            }
        }
    }

    private static void setScreen(Screen screen) {
        Minecraft client = ArceonClient.client != null ? ArceonClient.client : Minecraft.getInstance();
        try {
            Method method = Minecraft.class.getMethod("setScreen", Screen.class);
            method.invoke(client, screen);
        } catch (ReflectiveOperationException ignored) {
            try {
                Method method = Minecraft.class.getMethod("setScreenAndShow", Screen.class);
                method.invoke(client, screen);
            } catch (ReflectiveOperationException ignoredAgain) {
            }
        }
    }

    private static void requestPerms(){
        if (ImGui.isItemClicked()) {
            Networking.requestPerms();
        }
    }
}
