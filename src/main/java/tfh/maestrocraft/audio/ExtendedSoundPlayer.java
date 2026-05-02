package tfh.maestrocraft.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import tfh.maestrocraft.midi.MidiParser;
import tfh.maestrocraft.midi.NoteEvent;

import java.util.*;
import java.util.concurrent.*;

public class ExtendedSoundPlayer {
    private static ScheduledExecutorService scheduler;
    private static final Random RANDOM = Random.create();
    private static final Map<Integer, ScheduledFuture<?>> ACTIVE_TASKS = new ConcurrentHashMap<>();
    private static final Map<Integer, SoundInstance> ACTIVE_SOUNDS = new ConcurrentHashMap<>();
    private static final Map<Integer, SoundEvent> NOTE_SOUND_MAP = new ConcurrentHashMap<>();

    // 使用SoundEvent而不是RegistryEntry
    private static final SoundEvent[] SUSTAIN_SOUNDS;
    private static final SoundEvent[] SHORT_SOUNDS;

    static {
        // 获取实际的SoundEvent对象
        SUSTAIN_SOUNDS = new SoundEvent[] {
                getSoundEvent("block.note_block.harp"),
                getSoundEvent("block.note_block.flute"),
                getSoundEvent("block.enchantment_table.use"),
                getSoundEvent("block.fire.ambient"),
                getSoundEvent("weather.rain"),
                getSoundEvent("entity.evoker.cast_spell")
        };

        SHORT_SOUNDS = new SoundEvent[] {
                getSoundEvent("block.note_block.bass"),
                getSoundEvent("block.note_block.bell"),
                getSoundEvent("item.flintandsteel.use"),
                getSoundEvent("block.stone.break"),
                getSoundEvent("block.wooden_door.open"),
                getSoundEvent("entity.arrow.shoot"),
                getSoundEvent("entity.player.attack.weak"),
                getSoundEvent("entity.cow.ambient"),
                getSoundEvent("entity.pig.ambient")
        };

        System.out.println("初始化音效：延长音" + SUSTAIN_SOUNDS.length + "个，短促音" + SHORT_SOUNDS.length + "个");
    }

    /**
     * 通过ID获取SoundEvent
     */
    private static SoundEvent getSoundEvent(String id) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getNetworkHandler() != null) {
                Registry<SoundEvent> registry = client.getNetworkHandler().getRegistryManager().get(Registries.SOUND_EVENT.getKey());
                return registry.get(Identifier.of(id));
            }
        } catch (Exception e) {
            System.err.println("无法获取音效: " + id + " - " + e.getMessage());
        }
        return null;
    }

    /**
     * 清理null音效
     */
    private static SoundEvent[] cleanSoundArray(SoundEvent[] array) {
        List<SoundEvent> list = new ArrayList<>();
        for (SoundEvent sound : array) {
            if (sound != null) {
                list.add(sound);
            }
        }
        return list.toArray(new SoundEvent[0]);
    }

    /**
     * 根据音符持续时间选择合适的音效类别
     */
    private static SoundEvent[] getSoundCategory(double durationSeconds) {
        SoundEvent[] sustain = cleanSoundArray(SUSTAIN_SOUNDS);
        SoundEvent[] shortSounds = cleanSoundArray(SHORT_SOUNDS);

        if (sustain.length == 0) sustain = shortSounds;
        if (shortSounds.length == 0) shortSounds = sustain;

        if (durationSeconds > 0.5) {
            // 长音符使用延长音音效
            return sustain;
        } else {
            // 短音符使用短促音音效
            return shortSounds;
        }
    }

    /**
     * 获取音效（智能选择）
     */
    private static SoundEvent getSoundForNote(int noteKey, int velocity, double durationSeconds) {
        // 检查是否有缓存
        Integer cacheKey = noteKey * 1000 + (int)(durationSeconds * 10);
        SoundEvent cached = NOTE_SOUND_MAP.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 根据持续时间选择音效类别
        SoundEvent[] soundCategory = getSoundCategory(durationSeconds);

        if (soundCategory.length == 0) {
            // 回退到默认音效
            return getSoundEvent("block.note_block.harp");
        }

        // 根据音符和力度选择音效
        int index = (noteKey * 13 + velocity * 7 + (int)(durationSeconds * 100)) % soundCategory.length;
        SoundEvent selected = soundCategory[index];

        if (selected == null) {
            selected = soundCategory[0]; // 防止null
        }

        // 缓存选择
        NOTE_SOUND_MAP.put(cacheKey, selected);

        return selected;
    }

    /**
     * 计算音高（支持全音域 0-127）
     */
    private static float calculatePitch(int midiNote) {
        // MIDI音符范围: 0-127
        // 标准音高：MIDI音符69(A4)对应440Hz，pitch=1.0

        int semitones = midiNote - 69; // A4 = 69
        float pitch = (float) Math.pow(2.0, semitones / 12.0);

        // Minecraft音高范围限制在0.5-2.0之间
        return Math.max(0.5f, Math.min(2.0f, pitch));
    }

    /**
     * 创建循环音效（用于延长音）
     */
    private static SoundInstance createLoopingSound(SoundEvent soundEvent, float pitch, float volume) {
        if (soundEvent == null) {
            soundEvent = getSoundEvent("block.note_block.harp");
        }

        return new PositionedSoundInstance(
                soundEvent.getId(),
                SoundCategory.RECORDS,
                volume,
                pitch,
                Random.create(),
                false,
                0,
                SoundInstance.AttenuationType.NONE,
                0.0, 0.0, 0.0,
                true // 循环播放
        );
    }

    /**
     * 创建普通音效（单次播放）
     */
    private static SoundInstance createOneShotSound(SoundEvent soundEvent, float pitch, float volume) {
        if (soundEvent == null) {
            soundEvent = getSoundEvent("block.note_block.harp");
        }

        return PositionedSoundInstance.master(
                soundEvent,
                pitch,
                volume
        );
    }

    /**
     * 播放MIDI的MC音效版本（支持延长音）
     */
    public static void playMidiAsMCSounds(String midiPath) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) {
                System.err.println("Minecraft客户端不可用");
                return;
            }

            System.out.println("开始播放MC音效版本（支持延长音）: " + midiPath);

            // 1. 解析MIDI（包含note on/off）
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

            // 按开始时间排序
            allNotes.sort(Comparator.comparingLong(n -> n.tick));

            // 3. 计算时间转换
            double microsecondsPerTick = (double) midiData.tempo / midiData.resolution;

            // 4. 停止之前的播放
            stopPlaying();

            // 5. 创建新的调度器
            scheduler = Executors.newScheduledThreadPool(4);

            // 6. 清理缓存
            NOTE_SOUND_MAP.clear();

            // 7. 安排每个音符的播放
            int taskId = 0;
            for (NoteEvent note : allNotes) {
                if (!note.isNoteOn) continue;

                // 计算开始时间（秒）
                double startSeconds = (note.tick * microsecondsPerTick) / 1_000_000.0;

                // 计算持续时间（秒）
                double durationSeconds = note.getDurationSeconds(microsecondsPerTick);
                if (durationSeconds <= 0) {
                    durationSeconds = 0.5; // 默认0.5秒
                }

                // 获取音效（考虑持续时间）
                SoundEvent soundEvent = getSoundForNote(note.key, note.velocity, durationSeconds);

                // 计算音高
                float pitch = calculatePitch(note.key);

                // 计算音量
                float volume = Math.min(1.0f, note.velocity / 127.0f * 0.6f);

                // 调试信息
                System.out.println(String.format("安排音符: %s 开始:%.2fs 持续:%.2fs 音高:%.2f 音量:%.2f",
                        note.getNoteName(), startSeconds, durationSeconds, pitch, volume));

                // 根据持续时间选择播放策略
                if (durationSeconds > 1.0 && soundEvent != null) {
                    // 长音符：使用循环音效
                    scheduleLongNote(client, note, startSeconds, durationSeconds, soundEvent, pitch, volume, taskId++);
                } else if (soundEvent != null) {
                    // 短音符：普通播放
                    scheduleShortNote(client, note, startSeconds, durationSeconds, soundEvent, pitch, volume, taskId++);
                }
            }

            // 8. 安排播放完成消息
            if (!allNotes.isEmpty()) {
                long lastEndTick = 0;
                for (NoteEvent note : allNotes) {
                    long noteEnd = note.endTick > 0 ? note.endTick : note.tick + midiData.resolution;
                    lastEndTick = Math.max(lastEndTick, noteEnd);
                }

                double totalSeconds = (lastEndTick * microsecondsPerTick) / 1_000_000.0;

                scheduler.schedule(() -> {
                    client.execute(() -> {
                        client.player.sendMessage(
                                net.minecraft.text.Text.literal("§aMC音效播放完成！支持延长音"),
                                false
                        );
                    });
                    System.out.println("MC音效播放完成，总时长: " + totalSeconds + "秒");
                }, (long) (totalSeconds * 1000) + 1000, TimeUnit.MILLISECONDS);
            }

        } catch (Exception e) {
            System.err.println("播放MIDI失败: " + e.getMessage());
            e.printStackTrace();
            stopPlaying();
        }
    }

    /**
     * 安排长音符播放（使用循环音效）
     */
    private static void scheduleLongNote(MinecraftClient client, NoteEvent note,
                                         double startSeconds, double durationSeconds,
                                         SoundEvent soundEvent,
                                         float pitch, float volume, int taskId) {

        // 安排开始播放
        ScheduledFuture<?> startTask = scheduler.schedule(() -> {
            client.execute(() -> {
                try {
                    // 创建循环音效
                    SoundInstance sound = createLoopingSound(soundEvent, pitch, volume);
                    client.getSoundManager().play(sound);

                    // 记录活跃音效
                    ACTIVE_SOUNDS.put(taskId, sound);

                    System.out.println(String.format("开始长音符: %s 持续:%.2fs 音高:%.2f 循环音效",
                            note.getNoteName(), durationSeconds, pitch));

                } catch (Exception e) {
                    System.err.println("播放循环音效失败: " + e.getMessage());
                }
            });
        }, (long) (startSeconds * 1000), TimeUnit.MILLISECONDS);

        ACTIVE_TASKS.put(taskId, startTask);

        // 安排停止播放
        ScheduledFuture<?> stopTask = scheduler.schedule(() -> {
            client.execute(() -> {
                SoundInstance sound = ACTIVE_SOUNDS.remove(taskId);
                if (sound != null) {
                    client.getSoundManager().stop(sound);
                    System.out.println("停止长音符: " + note.getNoteName());
                }
            });
        }, (long) ((startSeconds + durationSeconds) * 1000), TimeUnit.MILLISECONDS);

        // 记录停止任务
        ACTIVE_TASKS.put(taskId + 100000, stopTask);
    }

    /**
     * 安排短音符播放（普通播放）
     */
    private static void scheduleShortNote(MinecraftClient client, NoteEvent note,
                                          double startSeconds, double durationSeconds,
                                          SoundEvent soundEvent,
                                          float pitch, float volume, int taskId) {

        // 安排播放
        ScheduledFuture<?> playTask = scheduler.schedule(() -> {
            client.execute(() -> {
                try {
                    // 创建普通音效
                    SoundInstance sound = createOneShotSound(soundEvent, pitch, volume);
                    client.getSoundManager().play(sound);

                    System.out.println(String.format("播放短音符: %s 持续:%.2fs 音高:%.2f",
                            note.getNoteName(), durationSeconds, pitch));

                } catch (Exception e) {
                    System.err.println("播放音效失败: " + e.getMessage());
                }
            });
        }, (long) (startSeconds * 1000), TimeUnit.MILLISECONDS);

        ACTIVE_TASKS.put(taskId, playTask);
    }

    /**
     * 停止所有播放
     */
    public static void stopPlaying() {
        try {
            // 取消所有任务
            if (scheduler != null && !scheduler.isShutdown()) {
                for (ScheduledFuture<?> task : ACTIVE_TASKS.values()) {
                    if (task != null && !task.isDone()) {
                        task.cancel(false);
                    }
                }
                scheduler.shutdownNow();
                scheduler = null;
            }

            ACTIVE_TASKS.clear();

            // 停止所有声音
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                // 停止所有活跃音效
                for (SoundInstance sound : ACTIVE_SOUNDS.values()) {
                    if (sound != null) {
                        client.getSoundManager().stop(sound);
                    }
                }
                client.getSoundManager().stopAll();
            }

            ACTIVE_SOUNDS.clear();
            NOTE_SOUND_MAP.clear();

            System.out.println("已停止所有播放（包括延长音）");

        } catch (Exception e) {
            System.err.println("停止播放失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前播放状态
     */
    public static boolean isPlaying() {
        return scheduler != null && !scheduler.isShutdown() && !ACTIVE_TASKS.isEmpty();
    }
}