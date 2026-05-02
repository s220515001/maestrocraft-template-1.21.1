package tfh.maestrocraft.audio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import tfh.maestrocraft.midi.MidiParser;
import tfh.maestrocraft.midi.NoteEvent;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DownloadManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");

    /**
     * 生成统一短音效版配置
     */
    public static SoundConfig generateUnifiedConfig(String midiPath) throws Exception {
        SoundConfig config = new SoundConfig();
        config.version = "1.0";
        config.name = Paths.get(midiPath).getFileName().toString().replace(".mid", "");
        config.type = "unified_short";
        config.description = "统一短音效版 - 全部使用短音效，音高减慢延长";

        // 解析MIDI
        MidiParser parser = new MidiParser();
        MidiParser.MidiData midiData = parser.parse(midiPath);
        double microsecondsPerTick = (double) midiData.tempo / midiData.resolution;

        // 收集所有音符
        List<NoteEvent> allNotes = new ArrayList<>();
        for (List<NoteEvent> trackNotes : midiData.tracks.values()) {
            allNotes.addAll(trackNotes);
        }

        // 按音轨分组处理
        Map<Integer, List<SoundNote>> trackNotesMap = new HashMap<>();

        for (NoteEvent note : allNotes) {
            if (!note.isNoteOn) continue;

            double startSeconds = (note.tick * microsecondsPerTick) / 1_000_000.0;
            double durationSeconds = note.getDurationSeconds(microsecondsPerTick);
            if (durationSeconds <= 0) durationSeconds = 0.3;

            // 计算音高（减慢效果）
            float pitch = calculatePitch(note.key, durationSeconds);

            // 计算重复次数
            int repeatCount = calculateRepeatCount(durationSeconds);

            // 为每个音轨分配固定音效
            String soundId = getTrackSoundId(note.channel);

            SoundNote soundNote = new SoundNote();
            soundNote.time = startSeconds;
            soundNote.duration = durationSeconds;
            soundNote.pitch = pitch;
            soundNote.volume = Math.min(0.8f, note.velocity / 127.0f * 0.7f);
            soundNote.repeat = repeatCount;
            soundNote.soundId = soundId;
            soundNote.noteName = note.getNoteName();

            if (!trackNotesMap.containsKey(note.channel)) {
                trackNotesMap.put(note.channel, new ArrayList<>());
            }
            trackNotesMap.get(note.channel).add(soundNote);
        }

        // 转换为音轨列表
        config.tracks = new ArrayList<>();
        for (Map.Entry<Integer, List<SoundNote>> entry : trackNotesMap.entrySet()) {
            SoundTrack track = new SoundTrack();
            track.id = entry.getKey();
            track.name = "音轨 " + entry.getKey();
            track.soundId = getTrackSoundId(entry.getKey());
            track.notes = entry.getValue();
            config.tracks.add(track);
        }

        // 统计信息
        config.totalNotes = allNotes.stream().filter(n -> n.isNoteOn).count();
        config.totalTracks = trackNotesMap.size();
        config.totalDuration = allNotes.stream()
                .filter(n -> n.isNoteOn)
                .mapToDouble(n -> (n.tick * microsecondsPerTick) / 1_000_000.0 + n.getDurationSeconds(microsecondsPerTick))
                .max().orElse(0);

        return config;
    }

    /**
     * 生成标准版配置
     */
    public static SoundConfig generateStandardConfig(String midiPath) throws Exception {
        SoundConfig config = new SoundConfig();
        config.version = "1.0";
        config.name = Paths.get(midiPath).getFileName().toString().replace(".mid", "");
        config.type = "standard";
        config.description = "标准版 - 主要使用音符盒音效";

        // 解析MIDI
        MidiParser parser = new MidiParser();
        MidiParser.MidiData midiData = parser.parse(midiPath);
        double microsecondsPerTick = (double) midiData.tempo / midiData.resolution;

        // 收集所有音符
        List<NoteEvent> allNotes = new ArrayList<>();
        for (List<NoteEvent> trackNotes : midiData.tracks.values()) {
            allNotes.addAll(trackNotes);
        }

        allNotes.sort(Comparator.comparingLong(n -> n.tick));

        // 生成音符列表
        config.tracks = new ArrayList<>();
        SoundTrack mainTrack = new SoundTrack();
        mainTrack.id = 0;
        mainTrack.name = "主音轨";
        mainTrack.soundId = "block.note_block.harp";
        mainTrack.notes = new ArrayList<>();

        for (NoteEvent note : allNotes) {
            if (!note.isNoteOn) continue;

            double startSeconds = (note.tick * microsecondsPerTick) / 1_000_000.0;
            double durationSeconds = note.getDurationSeconds(microsecondsPerTick);
            if (durationSeconds <= 0) durationSeconds = 0.3;

            float pitch = PitchCalculator.getCorrectPitch(note.key);

            SoundNote soundNote = new SoundNote();
            soundNote.time = startSeconds;
            soundNote.duration = durationSeconds;
            soundNote.pitch = pitch;
            soundNote.volume = Math.min(0.8f, note.velocity / 127.0f * 0.7f);
            soundNote.repeat = 1;
            soundNote.soundId = "block.note_block.harp";
            soundNote.noteName = note.getNoteName();

            mainTrack.notes.add(soundNote);
        }

        config.tracks.add(mainTrack);

        // 统计信息
        config.totalNotes = allNotes.stream().filter(n -> n.isNoteOn).count();
        config.totalTracks = 1;
        config.totalDuration = allNotes.stream()
                .filter(n -> n.isNoteOn)
                .mapToDouble(n -> (n.tick * microsecondsPerTick) / 1_000_000.0 + n.getDurationSeconds(microsecondsPerTick))
                .max().orElse(0);

        return config;
    }

    /**
     * 生成增强版配置
     */
    public static SoundConfig generateEnhancedConfig(String midiPath) throws Exception {
        SoundConfig config = new SoundConfig();
        config.version = "1.0";
        config.name = Paths.get(midiPath).getFileName().toString().replace(".mid", "");
        config.type = "enhanced";
        config.description = "增强版 - 多种音效混合（门、打火石、方块破坏等）";

        // 解析MIDI
        MidiParser parser = new MidiParser();
        MidiParser.MidiData midiData = parser.parse(midiPath);
        double microsecondsPerTick = (double) midiData.tempo / midiData.resolution;

        // 增强版音效类别
        Map<String, List<String>> soundCategories = new HashMap<>();
        soundCategories.put("low", Arrays.asList("block.stone.break", "block.wood.break", "entity.cow.ambient"));
        soundCategories.put("mid", Arrays.asList("block.note_block.harp", "block.note_block.bell", "block.note_block.pling"));
        soundCategories.put("high", Arrays.asList("item.flintandsteel.use", "entity.experience_orb.pickup", "block.fire.extinguish"));
        soundCategories.put("percussion", Arrays.asList("block.wooden_door.open", "block.lever.click", "entity.arrow.shoot"));

        // 收集所有音符
        List<NoteEvent> allNotes = new ArrayList<>();
        for (List<NoteEvent> trackNotes : midiData.tracks.values()) {
            allNotes.addAll(trackNotes);
        }

        allNotes.sort(Comparator.comparingLong(n -> n.tick));

        // 按音高范围分组
        Map<String, List<SoundNote>> categoryNotes = new HashMap<>();

        for (NoteEvent note : allNotes) {
            if (!note.isNoteOn) continue;

            double startSeconds = (note.tick * microsecondsPerTick) / 1_000_000.0;
            double durationSeconds = note.getDurationSeconds(microsecondsPerTick);
            if (durationSeconds <= 0) durationSeconds = 0.3;

            float pitch = PitchCalculator.getCorrectPitch(note.key);

            // 根据音高选择音效类别
            String category;
            if (note.key < 48) category = "low";
            else if (note.key < 72) category = "mid";
            else if (note.key < 96) category = "high";
            else category = "percussion";

            // 选择具体音效
            List<String> sounds = soundCategories.get(category);
            int soundIndex = Math.abs(note.key * 13 + note.velocity * 7) % sounds.size();
            String soundId = sounds.get(soundIndex);

            SoundNote soundNote = new SoundNote();
            soundNote.time = startSeconds;
            soundNote.duration = durationSeconds;
            soundNote.pitch = pitch;
            soundNote.volume = Math.min(0.8f, note.velocity / 127.0f * 0.7f);
            soundNote.repeat = durationSeconds > 1.0 ? 2 : 1;
            soundNote.soundId = soundId;
            soundNote.noteName = note.getNoteName();
            soundNote.category = category;

            if (!categoryNotes.containsKey(category)) {
                categoryNotes.put(category, new ArrayList<>());
            }
            categoryNotes.get(category).add(soundNote);
        }

        // 转换为音轨列表
        config.tracks = new ArrayList<>();
        int trackId = 0;
        for (Map.Entry<String, List<SoundNote>> entry : categoryNotes.entrySet()) {
            SoundTrack track = new SoundTrack();
            track.id = trackId++;
            track.name = getCategoryName(entry.getKey());
            track.soundCategory = entry.getKey();
            track.notes = entry.getValue();
            config.tracks.add(track);
        }

        // 统计信息
        config.totalNotes = allNotes.stream().filter(n -> n.isNoteOn).count();
        config.totalTracks = categoryNotes.size();
        config.totalDuration = allNotes.stream()
                .filter(n -> n.isNoteOn)
                .mapToDouble(n -> (n.tick * microsecondsPerTick) / 1_000_000.0 + n.getDurationSeconds(microsecondsPerTick))
                .max().orElse(0);

        return config;
    }

    /**
     * 下载配置文件
     */
    public static boolean downloadConfig(SoundConfig config, String outputPath) {
        try {
            String json = GSON.toJson(config);

            // 确保目录存在
            Path outputDir = Paths.get(outputPath).getParent();
            if (outputDir != null) {
                Files.createDirectories(outputDir);
            }

            // 写入文件
            Files.write(Paths.get(outputPath), json.getBytes());

            System.out.println("配置文件已保存: " + outputPath);
            return true;

        } catch (Exception e) {
            System.err.println("保存配置文件失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 下载并播放测试版
     */
    public static void downloadAndTest(String midiPath, String type) {
        new Thread(() -> {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client == null || client.player == null) return;

                String fileName = Paths.get(midiPath).getFileName().toString().replace(".mid", "");
                String timestamp = DATE_FORMAT.format(new Date());
                String downloadsDir = Paths.get(
                        client.runDirectory.getAbsolutePath(),
                        "downloads", "maestrocraft"
                ).toString();

                // 生成配置
                SoundConfig config;
                String configType;

                switch (type) {
                    case "unified":
                        config = generateUnifiedConfig(midiPath);
                        configType = "统一短音效版";
                        break;
                    case "enhanced":
                        config = generateEnhancedConfig(midiPath);
                        configType = "增强版";
                        break;
                    default:
                        config = generateStandardConfig(midiPath);
                        configType = "标准版";
                        break;
                }

                // 保存配置文件
                String outputPath = Paths.get(downloadsDir,
                        fileName + "_" + type + "_" + timestamp + ".json").toString();

                boolean success = downloadConfig(config, outputPath);

                if (success) {
                    // 显示成功消息
                    client.execute(() -> {
                        client.player.sendMessage(
                                net.minecraft.text.Text.literal("§a" + configType + "配置已下载！"),
                                false
                        );
                        client.player.sendMessage(
                                net.minecraft.text.Text.literal("§7路径: " + outputPath),
                                false
                        );
                        client.player.sendMessage(
                                net.minecraft.text.Text.literal("§7音轨: " + config.totalTracks +
                                        ", 音符: " + config.totalNotes +
                                        ", 时长: " + String.format("%.1f", config.totalDuration) + "秒"),
                                false
                        );
                    });

                    // 自动播放测试
                    Thread.sleep(1000);
                    testConfig(config);

                } else {
                    client.execute(() -> {
                        client.player.sendMessage(
                                net.minecraft.text.Text.literal("§c下载失败，请检查日志"),
                                false
                        );
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    client.execute(() -> {
                        client.player.sendMessage(
                                net.minecraft.text.Text.literal("§c生成配置失败: " + e.getMessage()),
                                false
                        );
                    });
                }
            }
        }).start();
    }

    /**
     * 测试配置文件
     */
    private static void testConfig(SoundConfig config) {
        new Thread(() -> {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client == null || client.player == null) return;

                client.execute(() -> {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("§e开始测试播放: " + config.description),
                            false
                    );
                });

                // 创建一个简单的播放器来测试配置
                playConfigTest(config);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 播放配置测试
     */
    private static void playConfigTest(SoundConfig config) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        // 简单测试：播放每个音轨的前3个音符
        for (SoundTrack track : config.tracks) {
            if (track.notes.size() > 0) {
                System.out.println("测试音轨 " + track.name + ": " + track.notes.size() + " 个音符");

                for (int i = 0; i < Math.min(3, track.notes.size()); i++) {
                    SoundNote note = track.notes.get(i);
                    final SoundEvent sound = SoundUtil.getSoundEvent(note.soundId);
                    if (sound != null) {
                        client.execute(() -> {
                            client.getSoundManager().play(
                                    net.minecraft.client.sound.PositionedSoundInstance.master(
                                            sound, note.pitch, note.volume
                                    )
                            );
                        });

                        try {
                            Thread.sleep(500); // 500ms间隔
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }

        client.execute(() -> {
            client.player.sendMessage(
                    net.minecraft.text.Text.literal("§a配置测试完成"),
                    false
            );
        });
    }

    /**
     * 辅助方法
     */
    private static float calculatePitch(int midiNote, double durationSeconds) {
        int semitonesFromC5 = midiNote - 60;
        float basePitch = (float) Math.pow(2.0, semitonesFromC5 / 12.0);

        if (durationSeconds > 1.0) {
            float slowFactor = (float) (1.0 / Math.sqrt(1.0 + durationSeconds * 0.5));
            basePitch *= slowFactor;
        }

        return Math.max(0.5f, Math.min(2.0f, basePitch));
    }

    private static int calculateRepeatCount(double durationSeconds) {
        if (durationSeconds < 0.3) return 1;
        else if (durationSeconds < 0.8) return 2;
        else {
            int repeat = (int) Math.ceil(durationSeconds / 0.5);
            return Math.min(5, Math.max(2, repeat));
        }
    }

    private static String getTrackSoundId(int trackId) {
        String[] shortSounds = {
                "entity.experience_orb.pickup",
                "block.note_block.harp",
                "block.note_block.pling",
                "item.flintandsteel.use",
                "block.stone.break",
                "block.wooden_door.open",
                "entity.arrow.shoot",
                "entity.cow.ambient"
        };
        return shortSounds[trackId % shortSounds.length];
    }

    private static String getCategoryName(String category) {
        switch (category) {
            case "low": return "低音部";
            case "mid": return "中音部";
            case "high": return "高音部";
            case "percussion": return "打击乐部";
            default: return "音轨";
        }
    }

    /**
     * 配置类
     */
    public static class SoundConfig {
        public String version;
        public String name;
        public String type;
        public String description;
        public List<SoundTrack> tracks;
        public long totalNotes;
        public int totalTracks;
        public double totalDuration;
    }

    public static class SoundTrack {
        public int id;
        public String name;
        public String soundId;
        public String soundCategory;
        public List<SoundNote> notes;
    }

    public static class SoundNote {
        public double time;
        public double duration;
        public float pitch;
        public float volume;
        public int repeat;
        public String soundId;
        public String noteName;
        public String category;
    }
}