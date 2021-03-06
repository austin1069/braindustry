package braindustry.core;

import ModVars.ModEnums;
import ModVars.modVars;
import arc.ApplicationListener;
import arc.Events;
import arc.graphics.Color;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.io.Reads;
import arc.util.io.ReusableByteInStream;
import braindustry.annotations.ModAnnotations;
import braindustry.cfunc.Couple;
import braindustry.gen.ModRemoteReadClient;
import braindustry.gen.ModRemoteReadServer;
import mindustry.Vars;
import mindustry.annotations.Annotations;
import mindustry.core.NetClient;
import mindustry.game.EventType;
import mindustry.gen.*;
import mindustry.net.Packets;
import mindustry.net.ValidateException;
import mindustry.world.Tile;
import mindustry.world.modules.ItemModule;
import braindustry.gen.ModEntityMapping;
import mindustryAddition.graphics.ModDraw;

import java.io.DataInputStream;
import java.io.IOException;

import static ModVars.modVars.*;
import static mindustry.Vars.*;
import static mindustry.Vars.netClient;
import static mindustry.Vars.netServer;

public class ModNetClient implements ApplicationListener {
    protected ReusableByteInStream byteStream = new ReusableByteInStream();
    protected DataInputStream dataStream = new DataInputStream(byteStream);

    @ModAnnotations.Remote(targets = Annotations.Loc.both, called = Annotations.Loc.client, forward = true)
    public static void showTeleportCircles(Player player,float x, float y, float radius, Color first, Color second, float fromRadius,float toRadius){
        if (headless || first==null|| second==null)return;
        ModDraw.teleportCircles(x,y,radius,first,second,Couple.of(fromRadius,toRadius));
    }
    @ModAnnotations.Remote(variants = Annotations.Variant.one, priority = Annotations.PacketPriority.low, unreliable = true)
    public static void stateSnapshot(float waveTime, int wave, int enemies, boolean paused, boolean gameOver, int timeData, short coreDataLen, byte[] coreData){
        try{
            if(wave > state.wave){
                state.wave = wave;
                Events.fire(new EventType.WaveEvent());
            }

            state.gameOver = gameOver;
            state.wavetime = waveTime;
            state.wave = wave;
            state.enemies = enemies;
            state.serverPaused = paused;

            universe.updateNetSeconds(timeData);

            modVars.netClient.byteStream.setBytes(net.decompressSnapshot(coreData, coreDataLen));
            DataInputStream input = modVars.netClient.dataStream;

            int cores = input.readInt();
            for(int i = 0; i < cores; i++){
                int pos = input.readInt();
                Tile tile = world.tile(pos);

                if(tile != null && tile.build != null){
                    tile.build.items.read(Reads.get(input));
                }else{
                    new ItemModule().read(Reads.get(input));
                }
            }

        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

//    @ModAnnotations.Remote(variants = Annotations.Variant.one, priority = Annotations.PacketPriority.low, unreliable = true, replaceLevel = 18)
    /**public static void entityZEROSnapshot(short amount, short dataLen, byte[] data) {

        try {
            modVars.netClient.byteStream.setBytes(Vars.net.decompressSnapshot(data, dataLen));
            DataInputStream input = modVars.netClient.dataStream;

            for (int j = 0; j < amount; ++j) {
                int id = input.readInt();
                byte typeID = input.readByte();
                Syncc entity = (Syncc) Groups.sync.getByID(id);
                boolean add = false;
                boolean created = false;
                if (entity == null && id == Vars.player.id()) {
                    entity = Vars.player;
                    add = true;
                }
                int classId = 255;
                if (typeID == modVars.MOD_CONTENT_ID) {
                    classId = input.readShort();
                }
                if (entity == null) {
                    if (typeID == modVars.MOD_CONTENT_ID) {
                        return;
                    } else {

                        entity = (Syncc) EntityMapping.map(typeID).get();
                    }
                    ((Syncc) entity).id(id);
                    if (!netClient.isEntityUsed(((Syncc) entity).id())) {
                        add = true;
                    }

                    created = true;
                }

                (entity).readSync(Reads.get(input));
                if (created) {
                    ((Syncc) entity).snapSync();
                }

                if (add) {
                    (entity).add();
                    netClient.addRemovedEntity((entity).id());
                }
            }

        } catch (IOException var10) {
            throw new RuntimeException(var10);
        }
    }*/

//    @ModAnnotations.Remote(variants = Annotations.Variant.one, priority = Annotations.PacketPriority.low, unreliable = true)
    /**public static void entitySnapshot(short amount, short dataLen, byte[] data) {
        if (false) {
            mindustry.core.NetClient.entitySnapshot(amount, dataLen, data);
            return;
        }
        try {
            modVars.netClient.byteStream.setBytes(Vars.net.decompressSnapshot(data, dataLen));
            DataInputStream input = modVars.netClient.dataStream;

            for (int j = 0; j < amount; ++j) {
                int id = input.readInt();
                byte typeID = input.readByte();
                Syncc entity = (Syncc) Groups.sync.getByID(id);
                boolean add = false;
                boolean created = false;
                if (entity == null && id == Vars.player.id()) {
                    entity = Vars.player;
                    add = true;
                }
                int classId = 255;
                if (typeID == modVars.MOD_CONTENT_ID) {
                    classId = input.readShort();
                }
                if (entity == null) {
                    Log.info("c: @, t: @", classId, typeID);
                    if (typeID == modVars.MOD_CONTENT_ID) {
                        entity = (Syncc) ModEntityMapping.map(classId).get();
                    } else {

                        entity = (Syncc) EntityMapping.map(typeID).get();
                    }
                    ((Syncc) entity).id(id);
                    if (!netClient.isEntityUsed(((Syncc) entity).id())) {
                        add = true;
                    }

                    created = true;
                }

                (entity).readSync(Reads.get(input));
                if (created) {
                    ((Syncc) entity).snapSync();
                }

                if (add) {
                    (entity).add();
                    netClient.addRemovedEntity((entity).id());
                }
            }

        } catch (IOException var10) {
            throw new RuntimeException(var10);
        }
    }*/

    @ModAnnotations.Remote(targets = Annotations.Loc.server, called = Annotations.Loc.client)
    public static void setServerCheatLevel(int level) {
        ModEnums.CheatLevel[] values = ModEnums.CheatLevel.values();
        modVars.settings.cheatLevelServer(values[level % values.length]);
    }

    public void registerCommands(CommandHandler handler) {
    }

    public boolean showCheatMenu() {
        boolean result = true;
        ModEnums.CheatLevel cheatLevel = modVars.settings.cheating() ? ModEnums.CheatLevel.onlyAdmins : ModEnums.CheatLevel.off;
//        Log.info("@ @ @", net.client(),net.server(),net.active());
        if (net.client()) {
            cheatLevel = modVars.settings.cheatLevelServer();
//            Log.info("on server: @", cheatLevel);
        }else if(net.server() && net.active()){
            cheatLevel=modVars.settings.cheatLevel();
        } else {
            return modVars.settings.cheating();
//            Log.info("c-@-@", net.client(),net.server());
        }
        switch (cheatLevel) {
            case off:
                result = false;
                break;

            case onlyAdmins:
                result = player.admin();
                break;
            case all:
                result = true;
                break;

        }
        return result;
    }

    @Override
    public void update() {

    }

    private static ReusableByteInStream bin;
    private static Reads read = new Reads(new DataInputStream(bin = new ReusableByteInStream()));
    public void loadNetHandler() {

        netClient.addPacketHandler(braindustryPacketPrefixServer,(string)->{
            ModRemoteReadClient.readPacket(string);
        });
//        net.handleClient(Packets.InvokePacket.class, packet -> {
//            byte[] clone = packet.bytes.clone();
//            bin.setBytes(clone);
//            ModRemoteReadClient.readPacket(read, packet.type);
////            Events.fire(new Object[]{"net.handleClient", packet.reader(), packet.type});
//
//        });
    }
}
