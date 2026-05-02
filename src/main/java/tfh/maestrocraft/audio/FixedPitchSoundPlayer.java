package tfh.maestrocraft.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import tfh.maestrocraft.midi.MidiParser;
import tfh.maestrocraft.midi.NoteEvent;

import java.util.*;
import java.util.concurrent.*;

public class FixedPitchSoundPlayer {
    private static ScheduledExecutorService scheduler;
    private static boolean isPlaying = false;

    /**
     * 唯一的音高计算方法
     */
    public static float getPitch(int midiNote, double durationSeconds) {
        return PitchCalculator.getPitchWithDuration(midiNote, durationSeconds);
    }

    /**
     * 播放MIDI（使用修复后的音高）
     */
    public static void playMidiAsMCSounds(String midiPath) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) {
                System.err.println("客户端未就绪");
                return;
            }

            System.out.println("=== 开始播放MIDI（修复音高版）===");

            // 显示音高信息
            PitchCalculator.showAllPitches();
            PitchTester.testActualMinecraftPitch();

            // 停止之前播放
            stopPlaying();

            // 解析MIDI
            MidiParser parser = new MidiParser();
            MidiParser.MidiData midiData = parser.parse(midiPath);

            // 时间转换
            double microsecondsPerTick = (double) midiData.tempo / midiData.resolution;

            // 创建调度器
            scheduler = Executors.newScheduledThreadPool(4);
            isPlaying = true;

            // 收集和排序所有音符
            List<NoteEvent> allNotes = new ArrayList<>();
            for (List<NoteEvent> trackNotes : midiData.tracks.values()) {
                allNotes.addAll(trackNotes);
            }

            if (allNotes.isEmpty()) {
                System.err.println("没有找到音符");
                return;
            }

            allNotes.sort(Comparator.comparingLong(n -> n.tick));

            // 显示MIDI信息
            System.out.println("\n=== MIDI文件信息 ===");
            System.out.println("总音轨数: " + midiData.tracks.size());
            System.out.println("总音符数: " + allNotes.size());
            System.out.println("分辨率: " + midiData.resolution);
            System.out.println("速度: " + midiData.tempo + " 微秒/四分音符");
            System.out.println("微秒每tick: " + microsecondsPerTick);

            // 统计音符范围
            int minNote = 127, maxNote = 0;
            for (NoteEvent note : allNotes) {
                if (note.isNoteOn) {
                    minNote = Math.min(minNote, note.key);
                    maxNote = Math.max(maxNote, note.key);
                }
            }
            System.out.println("音符范围: " + minNote + " - " + maxNote +
                    " (" + PitchCalculator.getNoteName(minNote) + " - " +
                    PitchCalculator.getNoteName(maxNote) + ")");

            // 安排播放
            int totalNotes = 0;
            int outOfRangeNotes = 0;

            for (NoteEvent note : allNotes) {
                if (!note.isNoteOn) continue;

                // 计算时间
                double startMs = (note.tick * microsecondsPerTick) / 1000.0;
                double durationSeconds = note.getDurationSeconds(microsecondsPerTick);
                if (durationSeconds <= 0) durationSeconds = 0.3;

                // 获取音效
                SoundEvent sound = SafeSoundPlayer.getTrackSound(note.channel);

                // 计算音高
                float pitch = getPitch(note.key, durationSeconds);

                // 检查是否超出Minecraft范围
                if (pitch < 0.5f || pitch > 2.0f) {
                    outOfRangeNotes++;
                    System.out.println(String.format("警告: 音符 %s (MIDI %d) pitch=%.3f 超出范围",
                            PitchCalculator.getNoteName(note.key), note.key, pitch));
                }

                // 计算音量
                float volume = Math.min(0.8f, note.velocity / 127.0f * 0.7f);

                // 安排播放
                float finalPitch = pitch;
                scheduler.schedule(() -> {
                    client.execute(() -> {
                        SafeSoundPlayer.playSound(sound, finalPitch, volume);
                    });
                }, (long) startMs, TimeUnit.MILLISECONDS);

                totalNotes++;

                // 显示前10个音符的详细信息
                if (totalNotes <= 10) {
                    String noteName = PitchCalculator.getNoteName(note.key);
                    System.out.println(String.format("音符%03d: %s (MIDI %d) -> 时间%.2fs 音高%.3f",
                            totalNotes, noteName, note.key, startMs/1000, pitch));
                }
            }

            System.out.println("\n=== 播放统计 ===");
            System.out.println("总共安排: " + totalNotes + " 个音符");
            System.out.println("超出范围: " + outOfRangeNotes + " 个音符");

            // 完成消息
            double totalTime = Math.max(3.0, (allNotes.get(allNotes.size()-1).tick * microsecondsPerTick) / 1_000_000.0);
            scheduler.schedule(() -> {
                client.execute(() -> {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("§a播放完成！音高已正确修复"),
                            false
                    );
                    isPlaying = false;
                });
                System.out.println("播放完成，总时长: " + totalTime + "秒");
            }, (long) (totalTime * 1000) + 1000, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            System.err.println("播放失败: " + e.getMessage());
            e.printStackTrace();
            stopPlaying();
        }
    }

    /**
     * 停止播放
     */
    public static void stopPlaying() {
        try {
            isPlaying = false;

            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdownNow();
                scheduler = null;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.getSoundManager().stopAll();
            }

        } catch (Exception e) {
            System.err.println("停止播放时出错: " + e.getMessage());
        }
    }

    /**
     * 是否正在播放
     */
    public static boolean isPlaying() {
        return isPlaying;
    }
}