package tfh.maestrocraft.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import tfh.maestrocraft.midi.MidiParser;
import tfh.maestrocraft.midi.NoteEvent;

import java.util.*;
import java.util.concurrent.*;

public class UnifiedSoundPlayer {
    private static ScheduledExecutorService scheduler;
    private static final Random RANDOM = Random.create();
    private static final Map<Integer, ScheduledFuture<?>> ACTIVE_TASKS = new ConcurrentHashMap<>();

    // 所有音效都使用短音效（类似经验颗粒的短促音效）
    private static final List<String> SHORT_SOUNDS = Arrays.asList(
            // 经验/颗粒类音效（非常短促）
            "entity.experience_orb.pickup",
            "entity.experience_orb.touch",
            "block.note_block.harp",  // 音符盒竖琴（相对短的版本）
            "block.note_block.pling",
            "block.note_block.bell",
            "item.flintandsteel.use",
            "block.stone.break",
            "block.wood.break",
            "block.glass.break",
            "entity.arrow.hit",
            "entity.arrow.shoot",
            "item.shield.block",
            "block.wooden_door.open",
            "block.lever.click",
            "block.button.click",
            "block.dispenser.dispense",
            "block.comparator.click",
            "entity.player.attack.weak",
            "entity.player.attack.strong",
            "entity.cow.ambient",
            "entity.pig.ambient",
            "entity.sheep.ambient",
            "block.fire.extinguish",
            "entity.tnt.primed",
            "block.lava.pop",
            "block.anvil.land"
    );

    // 音轨音效分配（每个音轨固定使用一种音效）
    private static final Map<Integer, SoundEvent> TRACK_SOUNDS = new ConcurrentHashMap<>();

    // 音轨处理状态
    private static final Map<Integer, List<NoteEvent>> TRACK_NOTES = new ConcurrentHashMap<>();

    static {
        System.out.println("统一短音效播放器初始化，音效库: " + SHORT_SOUNDS.size() + " 种短音效");
    }

    /**
     * 获取音轨对应的固定音效
     */
    private static SoundEvent getTrackSound(int trackId) {
        if (TRACK_SOUNDS.containsKey(trackId)) {
            return TRACK_SOUNDS.get(trackId);
        }

        // 为每个音轨分配一个固定的短音效
        int soundIndex = trackId % SHORT_SOUNDS.size();
        String soundId = SHORT_SOUNDS.get(soundIndex);
        SoundEvent soundEvent = getSoundEvent(soundId);

        if (soundEvent == null) {
            // 如果获取失败，使用默认音效
            soundEvent = getSoundEvent("entity.experience_orb.pickup");
        }

        TRACK_SOUNDS.put(trackId, soundEvent);
        System.out.println("音轨 " + trackId + " 分配音效: " + soundId);
        return soundEvent;
    }

    /**
     * 获取音效事件
     */
    private static SoundEvent getSoundEvent(String soundId) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getNetworkHandler() != null) {
                Registry<SoundEvent> registry = client.getNetworkHandler().getRegistryManager()
                        .get(Registries.SOUND_EVENT.getKey());
                return registry.get(Identifier.of(soundId));
            }
        } catch (Exception e) {
            System.err.println("无法获取音效: " + soundId + " - " + e.getMessage());
        }
        return null;
    }

    /**
     * 统一音高计算方法（减慢速度而非循环）
     */
    private static float calculatePitch(int midiNote, double durationSeconds) {
        // 基础音高：MIDI 60(C5) = pitch 1.0
        int semitonesFromC5 = midiNote - 60;
        float basePitch = (float) Math.pow(2.0, semitonesFromC5 / 12.0);

        // 关键：长音符通过降低音高来模拟减慢效果
        if (durationSeconds > 1.0) {
            // 减慢效果：持续时间越长，音高越低
            float slowFactor = (float) (1.0 / Math.sqrt(1.0 + durationSeconds * 0.5));
            basePitch *= slowFactor;
        }

        // 确保在Minecraft有效范围内
        return Math.max(0.5f, Math.min(2.0f, basePitch));
    }

    /**
     * 计算短音效的播放次数（用于延长效果）
     */
    private static int calculateRepeatCount(double durationSeconds) {
        if (durationSeconds < 0.3) {
            return 1; // 非常短的音符只播放一次
        } else if (durationSeconds < 0.8) {
            return 2; // 中等长度播放2次
        } else {
            // 长音符：每0.5秒播放一次，但最多播放5次
            int repeat = (int) Math.ceil(durationSeconds / 0.5);
            return Math.min(5, Math.max(2, repeat));
        }
    }

    /**
     * 安排音轨的音符播放
     */
    private static void scheduleTrackNotes(int trackId, List<NoteEvent> notes,
                                           double microsecondsPerTick, int baseTaskId) {
        if (notes.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        // 获取该音轨的固定音效
        SoundEvent trackSound = getTrackSound(trackId);
        if (trackSound == null) return;

        System.out.println("处理音轨 " + trackId + ": " + notes.size() + " 个音符");

        int taskCounter = baseTaskId;

        for (NoteEvent note : notes) {
            if (!note.isNoteOn) continue;

            // 计算时间和持续时间
            double startSeconds = (note.tick * microsecondsPerTick) / 1_000_000.0;
            double durationSeconds = note.getDurationSeconds(microsecondsPerTick);
            if (durationSeconds <= 0) durationSeconds = 0.3;

            // 计算音高（包含减慢效果）
            float pitch = calculatePitch(note.key, durationSeconds);

            // 计算音量（基于力度）
            float volume = Math.min(0.8f, note.velocity / 127.0f * 0.7f);

            // 计算重复播放次数
            int repeatCount = calculateRepeatCount(durationSeconds);

            // 安排播放
            for (int i = 0; i < repeatCount; i++) {
                // 计算每次播放的延迟
                double playDelay = startSeconds * 1000;
                if (i > 0) {
                    // 后续播放适当延后，模拟延长效果
                    playDelay += (durationSeconds / repeatCount) * i * 1000;
                }

                // 后续播放的音量递减
                float currentVolume = volume * (1.0f - i * 0.15f);
                if (currentVolume < 0.1f) currentVolume = 0.1f;

                // 后续播放的音高微调（制造层次感）
                float currentPitch = pitch;
                if (i > 0) {
                    currentPitch *= (1.0f - i * 0.05f);
                }

                final float finalPitch = currentPitch;
                final float finalVolume = currentVolume;

                ScheduledFuture<?> task = scheduler.schedule(() -> {
                    client.execute(() -> {
                        try {
                            client.getSoundManager().play(
                                    PositionedSoundInstance.master(trackSound, finalPitch, finalVolume)
                            );
                        } catch (Exception e) {
                            // 静默处理错误
                        }
                    });
                }, (long) playDelay, TimeUnit.MILLISECONDS);

                ACTIVE_TASKS.put(taskCounter++, task);
            }

            // 调试信息（只显示前几个音符）
            if (note.tick < 1000) { // 只显示开始部分的音符
                System.out.println(String.format("  音轨%d: %s 持续%.2fs 音高%.2f 重复%d次",
                        trackId, note.getNoteName(), durationSeconds, pitch, repeatCount));
            }
        }
    }

    /**
     * 播放MIDI（统一短音效版）
     */
    public static void playMidiWithUnifiedSounds(String midiPath) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) {
                System.err.println("Minecraft客户端不可用");
                return;
            }

            System.out.println("=== 开始播放统一短音效版 ===");
            System.out.println("文件: " + midiPath);

            // 解析MIDI
            MidiParser parser = new MidiParser();
            MidiParser.MidiData midiData = parser.parse(midiPath);

            // 停止之前的播放
            stopPlaying();

            // 清理状态
            TRACK_SOUNDS.clear();
            TRACK_NOTES.clear();

            // 创建调度器（根据音轨数动态调整线程）
            int trackCount = midiData.tracks.size();
            scheduler = Executors.newScheduledThreadPool(Math.min(8, trackCount + 2));

            // 时间转换
            double microsecondsPerTick = (double) midiData.tempo / midiData.resolution;

            System.out.println("MIDI信息: " + trackCount + " 个音轨, " +
                    "分辨率: " + midiData.resolution + ", " +
                    "速度: " + midiData.tempo + " 微秒/四分音符");

            // 按音轨分别处理
            int baseTaskId = 0;
            int totalNotes = 0;

            for (Map.Entry<Integer, List<NoteEvent>> trackEntry : midiData.tracks.entrySet()) {
                int trackId = trackEntry.getKey();
                List<NoteEvent> notes = trackEntry.getValue();

                if (notes.isEmpty()) continue;

                // 按时间排序该音轨的音符
                notes.sort(Comparator.comparingLong(n -> n.tick));

                // 统计该音轨信息
                int noteCount = 0;
                for (NoteEvent note : notes) {
                    if (note.isNoteOn) noteCount++;
                }

                System.out.println("音轨 " + trackId + ": " + noteCount + " 个音符");

                // 安排该音轨的音符播放
                scheduleTrackNotes(trackId, notes, microsecondsPerTick, baseTaskId);

                totalNotes += noteCount;
                baseTaskId += 10000; // 为每个音轨预留足够的task ID空间

                // 保存音轨数据用于调试
                TRACK_NOTES.put(trackId, notes);
            }

            System.out.println("总计: " + totalNotes + " 个音符");

            // 计算总时长
            double maxDuration = 0;
            for (List<NoteEvent> notes : midiData.tracks.values()) {
                for (NoteEvent note : notes) {
                    if (note.isNoteOn) {
                        double noteEnd = (note.tick * microsecondsPerTick) / 1_000_000.0 +
                                note.getDurationSeconds(microsecondsPerTick);
                        maxDuration = Math.max(maxDuration, noteEnd);
                    }
                }
            }

            // 安排完成消息
            if (maxDuration > 0) {
                final double finalMaxDuration = maxDuration;
                scheduler.schedule(() -> {
                    client.execute(() -> {
                        client.player.sendMessage(
                                net.minecraft.text.Text.literal("§a统一短音效播放完成！"),
                                false
                        );
                    });
                    System.out.println("播放完成，总时长: " + finalMaxDuration + "秒");
                    System.out.println("音轨音效使用统计:");
                    for (Map.Entry<Integer, SoundEvent> entry : TRACK_SOUNDS.entrySet()) {
                        System.out.println("  音轨 " + entry.getKey() + ": " +
                                entry.getValue().getId().getPath());
                    }
                }, (long) (maxDuration * 1000) + 1000, TimeUnit.MILLISECONDS);
            }

            // 显示使用说明
            client.execute(() -> {
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§e正在播放: 统一短音效版（不使用循环音效）"),
                        false
                );
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§7特点: 全部短音效 + 音高减慢延长 + 多音轨独立"),
                        false
                );
            });

        } catch (Exception e) {
            System.err.println("播放失败: " + e.getMessage());
            e.printStackTrace();
            stopPlaying();
        }
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
                try {
                    if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                scheduler = null;
            }

            ACTIVE_TASKS.clear();
            TRACK_SOUNDS.clear();
            TRACK_NOTES.clear();

            // 停止所有声音
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.getSoundManager().stopAll();
            }

            System.out.println("已停止所有播放");

        } catch (Exception e) {
            System.err.println("停止播放失败: " + e.getMessage());
        }
    }

    /**
     * 测试短音效库
     */
    public static void testShortSounds() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        new Thread(() -> {
            try {
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§e开始测试短音效库..."),
                        false
                );

                for (int i = 0; i < Math.min(12, SHORT_SOUNDS.size()); i++) {
                    String soundId = SHORT_SOUNDS.get(i);
                    SoundEvent sound = getSoundEvent(soundId);

                    if (sound != null) {
                        final int index = i + 1;
                        final String finalSoundId = soundId;

                        client.execute(() -> {
                            // 以不同音高播放，展示效果
                            for (int p = 0; p < 3; p++) {
                                float pitch = 0.8f + p * 0.2f;
                                client.getSoundManager().play(
                                        PositionedSoundInstance.master(sound, pitch, 0.5f)
                                );
                            }

                            client.player.sendMessage(
                                    net.minecraft.text.Text.literal("§7" + index + ". " +
                                            finalSoundId.replace("entity.", "").replace("block.", "").replace(".", " ")),
                                    false
                            );
                        });

                        Thread.sleep(1000); // 1秒间隔
                    }
                }

                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§a短音效测试完成"),
                        false
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 获取当前播放状态
     */
    public static boolean isPlaying() {
        return scheduler != null && !scheduler.isShutdown() && !ACTIVE_TASKS.isEmpty();
    }
}