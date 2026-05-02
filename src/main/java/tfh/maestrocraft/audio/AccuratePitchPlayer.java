package tfh.maestrocraft.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import tfh.maestrocraft.midi.MidiParser;
import tfh.maestrocraft.midi.NoteEvent;

import java.util.*;
import java.util.concurrent.*;

public class AccuratePitchPlayer {
    private static ScheduledExecutorService scheduler;
    private static boolean isPlaying = false;

    /**
     * 播放MIDI（使用正确的音高）
     */
    public static void playMidiAsMCSounds(String midiPath) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) {
                System.err.println("客户端未就绪");
                return;
            }

            System.out.println("=== 开始播放MIDI（正确音高版）===");

            // 先显示音高计算信息
            PitchCalculator.showAllPitches();  // 添加这行

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

            // 显示总音符数
            System.out.println("\n总共发现 " + allNotes.size() + " 个音符");

            // 安排播放
            int playedCount = 0;
            Map<Integer, Integer> noteCountByPitch = new HashMap<>();

            for (NoteEvent note : allNotes) {
                if (!note.isNoteOn) continue;

                // 计算播放时间
                double startMs = (note.tick * microsecondsPerTick) / 1000.0;
                double durationSeconds = note.getDurationSeconds(microsecondsPerTick);
                if (durationSeconds <= 0) durationSeconds = 0.3;

                // 获取音效（根据音轨）
                SoundEvent sound = SafeSoundPlayer.getTrackSound(note.channel);

                // 计算正确的音高（使用推荐方法）
                float pitch = PitchCalculator.getCorrectPitch(note.key);

                // 长音符处理
                if (durationSeconds > 1.0) {
                    pitch *= 0.95f; // 稍微降低音高
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

                playedCount++;

                // 统计音符
                noteCountByPitch.put(note.key, noteCountByPitch.getOrDefault(note.key, 0) + 1);

                // 显示前20个音符的详细信息
                if (playedCount <= 20) {
                    String noteName = PitchCalculator.getNoteName(note.key);
                    System.out.println(String.format("%03d: %s (MIDI %d) -> pitch %.3f at %.2fs",
                            playedCount, noteName, note.key, pitch, startMs/1000));
                }
            }

            // 显示音符分布
            System.out.println("\n=== 音符分布 ===");
            List<Integer> sortedNotes = new ArrayList<>(noteCountByPitch.keySet());
            Collections.sort(sortedNotes);

            // 修复这里：需要遍历所有音符来计算平均持续时间
            Map<Integer, Double> noteDurationMap = new HashMap<>();
            for (NoteEvent note : allNotes) {
                if (!note.isNoteOn) continue;
                double duration = note.getDurationSeconds(microsecondsPerTick);
                if (duration <= 0) duration = 0.3;
                noteDurationMap.put(note.key, duration);
            }

            for (int note : sortedNotes) {
                int count = noteCountByPitch.get(note);
                if (count >= 3) { // 只显示出现3次以上的音符
                    String noteName = PitchCalculator.getNoteName(note);
                    // 使用该音符的平均持续时间或默认值
                    double avgDuration = noteDurationMap.getOrDefault(note, 0.5);
                    float pitch = PitchCalculator.getCorrectPitch(note);
                    System.out.println(String.format("  %s (%d): %d次, pitch=%.3f",
                            noteName, note, count, pitch));
                }
            }

            // 完成消息
            double totalTime = Math.max(3.0, (allNotes.get(allNotes.size()-1).tick * microsecondsPerTick) / 1_000_000.0);
            int finalPlayedCount = playedCount;
            scheduler.schedule(() -> {
                client.execute(() -> {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("§a播放完成！播放了 " + finalPlayedCount + " 个音符"),
                            false
                    );
                    isPlaying = false;
                });
                System.out.println("\n播放完成，总时长: " + totalTime + "秒");
            }, (long) (totalTime * 1000) + 1000, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            System.err.println("播放失败: " + e.getMessage());
            e.printStackTrace();
            stopPlaying();
        }
    }

    /**
     * 测试单个音符的音高
     */
    public static void testSingleNote(int midiNote) {
        // 修复这里：使用默认持续时间
        float pitch = PitchCalculator.getCorrectPitch(midiNote);
        String noteName = PitchCalculator.getNoteName(midiNote);

        System.out.println(String.format("测试: %s (MIDI %d) -> pitch %.3f",
                noteName, midiNote, pitch));

        // 播放测试音
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                SoundEvent sound = SafeSoundPlayer.getTrackSound(0);
                SafeSoundPlayer.playSound(sound, pitch, 0.5f);

                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§e测试音: " + noteName + " (pitch=" + String.format("%.3f", pitch) + ")"),
                        false
                );
            }
        } catch (Exception e) {
            System.err.println("测试播放失败: " + e.getMessage());
        }
    }

    /**
     * 测试音阶
     */
    public static void testScale() {
        System.out.println("=== 测试C大调音阶 ===");

        int[] scaleNotes = {60, 62, 64, 65, 67, 69, 71, 72}; // C D E F G A B C

        for (int note : scaleNotes) {
            testSingleNote(note);

            try {
                Thread.sleep(500); // 间隔500ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
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