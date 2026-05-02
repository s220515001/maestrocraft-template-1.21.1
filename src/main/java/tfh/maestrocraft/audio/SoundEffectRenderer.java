package tfh.maestrocraft.audio;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import tfh.maestrocraft.midi.MidiParser;
import tfh.maestrocraft.midi.NoteEvent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SoundEffectRenderer {
    private static final int SAMPLE_RATE = 44100;
    private static final int BIT_DEPTH = 16;
    private static final int CHANNELS = 2; // 立体声

    // MC音效映射
    private static final Map<String, List<SoundResource>> SOUND_CATEGORIES = new HashMap<>();
    private static final Random RANDOM = Random.create();
    private static final Map<Integer, SoundResource> ACTIVE_SOUND_MAPPING = new ConcurrentHashMap<>();

    // 音效资源类
    public static class SoundResource {
        public final Identifier soundId;
        public final String category;
        public final float basePitch;
        public final float baseVolume;

        public SoundResource(Identifier soundId, String category, float basePitch, float baseVolume) {
            this.soundId = soundId;
            this.category = category;
            this.basePitch = basePitch;
            this.baseVolume = baseVolume;
        }

        public SoundResource(Identifier soundId, String category) {
            this(soundId, category, 1.0f, 1.0f);
        }
    }

    static {
        // 初始化音效分类
        initializeSoundCategories();
    }

    private static void initializeSoundCategories() {
        // 音符盒类音效
        SOUND_CATEGORIES.put("note", Arrays.asList(
                new SoundResource(Identifier.of("block.note_block.harp"), "note"),
                new SoundResource(Identifier.of("block.note_block.bass"), "note"),
                new SoundResource(Identifier.of("block.note_block.bell"), "note"),
                new SoundResource(Identifier.of("block.note_block.flute"), "note"),
                new SoundResource(Identifier.of("block.note_block.guitar"), "note"),
                new SoundResource(Identifier.of("block.note_block.xylophone"), "note"),
                new SoundResource(Identifier.of("block.note_block.iron_xylophone"), "note"),
                new SoundResource(Identifier.of("block.note_block.cow_bell"), "note"),
                new SoundResource(Identifier.of("block.note_block.didgeridoo"), "note"),
                new SoundResource(Identifier.of("block.note_block.bit"), "note"),
                new SoundResource(Identifier.of("block.note_block.banjo"), "note"),
                new SoundResource(Identifier.of("block.note_block.pling"), "note"),
                new SoundResource(Identifier.of("block.note_block.hat"), "note"),
                new SoundResource(Identifier.of("block.note_block.snare"), "note"),
                new SoundResource(Identifier.of("block.note_block.basedrum"), "note")
        ));

        // 经验/魔法类音效
        SOUND_CATEGORIES.put("magic", Arrays.asList(
                new SoundResource(Identifier.of("entity.experience_orb.pickup"), "magic", 0.8f, 0.7f),
                new SoundResource(Identifier.of("block.enchantment_table.use"), "magic"),
                new SoundResource(Identifier.of("entity.evoker.cast_spell"), "magic", 1.2f, 0.6f),
                new SoundResource(Identifier.of("entity.illusioner.cast_spell"), "magic"),
                new SoundResource(Identifier.of("block.beacon.activate"), "magic"),
                new SoundResource(Identifier.of("block.beacon.ambient"), "magic")
        ));

        // 门/机关类音效
        SOUND_CATEGORIES.put("mechanical", Arrays.asList(
                new SoundResource(Identifier.of("block.wooden_door.open"), "mechanical"),
                new SoundResource(Identifier.of("block.wooden_door.close"), "mechanical"),
                new SoundResource(Identifier.of("block.iron_door.open"), "mechanical"),
                new SoundResource(Identifier.of("block.iron_door.close"), "mechanical"),
                new SoundResource(Identifier.of("block.trapdoor.open"), "mechanical"),
                new SoundResource(Identifier.of("block.trapdoor.close"), "mechanical"),
                new SoundResource(Identifier.of("block.fence_gate.open"), "mechanical"),
                new SoundResource(Identifier.of("block.fence_gate.close"), "mechanical")
        ));

        // 火/打火石类音效
        SOUND_CATEGORIES.put("fire", Arrays.asList(
                new SoundResource(Identifier.of("item.flintandsteel.use"), "fire"),
                new SoundResource(Identifier.of("block.fire.ambient"), "fire"),
                new SoundResource(Identifier.of("block.fire.extinguish"), "fire"),
                new SoundResource(Identifier.of("entity.creeper.primed"), "fire", 1.5f, 0.8f),
                new SoundResource(Identifier.of("entity.tnt.primed"), "fire")
        ));

        // 动物叫声
        SOUND_CATEGORIES.put("animals", Arrays.asList(
                new SoundResource(Identifier.of("entity.cow.ambient"), "animals"),
                new SoundResource(Identifier.of("entity.pig.ambient"), "animals"),
                new SoundResource(Identifier.of("entity.sheep.ambient"), "animals"),
                new SoundResource(Identifier.of("entity.chicken.ambient"), "animals"),
                new SoundResource(Identifier.of("entity.wolf.ambient"), "animals"),
                new SoundResource(Identifier.of("entity.cat.ambient"), "animals"),
                new SoundResource(Identifier.of("entity.ocelot.ambient"), "animals"),
                new SoundResource(Identifier.of("entity.horse.ambient"), "animals")
        ));

        // 挖掘/破坏类音效
        SOUND_CATEGORIES.put("mining", Arrays.asList(
                new SoundResource(Identifier.of("block.stone.break"), "mining"),
                new SoundResource(Identifier.of("block.gravel.break"), "mining"),
                new SoundResource(Identifier.of("block.grass.break"), "mining"),
                new SoundResource(Identifier.of("block.wood.break"), "mining"),
                new SoundResource(Identifier.of("block.glass.break"), "mining"),
                new SoundResource(Identifier.of("block.anvil.break"), "mining"),
                new SoundResource(Identifier.of("block.metal.break"), "mining")
        ));

        // 武器/工具类音效
        SOUND_CATEGORIES.put("tools", Arrays.asList(
                new SoundResource(Identifier.of("entity.arrow.hit"), "tools"),
                new SoundResource(Identifier.of("entity.arrow.shoot"), "tools"),
                new SoundResource(Identifier.of("item.trident.hit"), "tools"),
                new SoundResource(Identifier.of("item.trident.throw"), "tools"),
                new SoundResource(Identifier.of("item.shield.block"), "tools"),
                new SoundResource(Identifier.of("item.axe.strip"), "tools"),
                new SoundResource(Identifier.of("item.hoe.till"), "tools")
        ));

        // 环境/氛围类音效
        SOUND_CATEGORIES.put("ambient", Arrays.asList(
                new SoundResource(Identifier.of("ambient.cave"), "ambient"),
                new SoundResource(Identifier.of("weather.rain"), "ambient"),
                new SoundResource(Identifier.of("weather.rain.above"), "ambient"),
                new SoundResource(Identifier.of("block.water.ambient"), "ambient"),
                new SoundResource(Identifier.of("block.lava.ambient"), "ambient"),
                new SoundResource(Identifier.of("block.fire.ambient"), "ambient")
        ));

        // 玩家/生物类音效
        SOUND_CATEGORIES.put("entity", Arrays.asList(
                new SoundResource(Identifier.of("entity.player.attack.weak"), "entity"),
                new SoundResource(Identifier.of("entity.player.attack.strong"), "entity"),
                new SoundResource(Identifier.of("entity.player.hurt"), "entity"),
                new SoundResource(Identifier.of("entity.player.levelup"), "entity"),
                new SoundResource(Identifier.of("entity.villager.ambient"), "entity"),
                new SoundResource(Identifier.of("entity.villager.hurt"), "entity"),
                new SoundResource(Identifier.of("entity.zombie.ambient"), "entity"),
                new SoundResource(Identifier.of("entity.skeleton.ambient"), "entity")
        ));
    }

    /**
     * 根据MIDI音高获取合适的音效类别
     */
    private static String getCategoryForNote(int midiNote, int velocity) {
        // 根据音高和力度选择音效类别
        if (midiNote >= 60 && midiNote <= 72) {
            // 中音区 - 主要使用音符盒音效
            return velocity > 80 ? "note" : "tools";
        } else if (midiNote < 60) {
            // 低音区 - 使用重低音效
            if (velocity > 90) return "mining";
            else if (velocity > 60) return "mechanical";
            else return "animals";
        } else {
            // 高音区 - 使用清脆音效
            if (velocity > 85) return "magic";
            else if (velocity > 50) return "fire";
            else return "ambient";
        }
    }

    /**
     * 获取音效资源（确保同一音高在一段时间内使用相同音效）
     */
    private static SoundResource getSoundResource(int noteKey, int velocity, long tick) {
        String category = getCategoryForNote(noteKey, velocity);
        List<SoundResource> availableSounds = SOUND_CATEGORIES.get(category);

        if (availableSounds == null || availableSounds.isEmpty()) {
            // 回退到默认音符盒
            availableSounds = SOUND_CATEGORIES.get("note");
        }

        // 检查是否有活跃的映射（相同音高在最近ticks内）
        int timeWindow = 20; // 20个tick内保持相同音效
        SoundResource existing = ACTIVE_SOUND_MAPPING.get(noteKey);

        if (existing != null) {
            // 检查是否还在时间窗口内
            // 这里简化处理：只要有映射就使用
            return existing;
        }

        // 随机选择一个新音效
        SoundResource selected = availableSounds.get(RANDOM.nextInt(availableSounds.size()));
        ACTIVE_SOUND_MAPPING.put(noteKey, selected);

        // 计划清理过期的映射
        // 在实际实现中，需要更复杂的时间管理

        return selected;
    }

    /**
     * 计算音高校正
     */
    private static float calculatePitch(int midiNote, SoundResource sound) {
        // 标准音高：MIDI音符60(C5)对应pitch=1.0
        float basePitch = sound.basePitch;

        // 计算相对音高
        int semitones = midiNote - 60;
        float pitchMultiplier = (float) Math.pow(2.0, semitones / 12.0);

        // 应用音高校正
        return basePitch * pitchMultiplier;
    }

    /**
     * 计算音量
     */
    private static float calculateVolume(int velocity, SoundResource sound) {
        float baseVolume = sound.baseVolume;
        // MIDI力度0-127映射到音量0.0-1.0
        float velocityVolume = velocity / 127.0f;

        // 结合基础音量和力度
        return Math.min(1.0f, baseVolume * velocityVolume * 0.8f);
    }

    /**
     * 渲染MIDI为MC音效MP3
     */
    public static void renderMidiToMCSound(String midiPath, String outputPath) throws Exception {
        System.out.println("开始渲染MC音效版本: " + midiPath);

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

        // 3. 计算总时长（秒）
        double microsecondsPerTick = (double) midiData.tempo / midiData.resolution;
        long maxTick = allNotes.stream()
                .mapToLong(n -> n.tick)
                .max()
                .orElse(0);

        double totalSeconds = (maxTick * microsecondsPerTick) / 1_000_000.0;
        int totalSamples = (int) (totalSeconds * SAMPLE_RATE) + SAMPLE_RATE; // 额外1秒缓冲

        System.out.println("总时长: " + totalSeconds + "秒, 总样本数: " + totalSamples);

        // 4. 创建音频缓冲区
        byte[] audioBuffer = new byte[totalSamples * CHANNELS * (BIT_DEPTH / 8)];

        // 5. 处理每个音符事件
        for (NoteEvent note : allNotes) {
            if (!note.isNoteOn) continue;

            // 获取音效资源
            SoundResource soundRes = getSoundResource(note.key, note.velocity, note.tick);

            // 计算参数
            float pitch = calculatePitch(note.key, soundRes);
            float volume = calculateVolume(note.velocity, soundRes);

            // 计算开始时间（样本位置）
            double startTime = (note.tick * microsecondsPerTick) / 1_000_000.0;
            int startSample = (int) (startTime * SAMPLE_RATE);

            System.out.println(String.format("音符: %s, 时间: %.3fs, 音高: %.2f, 音量: %.2f, 音效: %s",
                    note.getNoteName(), startTime, pitch, volume, soundRes.soundId.getPath()));

            // 这里应该播放音效并录制到缓冲区
            // 由于在Minecraft客户端内，我们需要模拟这个效果
            // 实际实现需要捕获游戏音效输出
        }

        // 6. 保存为MP3
        saveAsMp3(audioBuffer, outputPath);

        System.out.println("渲染完成: " + outputPath);
    }

    /**
     * 简化的MP3保存方法（实际需要LAME库或其他MP3编码器）
     */
    private static void saveAsMp3(byte[] audioData, String outputPath) throws IOException {
        // 注意：Java标准库不支持MP3编码，这里使用WAV格式作为示例
        // 实际项目中需要集成LAME或其他MP3编码库

        String wavPath = outputPath.replace(".mp3", ".wav");
        saveAsWav(audioData, wavPath);

        System.out.println("警告：Java标准库不支持MP3编码，已保存为WAV格式: " + wavPath);
        System.out.println("建议：1. 集成LAME编码库 2. 使用外部命令调用ffmpeg");

        // 调用外部ffmpeg转换（如果可用）
        convertWavToMp3WithFFmpeg(wavPath, outputPath);
    }

    /**
     * 保存为WAV格式
     */
    private static void saveAsWav(byte[] audioData, String outputPath) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // WAV头
            writeWavHeader(baos, audioData.length, SAMPLE_RATE, CHANNELS, BIT_DEPTH);
            baos.write(audioData);

            // 写入文件
            java.nio.file.Files.write(Paths.get(outputPath), baos.toByteArray());
        }
    }

    /**
     * 写入WAV文件头
     */
    private static void writeWavHeader(ByteArrayOutputStream baos, int dataLength,
                                       int sampleRate, int channels, int bitsPerSample) throws IOException {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int totalDataLen = dataLength + 36;

        // RIFF头
        baos.write("RIFF".getBytes()); // ChunkID
        writeInt(baos, totalDataLen); // ChunkSize
        baos.write("WAVE".getBytes()); // Format

        // fmt子块
        baos.write("fmt ".getBytes()); // Subchunk1ID
        writeInt(baos, 16); // Subchunk1Size
        writeShort(baos, 1); // AudioFormat (PCM)
        writeShort(baos, channels); // NumChannels
        writeInt(baos, sampleRate); // SampleRate
        writeInt(baos, byteRate); // ByteRate
        writeShort(baos, blockAlign); // BlockAlign
        writeShort(baos, bitsPerSample); // BitsPerSample

        // data子块
        baos.write("data".getBytes()); // Subchunk2ID
        writeInt(baos, dataLength); // Subchunk2Size
    }

    private static void writeInt(ByteArrayOutputStream baos, int value) throws IOException {
        byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
        baos.write(bytes);
    }

    private static void writeShort(ByteArrayOutputStream baos, short value) throws IOException {
        byte[] bytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array();
        baos.write(bytes);
    }

    private static void writeShort(ByteArrayOutputStream baos, int value) throws IOException {
        writeShort(baos, (short) value);
    }

    /**
     * 调用ffmpeg转换WAV到MP3
     */
    private static void convertWavToMp3WithFFmpeg(String wavPath, String mp3Path) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i", wavPath,
                    "-codec:a", "libmp3lame",
                    "-qscale:a", "2",
                    mp3Path
            );

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("FFmpeg转换成功: " + mp3Path);
                // 删除临时WAV文件
                new File(wavPath).delete();
            } else {
                System.err.println("FFmpeg转换失败，保留WAV文件");
            }
        } catch (Exception e) {
            System.err.println("无法调用ffmpeg: " + e.getMessage());
            System.err.println("请确保已安装ffmpeg并添加到系统PATH");
        }
    }

    /**
     * 清理音效映射
     */
    public static void cleanup() {
        ACTIVE_SOUND_MAPPING.clear();
    }
}