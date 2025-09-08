package net.arcaniax.arceon.axiom;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static net.arcaniax.arceon.axiom.ArceonInitializer.LOGGER;

public class Networking {

    private static boolean isSupported = false;
    private static boolean arceonDetected = false;
    private static boolean OP = false;
    private static ArrayList<String> perms = new ArrayList<>();
    private static Set<String> wantedPerms = new HashSet<>();

    public static boolean onServer = false;

    public static boolean getArceonDetected(){
        return arceonDetected;
    }

    private static ArrayList<Integer> order = new ArrayList();

    public static void register() {

        PayloadTypeRegistry.playS2C().register(IntPacketServer.ID, IntPacketServer.CODEC);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientPlayNetworking.registerGlobalReceiver(IntPacketServer.ID, (payload, context) -> {
                order.add(payload.d);
                if (order.size()==8){
                    updateVersion(order, context);
                    order = new ArrayList<>();
                }
            });
        });

        PayloadTypeRegistry.playS2C().register(StringPacketServer.ID, StringPacketServer.CODEC);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientPlayNetworking.registerGlobalReceiver(StringPacketServer.ID, (payload, context) -> {

                // DEBUG
                //context.player().sendMessage(Text.of(payload.message), false);

                if (payload.message.equalsIgnoreCase("OP")){
                    OP = true;
                }
                else{
                    perms.add(payload.message);
                }
            });
        });
        PayloadTypeRegistry.playC2S().register(PermsPacketServer.ID, PermsPacketServer.CODEC);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientPlayNetworking.unregisterGlobalReceiver(IntPacketServer.ARCEON_VERSION_ID);
            arceonDetected = false;
            isSupported = false;
            perms = new ArrayList<>();
        });




        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getCurrentServerEntry() != null && !onServer) {
                onServer = true;
                ArceonInitializer.client = client;
            }
            if (client.getCurrentServerEntry() == null) {
                onServer = false;
            }
        });

    }

    public static void requestPerms(){
        OP = false;
        perms = new ArrayList<>();
        for (String perm : wantedPerms){
            ClientPlayNetworking.send(new PermsPacketServer(perm));
        }
    }

    private record PermsPacketServer(String message) implements CustomPayload {
        public static final Identifier ARCEON_PERMS_ID = Identifier.of("arceon", "perms_request");
        public static final Id<PermsPacketServer> ID = new CustomPayload.Id<>(ARCEON_PERMS_ID);
        public static final PacketCodec<PacketByteBuf, PermsPacketServer> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, PermsPacketServer::message,
                        PermsPacketServer::new
                );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private record StringPacketServer(String message) implements CustomPayload {
        public static final Identifier ARCEON_VERSION_ID = Identifier.of("arceon", "perms");
        public static final Id<StringPacketServer> ID = new CustomPayload.Id<>(ARCEON_VERSION_ID);
        public static final PacketCodec<PacketByteBuf, StringPacketServer> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.STRING, StringPacketServer::message,
                        StringPacketServer::new
                );


        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private record IntPacketServer(int d) implements CustomPayload {
        public static final Identifier ARCEON_VERSION_ID = Identifier.of("arceon", "version");
        public static final Id<IntPacketServer> ID = new CustomPayload.Id<>(ARCEON_VERSION_ID);
        public static final PacketCodec<PacketByteBuf, IntPacketServer> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.INTEGER, IntPacketServer::d,
                        IntPacketServer::new
                );


        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static void updateVersion(ArrayList<Integer> payload, ClientPlayNetworking.Context context){
        // MAPPING
        String myVersion = context.client().getGameVersion();

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
        if (payload.get(7)!=ArceonInitializer.version){
            return;
        }

        String[] versionSplit = myVersion.split("\\.");
        LOGGER.info(myVersion);

        LOGGER.info("arceon: " + mainPayload + "." + versionPayload + "." + subPayload);
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

    public static boolean attemptToUse(ClientPlayerEntity player, boolean feedback){
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