package braindustry.world.blocks.production;

import ModVars.modFunc;
import arc.Core;
import arc.func.Func;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.EnumSet;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import braindustry.content.ModFx;
import braindustry.type.Recipe;
import braindustry.world.ModBlock;
import braindustry.world.meta.RecipeListValue;
import mindustry.content.Fx;
import mindustry.content.Liquids;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Sounds;
import mindustry.graphics.Pal;
import mindustry.type.*;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.ItemSelection;
import mindustry.world.consumers.*;
import mindustry.world.meta.BlockFlag;
import mindustry.world.meta.BlockGroup;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.values.ItemListValue;
import mindustryAddition.iu.MultiBar;
import mindustryAddition.world.consumers.ConsumeLiquidDynamic;
import braindustry.world.meta.AStat;
import braindustry.world.meta.AStats;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static ModVars.modFunc.fullName;
import static ModVars.modFunc.getInfoDialog;

public class MultiCrafter extends ModBlock {
    public final int timerDump;
    public final int timerReBuildBars;
    public Seq<Recipe> recipes = new Seq<>();
    public Effect craftEffect;
    public Effect updateEffect;
    public int queueSize = 1;
    public float updateEffectChance;
    public TextureRegion[] itemsTexture;
    public boolean changeTexture = false;
    public boolean dynamicItem = true;
    public boolean dynamicLiquid = true;
    public boolean dynamicPower = true;
    public float extraStorageLiquid = 1;
    public float extraStorageItem = 1;
    AStats aStats = new AStats();

    public MultiCrafter(String name) {
        super(name);
        this.stats = aStats.copy(stats);
        timerDump = timers++;
        timerReBuildBars = timers++;
        this.craftEffect = Fx.none;
        this.updateEffect = Fx.none;
        this.updateEffectChance = 0.04F;
        this.update = true;
        this.solid = true;
        this.hasItems = true;
        this.ambientSound = Sounds.machine;
        this.sync = true;
        this.ambientSoundVolume = 0.03F;
        this.flags = EnumSet.of(BlockFlag.factory);
        this.configurable = true;
        this.itemCapacity = 10;
        this.destructible = true;
        this.group = BlockGroup.none;
        this.config(Integer.class, (build, i) -> {
            MultiCrafterBuild tile = (MultiCrafterBuild) build;
            tile.currentRecipe = i >= 0 && i < recipes.size ? i : -1;
            Color color;
            boolean spawnEffect = changeTexture;
            if (tile.currentRecipe != -1) {
                color = new Color(Color.white).lerp(Color.yellow, 1.0f);
                if (itemsTexture != null && itemsTexture[i] == region) spawnEffect = false;
            } else {
                color = new Color(Color.black).lerp(Color.white, 0.0f);
            }
            if (spawnEffect)
                ModFx.changeCraft.at(tile.x, tile.y, tile.block.size * 1.1f, color, new Color(Color.black).lerp(Color.white, 8.0f));
            tile.progress = 0.0F;
            tile.rebuildInfo();

        });
        if (dynamicItem) {
            this.consumes.add(new ConsumeItemDynamic(e -> {
                return ((MultiCrafterBuild) e).getCurrentRecipe().consumeItems;
            }));
        }
        if (dynamicLiquid) {
            this.consumes.add(new ConsumeLiquidDynamic(e -> {
                return ((MultiCrafterBuild) e).getCurrentRecipe().consumeLiquids;
            }));
        }
    }

    public void recipes(Recipe... recipes) {
        this.recipes = Seq.with(recipes);
    }

    @Override
    public void load() {
        super.load();
        if (changeTexture) {
            itemsTexture = new TextureRegion[this.recipes.size];
            for (int i = 0; i < itemsTexture.length; i++) {
                String itemName = this.recipes.get(i).outputItem.item.name;
                if (itemName.startsWith(fullName(""))) itemName = itemName.split(fullName(""), 2)[1];
//                print("load: @",this.name+"-"+itemName);
                itemsTexture[i] = Core.atlas.find(this.name + "-" + itemName);
                if (!itemsTexture[i].found()) itemsTexture[i] = region;
            }
        }
    }

    @Override
    public void setStats() {
        aStats.add(Stat.size, "@x@", new Object[]{this.size, this.size});
        aStats.add(Stat.health, (float)this.health, StatUnit.none);
        if (this.canBeBuilt()) {
            aStats.add(Stat.buildTime, this.buildCost / 60.0F, StatUnit.seconds);
            aStats.add(Stat.buildCost, new ItemListValue(false, this.requirements));
        }

        if (this.instantTransfer) {
            aStats.add(Stat.maxConsecutive, 2.0F, StatUnit.none);
        }

        this.consumes.display(this.stats);
        if (this.hasLiquids) {
            aStats.add(Stat.liquidCapacity, this.liquidCapacity, StatUnit.liquidUnits);
        }

        if (this.hasItems && this.itemCapacity > 0) {
            aStats.add(Stat.itemCapacity, (float)this.itemCapacity, StatUnit.items);
        }
        aStats.add(AStat.recipes, new RecipeListValue(recipes));


    }

    public void init() {
//        hasLiquids|=dynamicLiquid;
//        hasItems|=dynamicItem;
//        hasPower|= dynamicPower;
        this.itemCapacity = 0;
        recipes.each((recipe -> {
            Seq.with(recipe.consumeItems).each((itemStack -> {
                this.itemCapacity = Math.max(this.itemCapacity, itemStack.amount);
//                this.itemCapacity+=itemStack.amount;
            }));
            Seq.with(recipe.consumeLiquids).each((liquidStack -> {
                this.liquidCapacity = Math.max(this.liquidCapacity, liquidStack.amount);
            }));
            if (recipe.outputLiquid != null)
                this.liquidCapacity = Math.max(this.liquidCapacity, recipe.outputLiquid.amount);
            if (recipe.outputItem != null) this.itemCapacity = Math.max(this.itemCapacity, recipe.outputItem.amount);
        }));
        this.liquidCapacity *= extraStorageLiquid;
        this.itemCapacity *= extraStorageItem;
        super.init();


        this.config(Item.class, (obj, item) -> {
            MultiCrafterBuild tile = (MultiCrafterBuild) obj;
            tile.currentRecipe = this.recipes.indexOf((p) -> {
                return p.outputItem.item == item;
            });
            tile.resetProgress();
        });
    }

    @Override
    public void setBars() {
        bars.add("health", (entity) -> {
            Color var10003 = Pal.health;
            Objects.requireNonNull(entity);
            return (new Bar("stat.health", var10003, entity::healthf)).blink(Color.white);
        });
        if (this.hasLiquids) {
            Func<Building, Liquid> current;
            if (this.consumes.has(ConsumeType.liquid) && this.consumes.get(ConsumeType.liquid) instanceof ConsumeLiquidDynamic) {
                bars.add("liquids", (t) -> {
                    MultiCrafterBuild build = (MultiCrafterBuild) t;
                    if (build == null) return new Bar("0", Color.black.cpy(), () -> 0f);
                    Seq<MultiBar.BarPart> barParts = new Seq<>();

                    Seq<LiquidStack> stacks = build.getNeedLiquids();
                    stacks.each((stack -> {
                        barParts.add(new MultiBar.BarPart(stack.liquid.color, () -> {
                            if (build.liquids == null) return 0.0f;
                            float amounts = build.liquids.get(stack.liquid);
                            float need = stack.amount;
                            if (need == 0 && build.currentRecipe != -1) return 0;
                            return Math.max(amounts / need, 0);
                        }));
                    }));
                    return new MultiBar(() -> {
                        String text = Core.bundle.get("bar.liquids");
                        if (build.liquids == null)
                            return text;
                        return text + " " + Mathf.round((build.countNowLiquid() / build.countNeedLiquid() * 100f), 0.1f) + "%";
                    }, barParts);
                });

            } else {
                if (this.consumes.has(ConsumeType.liquid) && this.consumes.get(ConsumeType.liquid) instanceof ConsumeLiquid) {
                    Liquid liquid = ((ConsumeLiquid) this.consumes.get(ConsumeType.liquid)).liquid;
                    current = (entity) -> {
                        return liquid;
                    };
                } else {
                    current = (entity) -> {
                        return entity.liquids == null ? Liquids.water : entity.liquids.current();
                    };
                }
                bars.add("liquid", (entity) -> {
                    return new Bar(() -> {
                        return entity.liquids.get((Liquid) current.get(entity)) <= 0.001F ? Core.bundle.get("bar.liquid") : ((Liquid) current.get(entity)).localizedName;
                    }, () -> {
                        return ((Liquid) current.get(entity)).barColor();
                    }, () -> {
                        return entity != null && entity.liquids != null ? entity.liquids.get((Liquid) current.get(entity)) / this.liquidCapacity : 0.0F;
                    });
                });
            }
        }

        if (this.hasPower && this.consumes.hasPower()) {
            ConsumePower cons = this.consumes.getPower();
            boolean buffered = cons.buffered;
            float capacity = cons.capacity;
            bars.add("power", (entity) -> {
                return new Bar(() -> {
                    return buffered ? Core.bundle.format("bar.poweramount", Float.isNaN(entity.power.status * capacity) ? "<ERROR>" : (int) (entity.power.status * capacity)) : Core.bundle.get("bar.power");
                }, () -> {
                    return Pal.powerBar;
                }, () -> {
                    return Mathf.zero(cons.requestedPower(entity)) && entity.power.graph.getPowerProduced() + entity.power.graph.getBatteryStored() > 0.0F ? 1.0F : entity.power.status;
                });
            });
        }

        if (this.hasItems && this.configurable) {

            bars.add("items", (entity) -> {
                return new Bar(() -> {
                    return Core.bundle.format("bar.items", entity.items.total());
                }, () -> {
                    return Pal.items;
                }, () -> {
                    return (float) entity.items.total() / (float) this.itemCapacity;
                });
            });
        }

    }


    public class MultiCrafterBuild extends Building {
        public int currentRecipe = -1;
        public MultiCrafter block;
        public float progress;
        public float totalProgress;
        protected Runnable rebuildBars=()->{};
        protected Runnable rebuildCons=()->{};

        public MultiCrafterBuild() {
            this.block = MultiCrafter.this;
        }

        public Building init(Tile tile, Team team, boolean shouldAdd, int rotation) {
            return super.init(tile, team, shouldAdd, rotation);
        }
        boolean selected=false;
        @Override
        public void update() {
            super.update();
            boolean selected=modFunc.selected(this);
            if (!selected && this.selected){
//                rebuildBars=rebuildCons=()->{};
            }
            this.selected=selected;
        }
        public void displayConsumption(Table table) {
            rebuildCons=()-> {
                table.clearChildren();
                table.clear();
                table.left();
                Consume[] var2 = this.block.consumes.all();
                int var3 = var2.length;

                for (Consume cons : var2) {
                    if (!cons.isOptional() || !cons.isBoost()) {
                        cons.build(this, table);
                    }
                }
            };
            rebuildCons.run();

        }
        public void displayBars(Table table) {

            rebuildBars=()->{
                table.clearChildren();
//                table.defaults().growX().height(18.0F).pad(4.0F);
                for (Func<Building, Bar> bar : bars.list()) {
                    try {
                        table.add(bar.get(this)).growX();
                        table.row();
                    } catch (ClassCastException e) {
                        break;
                    }
                }
            };
            rebuildBars.run();
        }

        public void drawStatus() {
            if (this.currentRecipe == -1) return;
            if (!MultiCrafter.this.changeTexture || (MultiCrafter.this.size >= 2)) {
                super.drawStatus();
            } else {
                float brcx = this.tile.drawx() + (float) (this.block.size * 8) / 2.0F - 2.0F;
                float brcy = this.tile.drawy() - (float) (this.block.size * 8) / 2.0F + 2.0F;
                Draw.z(71.0F);
                Draw.color(Pal.gray);
                Fill.square(brcx, brcy, 1.25f, 45.0F);
                Draw.color(this.cons.status().color);
                Fill.square(brcx, brcy, 0.75f, 45.0F);
                Draw.color();
            }

        }

        public int getMaximumAccepted(Item item) {
            ItemStack itemStack = Seq.with(getCurrentRecipe().consumeItems).find((i) -> i.item == item);
            return itemStack == null ? 0 : itemStack.amount;
        }

        public void draw() {
            if (!MultiCrafter.this.changeTexture || this.currentRecipe == -1) {
                Draw.rect(this.block.region, this.x, this.y, this.block.rotate ? this.rotdeg() : 0.0F);
            } else {
                Draw.rect(MultiCrafter.this.itemsTexture[currentRecipe], this.x, this.y, this.block.rotate ? this.rotdeg() : 0.0F);
            }
        }

        public void buildConfiguration(Table table) {
            Seq<Item> recipes = Seq.with(MultiCrafter.this.recipes).map((u) -> {
                return u.outputItem.item;
            }).filter((u) -> {
                return u.unlockedNow();
            });
            if (recipes.any()) {
                ItemSelection.buildTable(table, recipes, () -> {
                    return this.currentRecipe == -1 ? null : MultiCrafter.this.recipes.get(this.currentRecipe).outputItem.item;
                }, (item) -> {
                    this.configure(MultiCrafter.this.recipes.indexOf((u) -> {
                        return u.outputItem.item == item;
                    }));
                });
            } else {
                table.table(Styles.black3, (t) -> {
                    t.add("@none").color(Color.lightGray);
                });
            }
//            table.button(this.consValid()+"",()->{});
            if (true) return;
            table.button("debug", () -> {
                String[] lines = {
                        Strings.format("if1: @", this.consValid()),
                        Strings.format("if2: @", (getCurrentRecipe().outputItem != null
                                && this.items.get(getCurrentRecipe().outputItem.item) >= this.block.itemCapacity)),
                        Strings.format("liquid consume: @", getNeedLiquids().toString())
                };
                getInfoDialog("", "Debug window", Strings.join("\n", lines), Color.green.add(Color.black)).show();
            });
        }

        public float countNowLiquid() {

            AtomicReference<Float> amounts = new AtomicReference<>(0f);
            this.getNeedLiquids().each(stack -> {
                amounts.set(Math.min(this.liquids.get(stack.liquid), stack.amount) + amounts.get());
            });
            return amounts.get();
        }

        public float countNeedLiquid() {

            AtomicReference<Float> need = new AtomicReference<>(0f);
            this.getNeedLiquids().each(stack -> {
                need.set(stack.amount + need.get());
            });
            return need.get();
        }

        public Seq<LiquidStack> getNeedLiquids() {
            return Seq.with(getCurrentRecipe().consumeLiquids);
        }

        public void handleLiquid(Building source, Liquid liquid, float amount) {
            Seq<LiquidStack> needLiquids = getNeedLiquids();
            LiquidStack found = needLiquids.find((l) -> l.liquid == liquid);
            if (found == null) {
                return;
            }
            ;
            float need = Math.max(0, found.amount - this.liquids.get(liquid));
            this.liquids.add(liquid, Math.min(amount, need));
        }

        @Override
        public String toString() {
            return "MultiCrafterBuild#" + id;
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid) {
            Seq<LiquidStack> need = getNeedLiquids();
            LiquidStack found = need.find((l) -> l.liquid.name.equals(liquid.name));
            return found != null && this.liquids.get(liquid) <= found.amount;
        }


        public boolean acceptItem(Building source, Item item) {
            int[] count={-1};
            Seq.with(getCurrentRecipe().consumeItems).select((itemStack) -> {
                return itemStack.item == item;
            }).each((itemStack -> {
                count[0]+=itemStack.amount;
            }));
            return count[0] > 0 && this.items.get(item) < count[0];
        }

        public boolean shouldConsume() {
            return getCurrentRecipe().outputItem == null || this.items.get(getCurrentRecipe().outputItem.item) < MultiCrafter.this.itemCapacity;
        }

        protected Recipe getCurrentRecipe() {
            if (currentRecipe == -1) return Recipe.empty;
            return MultiCrafter.this.recipes.get(currentRecipe);
        }

        public boolean canCraft() {
            int req = 0;
            int has = 0;
            Seq<ItemStack> requirements = Seq.with(this.getCurrentRecipe().consumeItems);
            AtomicInteger count = new AtomicInteger(0);
            requirements.each((i) -> {
                count.addAndGet(i.amount + i.item.id);
            });
            req = count.get();
            count.set(0);
            this.items.each((item, c) -> {
                count.addAndGet(item.id + c);
            });
            has = count.get();
//            this.cons

            return this.consValid() && req <= has;
        }

        public void updateTile() {
            if (timer.get(timerReBuildBars, 5)) {
                setBars();
            }
            if (this.currentRecipe < 0 || this.currentRecipe >= this.block.recipes.size) {
                this.currentRecipe = -1;
                this.progress = 0;
            }

            if (this.canCraft() && this.currentRecipe != -1) {
                this.progress += this.getProgressIncrease(this.block.recipes.get(currentRecipe).produceTime);
                this.totalProgress += this.delta();
                /*if (Mathf.chanceDelta((double)this.block.updateEffectChance)) {
                    GenericCrafter.this.updateEffect.at(this.getX() + Mathf.range((float)GenericCrafter.this.size * 4.0F), this.getY() + (float)Mathf.range(GenericCrafter.this.size * 4));
                }*/
            }

            if (this.progress >= 1.0F && this.currentRecipe != -1) {
                this.consume();
                if (getCurrentRecipe().outputItem != null) {
                    for (int i = 0; i < getCurrentRecipe().outputItem.amount; ++i) {
                        this.offload(getCurrentRecipe().outputItem.item);
                    }
                }
/*
                if (GenericCrafter.this.outputLiquid != null) {
                    this.handleLiquid(this, GenericCrafter.this.outputLiquid.liquid, GenericCrafter.this.outputLiquid.amount);
                }*/

                this.block.craftEffect.at(this.x, this.y);
                this.progress = 0.0F;
            }
            if (getCurrentRecipe().outputItem != null && this.timer(MultiCrafterBuild.this.block.timerDump, 5.0F)) {
                this.dump(getCurrentRecipe().outputItem.item);
            }


            if (this.currentRecipe == -1) this.sleep();


            /*if (GenericCrafter.this.outputLiquid != null) {
                this.dumpLiquid(GenericCrafter.this.outputLiquid.liquid);
            }*/

        }

        public Object config() {
            return this.currentRecipe;
        }

        public void write(Writes write) {
            super.write(write);
            write.i(this.currentRecipe);
        }

        public void playerPlaced(Object config) {
            if (MultiCrafter.this.lastConfig == null) MultiCrafter.this.lastConfig = -1;
            if (config == null) {
                if (!MultiCrafter.this.lastConfig.equals(-1)) this.configure(MultiCrafter.this.lastConfig);
            } else {
                this.configure(config);
            }
        }

        public void read(Reads read, byte revision) {
            super.read(read, revision);
            this.currentRecipe = read.i();
            if (this.currentRecipe < 0 || this.currentRecipe >= this.block.recipes.size) {
                this.currentRecipe = -1;
                this.progress = 0;
            }
        }

        public void resetProgress() {
            this.progress = 0f;
            this.totalProgress = 0f;
        }

        public void rebuildInfo() {
            rebuildBars.run();
            rebuildCons.run();
        }
    }
}