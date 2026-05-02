package tfh.maestrocraft.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;
import tfh.maestrocraft.midi.MidiParser;
import tfh.maestrocraft.midi.NoteEvent;

import java.util.*;
import java.util.concurrent.*;

public class MultiTrackSoundPlayer {
    private static ScheduledExecutorService scheduler;
    private static final Random RANDOM = Random.create();
    private static final Map<Integer, ScheduledFuture<?>> ACTIVE_TASKS = new ConcurrentHashMap<>();
    private static boolean isPlaying = false;

    // 不同音轨使用不同的短音效ID（全部短促）
    private static final String[][] TRACK_SOUND_IDS = {
            // 音轨1：钢琴类音效（短促）
            {
                    "block.note_block.harp",
                    "block.note_block.pling",
                    "block.note_block.bell"
            },
            // 音轨2：弦乐类音效
            {
                    "block.note_block.guitar",
                    "block.note_block.banjo",
                    "block.note_block.bit"
            },
            // 音轨3：打击乐类音效
            {
                    "block.note_block.bass",
                    "block.note_block.snare",
                    "block.note_block.basedrum"
            },
            // 音轨4：管乐类音效
            {
                    "block.note_block.flute",
                    "block.note_block.didgeridoo",
                    "block.note_block.iron_xylophone"
            },
            // 音轨5：特殊音效
            {
                    "entity.experience_orb.pickup",
                    "item.flintandsteel.use",
                    "block.enchantment_table.use"
            },
            // 音轨6：环境音效
            {
                    "entity.cow.ambient",
                    "entity.pig.ambient",
                    "block.wooden_door.open"
            }
    };

    // 缓存音轨音效
    private static final Map<Integer, SoundEvent[]> TRACK_SOUND_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取音轨的音效数组
     */
    private static SoundEvent[] getSoundsForTrack(int trackIndex) {
        // 检查缓存
        if (TRACK_SOUND_CACHE.containsKey(trackIndex)) {
            return TRACK_SOUND_CACHE.get(trackIndex);
        }

        // 获取音效ID数组
        String[] soundIds = TRACK_SOUND_IDS[trackIndex % TRACK_SOUND_IDS.length];

        // 转换为SoundEvent数组
        List<SoundEvent> soundList = new ArrayList<>();
        for (String soundId : soundIds) {
            SoundEvent sound = SoundUtil.getSoundEvent(soundId);
            if (sound != null) {
                soundList.add(sound);
            }
        }

        // 确保至少有一个音效
        if (soundList.isEmpty()) {
            soundList.add(SoundUtil.getSoundEvent("block.note_block.harp"));
        }

        SoundEvent[] sounds = soundList.toArray(new SoundEvent[0]);
        TRACK_SOUND_CACHE.put(trackIndex, sounds);

        return sounds;
    }

    /**
     * 计算音高（支持全音域 0-127，并考虑延长效果）
     */
    private static float calculatePitch(int midiNote, double durationSeconds) {
        // 使用统一的音高计算方法
        return PitchCalculator.getCorrectPitch(midiNote);
    }

    /**
     * 计算音符应该播放的次数（对于长音符）
     */
    private static int calculateRepeatTimes(double durationSeconds) {
        if (durationSeconds < 0.3) {
            return 1; // 短音符只播放一次
        } else if (durationSeconds < 1.0) {
            return 2; // 中等长度播放2次
        } else {
            // 长音符：每0.4秒播放一次（比之前稍长，减少重复次数）
            return Math.max(2, (int) (durationSeconds / 0.4));
        }
    }

    /**
     * 计算重复播放的时间间隔
     */
    private static double calculateRepeatInterval(double durationSeconds, int repeatTimes) {
        if (repeatTimes <= 1) {
            return 0;
        }
        // 平均分配时间间隔，稍长一些避免太密集
        return durationSeconds / repeatTimes;
    }

    /**
     * 为音轨选择音效（同一音轨使用相同的音效类型）
     */
    private static SoundEvent selectSoundForTrack(int trackIndex, int noteKey) {
        SoundEvent[] trackSounds = getSoundsForTrack(trackIndex);

        // 确保同一音轨使用相同的音效类型
        // 使用音轨索引和音符值的组合来决定，但同一音轨内保持一致
        int soundIndex = Math.abs(trackIndex * 37 + (noteKey / 12) * 13) % trackSounds.length;
        return trackSounds[soundIndex];
    }

    /**
     * 处理单个音符的播放（支持延长效果）
     */
    private static void scheduleNotePlayback(MinecraftClient client, NoteEvent note,
                                             int trackIndex, double microsecondsPerTick,
                                             int baseTaskId) {

        // 计算开始时间
        double startSeconds = (note.tick * microsecondsPerTick) / 1_000_000.0;
        double durationSeconds = note.getDurationSeconds(microsecondsPerTick);
        if (durationSeconds <= 0) {
            durationSeconds = 0.3; // 默认0.3秒
        }

        // 为音轨选择音效
        SoundEvent sound = selectSoundForTrack(trackIndex, note.key);
        if (sound == null) {
            System.err.println("无法获取音效 for track " + trackIndex);
            return;
        }

        // 计算音高（考虑延长效果）
        float pitch = calculatePitch(note.key, durationSeconds);

        // 计算音量
        float volume = Math.min(0.8f, note.velocity / 127.0f * 0.7f);

        // 计算播放次数和时间间隔
        int repeatTimes = calculateRepeatTimes(durationSeconds);
        double repeatInterval = calculateRepeatInterval(durationSeconds, repeatTimes);

        // 安排播放
        for (int i = 0; i < repeatTimes; i++) {
            double playDelay = startSeconds * 1000 + i * repeatInterval * 1000;
            float currentPitch = pitch;
            float currentVolume = volume;

            // 后续播放稍微调整音量和音高以增加层次感
            if (i > 0) {
                currentVolume *= (0.9f - i * 0.1f); // 逐渐减小音量
                currentPitch *= (1.0f - i * 0.03f); // 稍微降低音高
            }

            final float finalPitch = currentPitch;
            final float finalVolume = Math.max(0.1f, currentVolume); // 确保最小音量

            ScheduledFuture<?> task = scheduler.schedule(() -> {
                client.execute(() -> {
                    try {
                        client.getSoundManager().play(
                                PositionedSoundInstance.master(
                                        sound,
                                        finalPitch,
                                        finalVolume
                                )
                        );
                    } catch (Exception e) {
                        // 静默处理错误
                    }
                });
            }, (long) playDelay, TimeUnit.MILLISECONDS);

            ACTIVE_TASKS.put(baseTaskId * 1000 + i, task);
        }

        // 调试信息
        if (repeatTimes > 1) {
            System.out.println(String.format("音轨%d: %s 持续%.2fs 音高%.2f 播放%d次",
                    trackIndex, note.getNoteName(), durationSeconds, pitch, repeatTimes));
        }
    }

    /**
     * 播放MIDI的MC音效版本（多音轨处理）
     */
    public static void playMidiAsMCSounds(String midiPath) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) {
                System.err.println("Minecraft客户端不可用");
                return;
            }

            System.out.println("开始播放MC音效版本（多音轨）: " + midiPath);

            // 1. 解析MIDI
            MidiParser parser = new MidiParser();
            MidiParser.MidiData midiData = parser.parse(midiPath);

            // 2. 停止之前的播放
            stopPlaying();

            // 3. 清理缓存
            SoundUtil.clearCache();
            TRACK_SOUND_CACHE.clear();

            // 4. 创建新的调度器
            scheduler = Executors.newScheduledThreadPool(6); // 为多音轨准备
            isPlaying = true;

            // 5. 计算时间转换
            double microsecondsPerTick = (double) midiData.tempo / midiData.resolution;

            // 6. 分别处理每个音轨
            int baseTaskId = 0;
            int trackCount = midiData.tracks.size();

            System.out.println("发现 " + trackCount + " 个音轨");

            for (Map.Entry<Integer, List<NoteEvent>> trackEntry : midiData.tracks.entrySet()) {
                int trackIndex = trackEntry.getKey();
                List<NoteEvent> notes = trackEntry.getValue();

                if (notes.isEmpty()) continue;

                // 按开始时间排序
                notes.sort(Comparator.comparingLong(n -> n.tick));

                // 获取该音轨的音效类型
                SoundEvent[] trackSounds = getSoundsForTrack(trackIndex);
                System.out.println(String.format("音轨%d: %d个音符，使用%d种音效",
                        trackIndex, notes.size(), trackSounds.length));

                // 安排该音轨的所有音符
                for (NoteEvent note : notes) {
                    if (!note.isNoteOn) continue;

                    scheduleNotePlayback(client, note, trackIndex, microsecondsPerTick, baseTaskId++);
                }
            }

            // 7. 计算总时长
            double maxDuration = 0;
            for (List<NoteEvent> notes : midiData.tracks.values()) {
                for (NoteEvent note : notes) {
                    double noteEnd = (note.tick * microsecondsPerTick) / 1_000_000.0 +
                            note.getDurationSeconds(microsecondsPerTick);
                    maxDuration = Math.max(maxDuration, noteEnd);
                }
            }

            // 8. 安排播放完成消息
            if (maxDuration > 0) {
                double finalMaxDuration = maxDuration;
                scheduler.schedule(() -> {
                    client.execute(() -> {
                        client.player.sendMessage(
                                net.minecraft.text.Text.literal("§aMC音效播放完成！"),
                                false
                        );
                        isPlaying = false;
                    });
                    System.out.println("多音轨播放完成，总时长: " + finalMaxDuration + "秒");
                }, (long) (maxDuration * 1000) + 1000, TimeUnit.MILLISECONDS);
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
            isPlaying = false;

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
            TRACK_SOUND_CACHE.clear();

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
     * 获取当前播放状态
     */
    public static boolean isPlaying() {
        return isPlaying;
    }
}