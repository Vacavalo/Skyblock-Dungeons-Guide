package kr.syeyoung.dungeonsguide.roomprocessor.bombdefuse.chambers.creeper;

import kr.syeyoung.dungeonsguide.roomprocessor.bombdefuse.RoomProcessorBombDefuseSolver;
import kr.syeyoung.dungeonsguide.roomprocessor.bombdefuse.chambers.BDChamber;
import kr.syeyoung.dungeonsguide.roomprocessor.bombdefuse.chambers.GeneralDefuseChamberProcessor;
import kr.syeyoung.dungeonsguide.utils.RenderUtils;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.awt.*;

public class CreeperRightProcessor extends GeneralDefuseChamberProcessor {
    public CreeperRightProcessor(RoomProcessorBombDefuseSolver solver, BDChamber chamber) {
        super(solver, chamber);

        poses = new BlockPos[9];
        for (int i = 0; i < 9; i++) {
            poses[i] = chamber.getBlockPos(3+(i%3), 1, 1+(i/3));
        }
        center = chamber.getBlockPos(4,4,4);
    }

    @Override
    public String getName() {
        return "creeperRight";
    }


    private int answer = -1;
    private BlockPos[] poses;
    private BlockPos center;
    @Override
    public void tick() {
        super.tick();
        if (answer != -1) return;
        answer = 0;
        for (int i = 0; i < poses.length; i++) {
            BlockPos pos = poses[i];
            if (getChamber().getRoom().getContext().getWorld().getBlockState(pos).getBlock() == Blocks.air) {
                answer |= (1 << i);
            }
        }
    }

    @Override
    public void drawWorld(float partialTicks) {
        super.drawWorld(partialTicks);
        RenderUtils.drawTextAtWorld(answer == -1 ? "Answer not received yet. Visit left room to obtain solution" : "" , center.getX()+ 0.5f, center.getY(), center.getZ()+ 0.5f, 0xFFFFFFFF, 0.03F, false, false, partialTicks);
        if (answer != -1) {
            for (int i = 0; i < 9; i++) {
                if (((answer >> i) & 0x01) != 0) {
                    RenderUtils.highlightBlock(poses[answer], Color.green, partialTicks, false);
                }
            }
        }
    }

    @Override
    public void onInteractBlock(PlayerInteractEvent event) {
        BlockPos pos = event.pos;
        World w = event.world;

        if (answer == -1) return;

        if (w.getBlockState(pos).getBlock() == Blocks.wooden_button) {
            for (int i = 0; i < 9; i++) {
                if (poses[answer] == pos && ((answer >> i) & 0x01) == 0) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @Override
    public void onSendData() {
        if (answer == -1) return;
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("a", "b");
        nbt.setInteger("b", answer);
        getSolver().communicate(nbt);
    }

    @Override
    public void onDataRecieve(NBTTagCompound compound) {
        if ("b".equals(compound.getString("a"))) {
            answer = compound.getInteger("b");
        }
    }
}
