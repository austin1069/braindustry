package braindustry;

import ModVars.Classes.ModAtlas;
import ModVars.Classes.ModEventType;
import ModVars.modVars;
import arc.Core;
import arc.Events;
import arc.func.Boolf;
import arc.func.Func;
import arc.graphics.g2d.TextureRegion;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.CommandHandler;
import braindustry.audio.ModAudio;
import braindustry.core.ModContentLoader;
import braindustry.gen.*;
import braindustry.graphics.ModShaders;
import braindustry.graphics.g2d.ModBloom;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.ctype.MappableContent;
import mindustry.ctype.UnlockableContent;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.game.EventType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.DisposeEvent;
import mindustry.gen.EntityMapping;
import mindustry.io.JsonIO;
import mindustry.mod.Mod;
import mindustry.mod.Mods;
import mindustry.type.Item;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.meta.BuildVisibility;

import static ModVars.modFunc.*;
import static ModVars.modVars.*;
import static mindustry.Vars.*;

public class BraindustryMod extends Mod {


    static Seq<String> names = new Seq<>();

    public BraindustryMod() {
        ModEntityMapping.mapClasses();
        EventOn(DisposeEvent.class, (d) -> {
            modUI.dispose();
            Vars.ui.dispose();
        });
        modInfo = Vars.mods.getMod(this.getClass());
        modVars.load();
        Events.on(EventType.Trigger.class, e -> {
            if (e == EventType.Trigger.preDraw) {
                renderUpdate = true;
            }
            if (e == EventType.Trigger.postDraw) {
                renderUpdate = false;
            }
            if (e == EventType.Trigger.draw) {
                if (!headless) {
                    if (!(renderer.bloom instanceof ModBloom)) {
                        renderer.bloom = modBloom.parent(renderer.bloom);
                    }
                }
            }
        });
        EventOn(ClientLoadEvent.class, (e) -> {
            ModAudio.reload();
            constructor();
        });
    }

    public static TextureRegion getIcon() {

//        if (modInfo==null)modInfo = Vars.mods.getMod(mod.getClass());
//        print("modInfo: @",modInfo);
        if (modInfo == null || modInfo.iconTexture == null) return Core.atlas.find("nomap");
        return new TextureRegion(modInfo.iconTexture);
    }

    public static boolean inPackage(String packageName, Object obj) {
        if (packageName == null || obj == null) return false;
        String name;
        try {
            name = obj.getClass().getPackage().getName();
        } catch (Exception e) {
            return false;
        }
        return name.startsWith(packageName + ".");
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        modVars.netServer.registerCommands(handler);
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        modVars.netClient.registerCommands(handler);
    }

    void createPlayer() {
        if (true)return;
        player = ModPlayer.create();
        player.name = Core.settings.getString("name");
        player.color.set(Core.settings.getInt("color-0"));
        if (state.isGame()) {
            player.add();
        }
    }

    public void init() {
        if (!loaded) return;
        getModContent().each(c -> {
            if (c instanceof MappableContent && !headless) ModContentRegions.loadRegions((MappableContent) c);
        });
        createPlayer();
        modVars.init();
        EntityMapping.idMap[12] = ModPlayer::new;
        EntityMapping.nameMap.put("Player", ModPlayer::new);
        EntityMapping.nameMap.put("player", ModPlayer::new);
        Events.fire(new ModEventType.ModInit());
    }

    private void constructor() {
        if (!loaded) return;
        modInfo = Vars.mods.getMod(this.getClass());;
        BaseDialog dialog = new BaseDialog("@start_warning.title");
        dialog.cont.add("@start_warning.message").row();
        dialog.cont.button("Ok", dialog::hide).size(100f, 50f);
//        dialog.show();
    }

    public void loadContent() {
        modInfo = Vars.mods.getMod(this.getClass());
        ModAudio.load();
        print("loading mod content...");
        modAtlas = new ModAtlas();
        Events.fire(ModEventType.ModContentLoad.class);
        inTry(() -> {
            if (!headless) ModShaders.init();
        });
        if (!headless) {
            try {
                ModSounds.load();
                ModMusics.load();
            } catch (Exception ignored) {
            }
        }
        new ModContentLoader((load) -> {
            try {
                load.load();
            } catch (NullPointerException e) {
                if (!headless) showException(e);
            }
        });
        Vars.content.each((c) -> {
            if (inPackage("braindustry", c)) {
                modVars.addContent(c);
                if (c instanceof UnlockableContent) checkTranslate((UnlockableContent) c);
            }
        });
        loaded = true;
    }
}
