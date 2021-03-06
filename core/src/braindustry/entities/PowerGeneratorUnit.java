package braindustry.entities;

import arc.util.io.Reads;
import arc.util.io.Writes;
import braindustry.gen.ModEntityMapping;
import mindustry.gen.UnitWaterMove;
import mindustry.world.Tile;

public class PowerGeneratorUnit extends UnitWaterMove {
    public PowerGeneratorUnit() {
        super();
    }

    public static PowerGeneratorUnit create() {
        PowerGeneratorUnit powerGeneratorUnit = new PowerGeneratorUnit();
        return powerGeneratorUnit;
    }




    @Override
    public void afterSync() {
        super.afterSync();
    }

    @Override
    public void move(float cx, float cy) {
        super.move(cx, cy);
    }

    @Override
    public void draw() {
        super.draw();
        Tile tile = this.tileOn();
//        Draw.z(z + 0.1f);
        /*this.links.each(link -> {
            ((PowerUnitType) this.type).drawLaser(this.team, this.x, this.y, link.x, link.y, 1, link.block.size);
        });*/
    }

    public String toString() {
        return "PowerGeneratorUnit#" + this.id;
    }



    @Override
    public void update() {
        super.update();
//        if (this.type instanceof PowerUnitType) {
//            ((PowerUnitType) type).update(this);
//        }
    }

    @Override
    public void write(Writes write) {
        super.write(write);
    }

    @Override
    public void read(Reads read) {
        super.read(read);
    }

    public boolean serialize() {
        return true;
    }



    @Override
    public void clearCommand() {
        super.clearCommand();
    }

    @Override
    public void kill() {
        super.kill();
    }

    @Override
    public void remove() {
        super.remove();
    }

    @Override
    public int classId() {
        return ModEntityMapping.getId(getClass());
    }
}
