package net.arcaniax.arceon.axiom.client.network;

import net.arcaniax.arceon.axiom.ArceonClient;
import net.arcaniax.arceon.axiom.client.network.packet.IntPacketPayload;
import net.arcaniax.arceon.axiom.client.network.packet.PermsRequestPacketPayload;
import net.arcaniax.arceon.axiom.client.network.packet.StringPacketPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static net.arcaniax.arceon.axiom.ArceonClient.LOGGER;

public class Networking {

    private static boolean isSupported = false;
    private static boolean arceonDetected = false;
    private static boolean OP = false;
    private static ArrayList<String> perms = new ArrayList<>();
    private static Set<String> wantedPerms = new HashSet<>();

    public static boolean onServer = false;

    public static String maxVersion = "unknown";

    public static boolean getArceonDetected(){
        return arceonDetected;
    }

    private static Instant lastRequest = null;

    private static ArrayList<Integer> order = new ArrayList();

    public static void register() {

        PayloadTypeRegistry.clientboundPlay().register(IntPacketPayload.ID, IntPacketPayload.CODEC);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientPlayNetworking.registerGlobalReceiver(IntPacketPayload.ID, (payload, context) -> {


                //context.player().displayClientMessage(Component.literal("" + payload.getInt()), false);
                order.add(payload.getInt());
                if (order.size()==8){
                    updateVersion(order, context);
                    order = new ArrayList<>();
                }
            });
        });

        PayloadTypeRegistry.clientboundPlay().register(StringPacketPayload.ID, StringPacketPayload.CODEC);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientPlayNetworking.registerGlobalReceiver(StringPacketPayload.ID, (payload, context) -> {

                // DEBUG
                //context.player().displayClientMessage(Component.literal(payload.getMessage()), false);

                if (payload.getMessage().equalsIgnoreCase("OP")){
                    OP = true;
                }
                else{
                    perms.add(payload.getMessage());
                }
            });
        });

        PayloadTypeRegistry.serverboundPlay().register(PermsRequestPacketPayload.ID, PermsRequestPacketPayload.CODEC);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientPlayNetworking.unregisterGlobalReceiver(IntPacketPayload.ID.id());
            ClientPlayNetworking.unregisterGlobalReceiver(StringPacketPayload.ID.id());
            arceonDetected = false;
            isSupported = false;
            perms = new ArrayList<>();
        });




        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getCurrentServer() != null && !onServer) {
                onServer = true;
                ArceonClient.client = client;
                lastRequest = null;
            }
            if (client.getCurrentServer() == null) {
                onServer = false;
            }

            if (onServer && client.level != null && !getArceonDetected()){
                if (lastRequest == null || Instant.now().isAfter(lastRequest.plusSeconds(5))) {
                    //client.player.sendMessage(Text.of("attempt reconnect"), false);
                    order = new ArrayList<>();
                    if (wantedPerms.isEmpty()){
                        wantedPerms.add("ping");
                    }
                    Networking.requestPerms();
                    lastRequest = Instant.now();
                }
            }
        });
    }

    public static void requestPerms(){
        OP = false;
        perms = new ArrayList<>();
        order = new ArrayList();
        for (String perm : wantedPerms){
            ClientPlayNetworking.send(new PermsRequestPacketPayload(perm));
        }
    }

    private static void updateVersion(ArrayList<Integer> payload, ClientPlayNetworking.Context context){
        // MAPPING
        String myVersion = FabricLoader.getInstance().getModContainer("minecraft")
                .orElseThrow()
                .getMetadata()
                .getVersion()
                .getFriendlyString();

        int mainPayload = payload.get(6) + 34;
        int subPayload = payload.get(5) - 97;
        int versionPayload = payload.get(1) + subPayload;


        if (payload.get(4)!=165){ // VERIFY LEGITIMACY OF MESSAGE
            return;
        }

        if (payload.get(3) != (subPayload + versionPayload + 77)){ // VERIFY LEGITIMACY OF MESSAGE
            return;
        }
        arceonDetected = true;
        if (payload.get(7)!= ArceonClient.version){
            return;
        }

        maxVersion = mainPayload + "." + versionPayload + "." + subPayload;

        if (myVersion.contains("fabric-loader")){
            String[] parts = myVersion.split("-");
            myVersion = parts[parts.length - 1];
        }

        String[] versionSplit = myVersion.split("\\.");
        //LOGGER.info(myVersion);

        //LOGGER.info("arceon: " + mainPayload + "." + versionPayload + "." + subPayload);
        if (versionSplit.length==3){
            try{
                int main = Integer.parseInt(versionSplit[0]);
                int ver = Integer.parseInt(versionSplit[1]);
                int sub = Integer.parseInt(versionSplit[2]);
                if (main <= mainPayload){
                    if (ver <= versionPayload){
                        if (sub <= subPayload){
                            isSupported = true;
                        }
                    }
                }

            } catch (Exception e){}
        }
    }

    public static boolean attemptToUse(Player player, boolean feedback){
        if (!arceonDetected){
            //if (feedback){
                //player.sendMessage(Text.of("No Arceon plugin has been found on the server."), false);
                //player.sendMessage(Text.of("https://arceon.gitbook.io/arceon-wiki"), false);
            //}
            return false;
        }
        else{
            if (isSupported){
                return true;
            }
            else{
                //if (feedback){
                    //player.sendMessage(Text.of("This version is not supported, please update your Arceon plugin!"), false);
                //}
                return false;
            }
        }
    }

    public static boolean hasPermission(String request){
        if (!wantedPerms.contains("arceon.axiom." + request)){
            wantedPerms.add("arceon.axiom." + request);
            requestPerms();
        }
        if (OP || perms.contains("arceon.axiom.*") || perms.contains("arceon.axiom." + request)){
            return true;
        }
        return false;
    }
}
