package tfh.maestrocraft.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

public class SafeSoundPlayer {
    private static final Random RANDOM = Random.create();

    // 音效ID列表（避免直接使用SoundEvents常量）
    private static final String[] SOUND_IDS = {
            // 音符盒音效
            "block.note_block.harp",
            "block.note_block.bass",
            "block.note_block.bell",
            "block.note_block.flute",
            "block.note_block.guitar",
            "block.note_block.xylophone",
            "block.note_block.iron_xylophone",
            "block.note_block.cow_bell",
            "block.note_block.didgeridoo",
            "block.note_block.bit",
            "block.note_block.banjo",
            "block.note_block.pling",
            "block.note_block.hat",
            "block.note_block.snare",
            "block.note_block.basedrum",

            // 其他音效
            "entity.experience_orb.pickup",
            "item.flintandsteel.use",
            "block.enchantment_table.use",
            "entity.cow.ambient",
            "entity.pig.ambient",
            "block.wooden_door.open",
            "entity.arrow.shoot"
    };

    /**
     * 安全的获取SoundEvent方法
     */
    public static SoundEvent getSafeSoundEvent(String soundId) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getNetworkHandler() == null) {
                return getDefaultSoundEvent();
            }

            Registry<SoundEvent> registry = client.getNetworkHandler().getRegistryManager().get(Registries.SOUND_EVENT.getKey());
            Identifier id = Identifier.of(soundId);
            SoundEvent soundEvent = registry.get(id);

            return soundEvent != null ? soundEvent : getDefaultSoundEvent();

        } catch (Exception e) {
            System.err.println("获取音效失败: " + soundId + " - " + e.getMessage());
            return getDefaultSoundEvent();
        }
    }

    /**
     * 获取默认音效（竖琴）
     */
    private static SoundEvent getDefaultSoundEvent() {
        return getSafeSoundEvent("block.note_block.harp");
    }

    /**
     * 获取音轨对应的音效
     */
    public static SoundEvent getTrackSound(int trackIndex) {
        int soundIndex = trackIndex % SOUND_IDS.length;
        return getSafeSoundEvent(SOUND_IDS[soundIndex]);
    }

    /**
     * 播放音效（安全方法）
     */
    public static void playSound(SoundEvent sound, float pitch, float volume) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;

            client.getSoundManager().play(
                    PositionedSoundInstance.master(sound, pitch, volume)
            );
        } catch (Exception e) {
            System.err.println("播放音效失败: " + e.getMessage());
        }
    }
}