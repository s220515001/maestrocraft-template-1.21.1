package tfh.maestrocraft.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class SoundUtil {
    private static final Map<String, SoundEvent> SOUND_CACHE = new HashMap<>();

    /**
     * 通过ID获取SoundEvent，处理RegistryEntry转换
     */
    public static SoundEvent getSoundEvent(String soundId) {
        // 检查缓存
        if (SOUND_CACHE.containsKey(soundId)) {
            return SOUND_CACHE.get(soundId);
        }

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getNetworkHandler() == null) {
                System.err.println("无法获取Minecraft客户端");
                return getDefaultSound();
            }

            // 获取声音注册表
            Registry<SoundEvent> registry = client.getNetworkHandler().getRegistryManager().get(Registries.SOUND_EVENT.getKey());

            // 通过ID获取SoundEvent
            Identifier id = Identifier.of(soundId);
            SoundEvent soundEvent = registry.get(id);

            if (soundEvent == null) {
                System.err.println("找不到音效: " + soundId);
                soundEvent = getDefaultSound();
            }

            // 缓存结果
            SOUND_CACHE.put(soundId, soundEvent);
            return soundEvent;

        } catch (Exception e) {
            System.err.println("获取音效失败 (" + soundId + "): " + e.getMessage());
            return getDefaultSound();
        }
    }

    /**
     * 获取默认音效（音符盒竖琴）
     */
    private static SoundEvent getDefaultSound() {
        return getSoundEvent("block.note_block.harp");
    }

    /**
     * 清除音效缓存
     */
    public static void clearCache() {
        SOUND_CACHE.clear();
    }
}