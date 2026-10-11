package net.arcaniax.arceon.axiom;

import com.moulberry.axiom.tools.ToolManager;
import net.arcaniax.arceon.axiom.client.tools.BoulderBrush;

/**
 * Isolates references to Axiom classes so the main entrypoint can load without
 * Axiom on the client classpath.
 */
final class AxiomIntegration {
    private AxiomIntegration() {
    }

    static void register() {
        ArceonClient.LOGGER.info("Axiom detected; registering Arceon Axiom tools...");
        ToolManager.addTool(new BoulderBrush());
    }
}
