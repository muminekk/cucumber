package com.cucumber;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class Bomb {
    public static final String[] NAMES = {
            "Explosion", "TNT Fuse", "Creeper Hiss", "Thunder", "Dragon Growl",
            "Wither Spawn", "Anvil", "Bell", "Level Up", "Pling", "Villager No"
    };
    private static final String[] IDS = {
            "entity.generic.explode", "entity.tnt.primed", "entity.creeper.primed", "entity.lightning_bolt.thunder",
            "entity.ender_dragon.growl", "entity.wither.spawn", "block.anvil.land", "block.bell.use",
            "entity.player.levelup", "block.note_block.pling", "entity.villager.no"
    };

    private static int ticksLeft = -1;

    private Bomb() {}

    public static boolean armed() {
        return ticksLeft >= 0;
    }

    public static int secondsLeft() {
        return (ticksLeft + 19) / 20;
    }

    public static void start() {
        if (!armed()) ticksLeft = Config.bombDelay * 20;
    }

    public static void tick(MinecraftClient mc) {
        if (ticksLeft < 0) return;
        if (mc.world == null) {
            ticksLeft = -1;
            return;
        }
        if (ticksLeft == 0) {
            play(Config.bombSound);
            ticksLeft = -1;
        } else {
            ticksLeft--;
        }
    }

    public static void play(int index) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int i = Math.max(0, Math.min(IDS.length - 1, index));
        SoundEvent ev = SoundEvent.of(Identifier.ofVanilla(IDS[i]));
        mc.getSoundManager().play(PositionedSoundInstance.master(ev, 1.0f, 1.0f));
    }
}
