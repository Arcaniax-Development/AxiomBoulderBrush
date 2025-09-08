package net.arcaniax.arceon.axiom;

import net.fabricmc.api.ClientModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.moulberry.axiom.tools.ToolManager;

public class ArceonInitializer implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("arceon_x_axiom");

    public static int version = 1;

    public static MinecraftClient client = null;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Registering Arceon features into Axiom...");
        ToolManager.addTool(new BoulderBrush());
        Networking.register();
    }
}
