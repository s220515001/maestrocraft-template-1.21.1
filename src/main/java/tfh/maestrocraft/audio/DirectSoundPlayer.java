package tfh.maestrocraft.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import tfh.maestrocraft.midi.MidiParser;
import tfh.maestrocraft.midi.NoteEvent;

import java.util.*;
import java.util.concurrent.*;

public class DirectSoundPlayer {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final Random RANDOM = Random.create();
    private static final Map<Integer, SoundEvent> ACTIVE_SOUND_MAPPING = new ConcurrentHashMap<>();

    // MC音效分类
    private static final List<SoundEvent> NOTE_SOUNDS = new ArrayList<>();
    private static final List<SoundEvent> EXPERIENCE_SOUNDS = new ArrayList<>();
    private static final List<SoundEvent> DOOR_SOUNDS = new ArrayList<>();
    private static final List<SoundEvent> FLINT_SOUNDS = new ArrayList<>();
    private static final List<SoundEvent> ANIMAL_SOUNDS = new ArrayList<>();
    private static final List<SoundEvent> MINING_SOUNDS = new ArrayList<>();
    private static final List<SoundEvent> TOOL_SOUNDS = new ArrayList<>();
    private static final List<SoundEvent> AMBIENT_SOUNDS = new ArrayList<>();
    private static final List<SoundEvent> ENTITY_SOUNDS = new ArrayList<>();

    static {
        initializeSoundEvents();
    }

    private static void initializeSoundEvents() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) return;

        // 获取声音注册表
        Registry<SoundEvent> registry = client.getNetworkHandler().getRegistryManager().get(Registries.SOUND_EVENT.getKey());

        // 音符盒音效
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.harp");
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.bass");
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.bell");
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.flute");
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.guitar");
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.xylophone");
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.iron_xylophone");
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.cow_bell");
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.didgeridoo");
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.bit");
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.banjo");
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.pling");
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.hat");
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.snare");
        addSoundIfPresent(registry, NOTE_SOUNDS, "block.note_block.basedrum");

        // 经验/魔法音效
        addSoundIfPresent(registry, EXPERIENCE_SOUNDS, "entity.experience_orb.pickup");
        addSoundIfPresent(registry, EXPERIENCE_SOUNDS, "block.enchantment_table.use");
        addSoundIfPresent(registry, EXPERIENCE_SOUNDS, "entity.evoker.cast_spell");

        // 门音效
        addSoundIfPresent(registry, DOOR_SOUNDS, "block.wooden_door.open");
        addSoundIfPresent(registry, DOOR_SOUNDS, "block.wooden_door.close");
        addSoundIfPresent(registry, DOOR_SOUNDS, "block.iron_door.open");
        addSoundIfPresent(registry, DOOR_SOUNDS, "block.iron_door.close");

        // 打火石音效
        addSoundIfPresent(registry, FLINT_SOUNDS, "item.flintandsteel.use");
        addSoundIfPresent(registry, FLINT_SOUNDS, "block.fire.ambient");

        // 动物音效
        addSoundIfPresent(registry, ANIMAL_SOUNDS, "entity.cow.ambient");
        addSoundIfPresent(registry, ANIMAL_SOUNDS, "entity.pig.ambient");
        addSoundIfPresent(registry, ANIMAL_SOUNDS, "entity.sheep.ambient");
        addSoundIfPresent(registry, ANIMAL_SOUNDS, "entity.chicken.ambient");

        // 挖掘音效
        addSoundIfPresent(registry, MINING_SOUNDS, "block.stone.break");
        addSoundIfPresent(registry, MINING_SOUNDS, "block.gravel.break");
        addSoundIfPresent(registry, MINING_SOUNDS, "block.wood.break");

        // 工具音效
        addSoundIfPresent(registry, TOOL_SOUNDS, "entity.arrow.hit");
        addSoundIfPresent(registry, TOOL_SOUNDS, "entity.arrow.shoot");
        addSoundIfPresent(registry, TOOL_SOUNDS, "item.shield.block");

        // 环境音效
        addSoundIfPresent(registry, AMBIENT_SOUNDS, "ambient.cave");
        addSoundIfPresent(registry, AMBIENT_SOUNDS, "weather.rain");

        // 实体音效
        addSoundIfPresent(registry, ENTITY_SOUNDS, "entity.player.attack.weak");
        addSoundIfPresent(registry, ENTITY_SOUNDS, "entity.player.attack.strong");
        addSoundIfPresent(registry, ENTITY_SOUNDS, "entity.player.levelup");

        System.out.println("初始化音效完成:");
        System.out.println("音符盒音效: " + NOTE_SOUNDS.size());
        System.out.println("经验音效: " + EXPERIENCE_SOUNDS.size());
        System.out.println("门音效: " + DOOR_SOUNDS.size());
        System.out.println("动物音效: " + ANIMAL_SOUNDS.size());
    }

    private static void addSoundIfPresent(Registry<SoundEvent> registry, List<SoundEvent> list, String soundId) {
        try {
            Identifier id = Identifier.of(soundId);
            SoundEvent soundEvent = registry.get(id);
            if (soundEvent != null) {
                list.add(soundEvent);
                System.out.println("添加音效: " + soundId);
            } else {
                System.err.println("找不到音效: " + soundId);
            }
        } catch (Exception e) {
            System.err.println("添加音效时出错 (" + soundId + "): " + e.getMessage());
        }
    }

    /**
     * 根据MIDI音高获取合适的音效列表
     */
    private static List<SoundEvent> getSoundListForNote(int midiNote, int velocity) {
        // 根据音高和力度选择音效类别
        if (midiNote >= 60 && midiNote <= 72) {
            // 中音区
            return velocity > 80 ? NOTE_SOUNDS : TOOL_SOUNDS;
        } else if (midiNote < 60) {
            // 低音区
            if (velocity > 90) return MINING_SOUNDS;
            else if (velocity > 60) return DOOR_SOUNDS;
            else return ANIMAL_SOUNDS;
        } else {
            // 高音区
            if (velocity > 85) return EXPERIENCE_SOUNDS;
            else if (velocity > 50) return FLINT_SOUNDS;
            else return AMBIENT_SOUNDS;
        }
    }

    /**
     * 获取音效（确保同一音高在一段时间内使用相同音效）
     */
    private static SoundEvent getSoundForNote(int noteKey, int velocity, long tick) {
        // 检查是否有活跃的映射
        SoundEvent existing = ACTIVE_SOUND_MAPPING.get(noteKey);

        if (existing != null) {
            // 如果已经有映射，使用相同的音效
            return existing;
        }

        // 获取适合的音效列表
        List<SoundEvent> soundList = getSoundListForNote(noteKey, velocity);

        if (soundList.isEmpty()) {
            // 回退到音符盒音效
            soundList = NOTE_SOUNDS;
            if (soundList.isEmpty()) {
                // 如果连音符盒音效都没有，返回null
                return null;
            }
        }

        // 随机选择一个音效
        SoundEvent selected = soundList.get(RANDOM.nextInt(soundList.size()));
        ACTIVE_SOUND_MAPPING.put(noteKey, selected);

        return selected;
    }

    /**
     * 计算音高
     */
    private static float calculatePitch(int midiNote, float basePitch) {
        // 标准音高：MIDI音符60(C5)对应pitch=1.0
        int semitones = midiNote - 60;
        return basePitch * (float) Math.pow(2.0, semitones / 12.0);
    }

    /**
     * 播放MIDI的MC音效版本
     */
    public static void playMidiAsMCSounds(String midiPath) throws Exception {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            throw new IllegalStateException("Minecraft client not available");
        }

        System.out.println("开始播放MC音效版本: " + midiPath);

        // 1. 解析MIDI
        MidiParser parser = new MidiParser();
        MidiParser.MidiData midiData = parser.parse(midiPath);

        // 2. 收集所有音符事件并排序
        List<NoteEvent> allNotes = new ArrayList<>();
        for (List<NoteEvent> trackNotes : midiData.tracks.values()) {
            allNotes.addAll(trackNotes);
        }

        // 按时间排序
        allNotes.sort(Comparator.comparingLong(n -> n.tick));

        // 3. 计算时间转换
        double microsecondsPerTick = (double) midiData.tempo / midiData.resolution;

        // 4. 获取玩家当前位置
        var player = client.player;

        // 5. 取消任何正在播放的音效
        scheduler.shutdownNow();

        // 6. 创建新的调度器
        ScheduledExecutorService newScheduler = Executors.newScheduledThreadPool(1);

        // 7. 安排每个音符的播放
        for (NoteEvent note : allNotes) {
            if (!note.isNoteOn) continue;

            // 计算延迟（秒）
            double delaySeconds = (note.tick * microsecondsPerTick) / 1_000_000.0;

            // 获取音效
            SoundEvent soundEvent = getSoundForNote(note.key, note.velocity, note.tick);
            if (soundEvent == null) {
                System.err.println("无法找到音效 for note: " + note.key);
                continue;
            }

            // 计算音高和音量
            float pitch = calculatePitch(note.key, 1.0f);
            float volume = note.velocity / 127.0f * 0.8f; // 降低总体音量

            // 安排播放任务
            newScheduler.schedule(() -> {
                client.execute(() -> {
                    try {
                        // 创建音效实例 - 使用新的API
                        var soundInstance = PositionedSoundInstance.master(
                                soundEvent,
                                pitch,
                                volume
                        );

                        client.getSoundManager().play(soundInstance);

                        System.out.println(String.format("播放: %s (音高: %.2f, 音量: %.2f, 音效: %s)",
                                note.getNoteName(), pitch, volume, soundEvent.getId().toString()));
                    } catch (Exception e) {
                        System.err.println("播放音效失败: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }, (long) (delaySeconds * 1000), TimeUnit.MILLISECONDS);
        }

        // 8. 清理 - 在最后一个音符后2秒清理
        if (!allNotes.isEmpty()) {
            long lastTick = allNotes.get(allNotes.size() - 1).tick;
            long cleanupDelay = (long) ((lastTick * microsecondsPerTick) / 1_000_000.0 * 1000) + 2000;

            newScheduler.schedule(() -> {
                ACTIVE_SOUND_MAPPING.clear();
                newScheduler.shutdown();
                client.player.sendMessage(net.minecraft.text.Text.literal("MC音效播放完成！"), false);
                System.out.println("MC音效播放完成");
            }, cleanupDelay, TimeUnit.MILLISECONDS);
        } else {
            newScheduler.shutdown();
        }
    }

    /**
     * 停止播放
     */
    public static void stopPlaying() {
        scheduler.shutdownNow();
        ACTIVE_SOUND_MAPPING.clear();
        MinecraftClient.getInstance().getSoundManager().stopAll();
    }
}