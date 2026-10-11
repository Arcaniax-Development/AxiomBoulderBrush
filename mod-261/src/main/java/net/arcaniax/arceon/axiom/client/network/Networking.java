package net.arcaniax.arceon.axiom.client.network;

import net.arcaniax.arceon.axiom.ArceonClient;
import net.arcaniax.arceon.axiom.client.loft.LoftEditorClient;
import net.arcaniax.arceon.axiom.client.network.packet.IntPacketPayload;
import net.arcaniax.arceon.axiom.client.network.packet.LoftCancelPacketPayload;
import net.arcaniax.arceon.axiom.client.network.packet.LoftHelloPacketPayload;
import net.arcaniax.arceon.axiom.client.network.packet.LoftResultPacketPayload;
import net.arcaniax.arceon.axiom.client.network.packet.LoftSessionOpenPacketPayload;
import net.arcaniax.arceon.axiom.client.network.packet.PermsRequestPacketPayload;
import net.arcaniax.arceon.axiom.client.network.packet.StringPacketPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
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
        LoftEditorClient.debug("Registering Loft networking for Minecraft 26.1.2");

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
        PayloadTypeRegistry.serverboundPlay().register(LoftHelloPacketPayload.ID, LoftHelloPacketPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LoftCancelPacketPayload.ID, LoftCancelPacketPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LoftSessionOpenPacketPayload.ID, LoftSessionOpenPacketPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LoftResultPacketPayload.ID, LoftResultPacketPayload.CODEC);
        LoftEditorClient.debug(
                "Registered preview-only payload types: C2S loft_hello/loft_cancel; S2C loft_session_open/loft_result"
        );

        ClientPlayNetworking.registerGlobalReceiver(LoftSessionOpenPacketPayload.ID, (payload, context) -> {
            LoftEditorClient.debug(
                    "Network receiver got arceon:loft_session_open sessionId={} revision={}",
                    payload.sessionId(), payload.revision()
            );
            Minecraft.getInstance().execute(() -> LoftEditorClient.open(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(LoftResultPacketPayload.ID, (payload, context) -> {
            LoftEditorClient.debug(
                    "Network receiver got arceon:loft_result sessionId={} accepted={} code={}",
                    payload.sessionId(), payload.accepted(), payload.code()
            );
            Minecraft.getInstance().execute(() -> LoftEditorClient.handleResult(payload));
        });
        LoftEditorClient.debug("Registered Loft S2C receivers before connection channel advertisement");

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LoftEditorClient.debug("Client JOIN event fired; Loft receivers were already registered during initialization");
            client.execute(() -> {
                LoftEditorClient.debug("Client thread handling JOIN; starting Loft hello retry window");
                LoftEditorClient.queueHelloRetries();
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LoftEditorClient.debug("Client DISCONNECT event fired; keeping global Loft receivers registered");
            ClientPlayNetworking.unregisterGlobalReceiver(IntPacketPayload.ID.id());
            ClientPlayNetworking.unregisterGlobalReceiver(StringPacketPayload.ID.id());
            arceonDetected = false;
            isSupported = false;
            perms = new ArrayList<>();
            LoftEditorClient.clearSession();
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

        isSupported = isClientVersionSupported(myVersion, mainPayload, versionPayload, subPayload);
    }

    private static boolean isClientVersionSupported(String clientVersion, int maxMajor, int maxMinor, int maxPatch) {
        int[] client = parseVersion(clientVersion);
        if (client == null) {
            return false;
        }
        return compareVersions(client[0], client[1], client[2], maxMajor, maxMinor, maxPatch) <= 0;
    }

    private static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts.length == 0) {
            return null;
        }
        try {
            return new int[]{
                    Integer.parseInt(parts[0].replaceAll("[^0-9].*$", "")),
                    parts.length > 1 ? Integer.parseInt(parts[1].replaceAll("[^0-9].*$", "")) : 0,
                    parts.length > 2 ? Integer.parseInt(parts[2].replaceAll("[^0-9].*$", "")) : 0
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int compareVersions(int aMajor, int aMinor, int aPatch, int bMajor, int bMinor, int bPatch) {
        if (aMajor != bMajor) return Integer.compare(aMajor, bMajor);
        if (aMinor != bMinor) return Integer.compare(aMinor, bMinor);
        return Integer.compare(aPatch, bPatch);
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

