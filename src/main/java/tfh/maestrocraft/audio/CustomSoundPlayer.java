package tfh.maestrocraft.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;
import tfh.maestrocraft.midi.MidiParser;
import tfh.maestrocraft.midi.NoteEvent;

import java.util.*;
import java.util.concurrent.*;

public class CustomSoundPlayer {
    private static ScheduledExecutorService scheduler;
    private static final Random RANDOM = Random.create();
    private static final Map<Integer, PlayingSound> ACTIVE_SOUNDS = new ConcurrentHashMap<>();
    private static final Map<Integer, SoundEvent> NOTE_TO_SOUND_MAPPING = new ConcurrentHashMap<>();

    // 音效资源（使用SoundEvents常量）
    private static final RegistryEntry<SoundEvent>[] NOTE_SOUNDS = new RegistryEntry[] {
            SoundEvents.BLOCK_NOTE_BLOCK_HARP,
            SoundEvents.BLOCK_NOTE_BLOCK_BASS,
            SoundEvents.BLOCK_NOTE_BLOCK_BELL,
            SoundEvents.BLOCK_NOTE_BLOCK_FLUTE,
            SoundEvents.BLOCK_NOTE_BLOCK_GUITAR,
            SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE,
            SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE,
            SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL,
            SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO,
            SoundEvents.BLOCK_NOTE_BLOCK_BIT,
            SoundEvents.BLOCK_NOTE_BLOCK_BANJO,
            SoundEvents.BLOCK_NOTE_BLOCK_PLING,
            SoundEvents.BLOCK_NOTE_BLOCK_HAT,
            SoundEvents.BLOCK_NOTE_BLOCK_SNARE,
            SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM
    };


    private static final SoundEvent[] EXPERIENCE_SOUNDS = {
            SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
            SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
            SoundEvents.ENTITY_EVOKER_CAST_SPELL
    };

    private static final SoundEvent[] DOOR_SOUNDS = {
            SoundEvents.BLOCK_WOODEN_DOOR_OPEN,
            SoundEvents.BLOCK_WOODEN_DOOR_CLOSE,
            SoundEvents.BLOCK_IRON_DOOR_OPEN,
            SoundEvents.BLOCK_IRON_DOOR_CLOSE
    };

    private static final SoundEvent[] FLINT_SOUNDS = {
            SoundEvents.ITEM_FLINTANDSTEEL_USE,
            SoundEvents.BLOCK_FIRE_AMBIENT
    };

    private static final SoundEvent[] ANIMAL_SOUNDS = {
            SoundEvents.ENTITY_COW_AMBIENT,
            SoundEvents.ENTITY_PIG_AMBIENT,
            SoundEvents.ENTITY_SHEEP_AMBIENT,
            SoundEvents.ENTITY_CHICKEN_AMBIENT,
            SoundEvents.ENTITY_WOLF_AMBIENT,
            SoundEvents.ENTITY_CAT_AMBIENT
    };

    private static final SoundEvent[] MINING_SOUNDS = {
            SoundEvents.BLOCK_STONE_BREAK,
            SoundEvents.BLOCK_GRAVEL_BREAK,
            SoundEvents.BLOCK_WOOD_BREAK,
            SoundEvents.BLOCK_GLASS_BREAK
    };

    private static final SoundEvent[] TOOL_SOUNDS = {
            SoundEvents.ENTITY_ARROW_HIT,
            SoundEvents.ENTITY_ARROW_SHOOT,
            SoundEvents.ITEM_SHIELD_BLOCK,
            SoundEvents.ITEM_AXE_STRIP
    };

    private static final SoundEvent[] ENTITY_SOUNDS = {
            SoundEvents.ENTITY_PLAYER_ATTACK_WEAK,
            SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
            SoundEvents.ENTITY_PLAYER_LEVELUP
    };

    // 音效最大持续时间（毫秒）
    private static final Map<SoundEvent, Integer> SOUND_DURATIONS = new HashMap<>();

    static {
        // 初始化音效持续时间
        // 音符盒音效：较短
        for (RegistryEntry<SoundEvent> sound : NOTE_SOUNDS) {
            SOUND_DURATIONS.put(sound.value(), 500);
        }

        // 经验音效：中等
        for (SoundEvent sound : EXPERIENCE_SOUNDS) {
            SOUND_DURATIONS.put(sound, 300);
        }

        // 门音效：裁剪到300ms
        for (SoundEvent sound : DOOR_SOUNDS) {
            SOUND_DURATIONS.put(sound, 300);
        }

        // 打火石音效：很短
        for (SoundEvent sound : FLINT_SOUNDS) {
            SOUND_DURATIONS.put(sound, 100);
        }

        // 动物叫声：裁剪到400ms
        for (SoundEvent sound : ANIMAL_SOUNDS) {
            SOUND_DURATIONS.put(sound, 400);
        }

        // 挖掘音效：中等
        for (SoundEvent sound : MINING_SOUNDS) {
            SOUND_DURATIONS.put(sound, 200);
        }

        // 工具音效：中等
        for (SoundEvent sound : TOOL_SOUNDS) {
            SOUND_DURATIONS.put(sound, 250);
        }

        // 实体音效：中等
        for (SoundEvent sound : ENTITY_SOUNDS) {
            SOUND_DURATIONS.put(sound, 300);
        }
    }

    /**
     * 可控制的音效实例，支持自定义停止
     */
    private static class ControllableSound extends AbstractSoundInstance implements TickableSoundInstance {
        private final long stopTime;
        private boolean done;

        public ControllableSound(SoundEvent sound, float volume, float pitch, long duration) {
            super(sound, SoundCategory.RECORDS, Random.create());
            this.volume = volume;
            this.pitch = pitch;
            this.stopTime = System.currentTimeMillis() + duration;
            this.done = false;
            this.relative = true; // 相对位置，跟随玩家
        }

        @Override
        public void tick() {
            if (System.currentTimeMillis() >= stopTime) {
                this.done = true;
            }
        }

        @Override
        public boolean isDone() {
            return done;
        }

        public void stop() {
            this.done = true;
        }
    }

    /**
     * 正在播放的音效记录
     */
    private static class PlayingSound {
        final ControllableSound sound;
        final ScheduledFuture<?> stopTask;

        PlayingSound(ControllableSound sound, ScheduledFuture<?> stopTask) {
            this.sound = sound;
            this.stopTask = stopTask;
        }
    }

    /**
     * 根据MIDI音高获取合适的音效类别
     */
    private static Object[] getSoundCategory(int midiNote, int velocity) {
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
            else return ENTITY_SOUNDS;
        }
    }

    /**
     * 获取音效（确保同一音高在一段时间内使用相同音效）
     */
    private static SoundEvent getSoundForNote(int noteKey, int velocity) {
        // 检查是否有映射
        SoundEvent existing = NOTE_TO_SOUND_MAPPING.get(noteKey);

        if (existing != null) {
            return existing;
        }

        // 获取适合的音效类别
        SoundEvent[] soundCategory = (SoundEvent[]) getSoundCategory(noteKey, velocity);

        // 随机选择一个音效
        SoundEvent selected = soundCategory[RANDOM.nextInt(soundCategory.length)];
        NOTE_TO_SOUND_MAPPING.put(noteKey, selected);

        return selected;
    }

    /**
     * 计算音高（支持全音域 0-127）
     */
    private static float calculatePitch(int midiNote, float basePitch) {
        // MIDI音符范围: 0-127
        // 标准音高：MIDI音符69(A4)对应440Hz，pitch=1.0

        int semitones = midiNote - 69; // A4 = 69
        float pitch = basePitch * (float) Math.pow(2.0, semitones / 12.0);

        // Minecraft音高范围限制在0.5-2.0之间
        return Math.max(0.5f, Math.min(2.0f, pitch));
    }

    /**
     * 计算音符持续时间（根据MIDI节奏）
     */
    private static long calculateDuration(int velocity, SoundEvent soundEvent) {
        // 基础持续时间
        int baseDuration = SOUND_DURATIONS.getOrDefault(soundEvent, 300);

        // 根据力度调整持续时间
        float velocityFactor = velocity / 127.0f;

        // 强音持续稍长，弱音持续稍短
        long duration = (long) (baseDuration * (0.7f + 0.6f * velocityFactor));

        return Math.max(50, Math.min(1000, duration)); // 限制在50ms-1000ms之间
    }

    /**
     * 播放MIDI的MC音效版本
     */
    public static void playMidiAsMCSounds(String midiPath) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) {
                System.err.println("Minecraft客户端不可用");
                return;
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

            if (allNotes.isEmpty()) {
                System.err.println("MIDI文件中没有音符");
                return;
            }

            // 按时间排序
            allNotes.sort(Comparator.comparingLong(n -> n.tick));

            // 3. 计算时间转换
            double microsecondsPerTick = (double) midiData.tempo / midiData.resolution;

            // 4. 停止之前的播放
            stopPlaying();

            // 5. 重置映射
            NOTE_TO_SOUND_MAPPING.clear();

            // 6. 创建新的调度器
            scheduler = Executors.newScheduledThreadPool(2);

            // 7. 安排每个音符的播放
            for (NoteEvent note : allNotes) {
                if (!note.isNoteOn) continue;

                // 计算延迟（秒）
                double delaySeconds = (note.tick * microsecondsPerTick) / 1_000_000.0;

                // 获取音效
                SoundEvent soundEvent = getSoundForNote(note.key, note.velocity);

                // 计算音高（支持全音域）
                float pitch = calculatePitch(note.key, 1.0f);

                // 计算音量
                float volume = Math.min(1.0f, note.velocity / 127.0f * 0.7f);

                // 计算持续时间（根据音效类型和力度）
                long duration = calculateDuration(note.velocity, soundEvent);

                // 安排播放任务
                scheduler.schedule(() -> {
                    client.execute(() -> {
                        try {
                            // 创建可控制的音效实例
                            ControllableSound sound = new ControllableSound(
                                    soundEvent, volume, pitch, duration
                            );

                            // 播放音效
                            client.getSoundManager().play(sound);

                            // 安排停止任务
                            ScheduledFuture<?> stopTask = scheduler.schedule(() -> {
                                sound.stop();
                                // 从活动音效中移除
                                ACTIVE_SOUNDS.values().removeIf(ps -> ps.sound == sound);
                            }, duration, TimeUnit.MILLISECONDS);

                            // 记录正在播放的音效
                            int soundId = System.identityHashCode(sound);
                            ACTIVE_SOUNDS.put(soundId, new PlayingSound(sound, stopTask));

                            // 调试信息
                            String noteName = note.getNoteName();
                            System.out.println(String.format("播放: %s (MIDI:%d) 音高:%.2fx 音量:%.2f 持续:%dms 音效:%s",
                                    noteName, note.key, pitch, volume, duration,
                                    soundEvent.getId().getPath()));

                        } catch (Exception e) {
                            System.err.println("播放音效失败: " + e.getMessage());
                        }
                    });
                }, (long) (delaySeconds * 1000), TimeUnit.MILLISECONDS);
            }

            // 8. 计算总时长并安排清理
            if (!allNotes.isEmpty()) {
                long lastTick = allNotes.get(allNotes.size() - 1).tick;
                double totalSeconds = (lastTick * microsecondsPerTick) / 1_000_000.0;
                long cleanupDelay = (long) (totalSeconds * 1000) + 2000;

                scheduler.schedule(() -> {
                    client.execute(() -> {
                        client.player.sendMessage(net.minecraft.text.Text.literal("§aMC音效播放完成！"), false);
                        System.out.println("MC音效播放完成，共播放 " + allNotes.size() + " 个音符");
                    });

                    // 清理
                    NOTE_TO_SOUND_MAPPING.clear();

                }, cleanupDelay, TimeUnit.MILLISECONDS);
            }

        } catch (Exception e) {
            System.err.println("播放MIDI失败: " + e.getMessage());
            e.printStackTrace();
            stopPlaying();
        }
    }

    /**
     * 停止所有播放
     */
    public static void stopPlaying() {
        try {
            // 停止所有调度任务
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdownNow();
                scheduler = null;
            }

            // 停止所有正在播放的音效
            for (PlayingSound playingSound : ACTIVE_SOUNDS.values()) {
                if (playingSound.stopTask != null && !playingSound.stopTask.isDone()) {
                    playingSound.stopTask.cancel(true);
                }
                if (playingSound.sound != null) {
                    playingSound.sound.stop();
                }
            }
            ACTIVE_SOUNDS.clear();

            // 停止所有声音
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.getSoundManager().stopAll();
            }

            // 清理映射
            NOTE_TO_SOUND_MAPPING.clear();

            System.out.println("已停止所有播放");

        } catch (Exception e) {
            System.err.println("停止播放失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前播放状态
     */
    public static boolean isPlaying() {
        return scheduler != null && !scheduler.isShutdown() && !ACTIVE_SOUNDS.isEmpty();
    }
}