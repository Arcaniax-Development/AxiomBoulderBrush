package net.arcaniax.arceon.axiom;

import net.arcaniax.arceon.axiom.client.network.Networking;
import net.arcaniax.arceon.axiom.client.tools.BoulderBrush;
import net.fabricmc.api.ClientModInitializer;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.moulberry.axiom.tools.ToolManager;

public class ArceonClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("arceon_x_axiom");

    public static int version = 1;

    public static Minecraft client = null;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Registering Arceon features into Axiom...");
        ToolManager.addTool(new BoulderBrush());
        if (hasFabricPayloadRegistry()){
            Networking.register();
        }
        else {
            // OLD NETWORKING (NOT WORKING)
            LOGGER.info("Older version of Fabric detected. (only works for Singleplayer)");
        }
    }

    public static boolean hasFabricPayloadRegistry() {
        try {
            Class.forName("net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry");
            return true; // Available in this Fabric API version
        } catch (ClassNotFoundException e) {
            return false; // Too old
        }
    }
}
