package net.arcaniax.arceon.axiom.client.util;

import imgui.moulberry92.ImFont;
import net.arcaniax.arceon.axiom.ArceonClient;

import java.io.IOException;
import java.io.InputStream;

public class FontHelper {


    public static ImFont icons = null;

    public static short start = (short) 0xEA00;
    public static short end = (short) 0xEA0F;
    public static boolean wasIconDrawnThisFrame = false;

    public static byte[] loadFont(String s) {
        try {
            InputStream input = ArceonClient.class.getClassLoader().getResourceAsStream(s);
            if (input != null) {
                byte[] arrayOfByte = input.readAllBytes();
                input.close();
                return arrayOfByte;
            }
            return new byte[0];
        } catch (IOException e) {
            return new byte[0]; // Return empty array if loading fails
        }
    }



    public static boolean isOurIcon(char c) {
        return c >= 0xEA00 && c <= 0xEA0F;
    }
}
