package net.arcaniax.arceon.axiom;

import net.arcaniax.arceon.axiom.client.network.Networking;
import net.arcaniax.arceon.axiom.client.loft.LoftEditorClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArceonClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("arceon_x_axiom");

    public static int version = 1;

    public static Minecraft client = null;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Registering Arceon client features...");
        LoftEditorClient.register();
        if (hasFabricPayloadRegistry()){
            Networking.register();
        }
        else {
            // OLD NETWORKING (NOT WORKING)
            LOGGER.info("Older version of Fabric detected. (only works for Singleplayer)");
        }

        if (FabricLoader.getInstance().isModLoaded("axiom")) {
            AxiomIntegration.register();
        } else {
            LOGGER.info("Axiom is not installed; Loft preview remains available and Axiom tools are disabled");
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
