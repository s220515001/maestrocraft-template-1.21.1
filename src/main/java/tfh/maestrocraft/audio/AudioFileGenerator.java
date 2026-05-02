package tfh.maestrocraft.audio;

import net.minecraft.client.MinecraftClient;
import tfh.maestrocraft.midi.MidiParser;
import tfh.maestrocraft.midi.NoteEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AudioFileGenerator {
    private static final int SAMPLE_RATE = 44100;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");

    // 音效缓存
    private static final Map<String, float[]> SOUND_CACHE = new ConcurrentHashMap<>();

    /**
     * 生成音频文件（修复版）
     */
    public static void generateAudioFile(String midiPath, String version, String format) {
        new Thread(() -> {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                String versionName = getVersionDisplayName(version);

                if (client != null && client.player != null) {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("§e开始生成" + versionName + "音频文件..."),
                            false
                    );
                }

                // 解析MIDI
                MidiParser parser = new MidiParser();
                MidiParser.MidiData midiData = parser.parse(midiPath);
                double microsecondsPerTick = (double) midiData.tempo / midiData.resolution;

                // 收集所有音符
                List<NoteEvent> allNotes = new ArrayList<>();
                for (List<NoteEvent> trackNotes : midiData.tracks.values()) {
                    allNotes.addAll(trackNotes);
                }

                if (allNotes.isEmpty()) {
                    throw new Exception("MIDI文件中没有音符");
                }

                // 计算总时长
                double maxDuration = 0;
                for (NoteEvent note : allNotes) {
                    if (note.isNoteOn) {
                        double noteEnd = (note.tick * microsecondsPerTick) / 1_000_000.0 +
                                note.getDurationSeconds(microsecondsPerTick);
                        maxDuration = Math.max(maxDuration, noteEnd);
                    }
                }

                // 添加缓冲
                maxDuration += 2.0; // 2秒缓冲
                int totalSamples = (int) (maxDuration * SAMPLE_RATE);

                if (client != null && client.player != null) {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("§7音频长度: " + String.format("%.1f", maxDuration) +
                                    "秒, 样本数: " + totalSamples),
                            false
                    );
                }

                // 创建音频缓冲区
                float[] leftBuffer = new float[totalSamples];
                float[] rightBuffer = new float[totalSamples];

                // 处理每个音符
                int noteCount = 0;
                int totalNotes = (int) allNotes.stream().filter(n -> n.isNoteOn).count();

                for (NoteEvent note : allNotes) {
                    if (!note.isNoteOn) continue;

                    noteCount++;

                    if (noteCount % 20 == 0 && client != null && client.player != null) {
                        client.player.sendMessage(
                                net.minecraft.text.Text.literal("§7处理音符: " + noteCount + "/" + totalNotes),
                                false
                        );
                    }

                    // 计算时间参数
                    double startSeconds = (note.tick * microsecondsPerTick) / 1_000_000.0;
                    double durationSeconds = note.getDurationSeconds(microsecondsPerTick);
                    if (durationSeconds <= 0) durationSeconds = 0.3;

                    // 选择音效和计算参数
                    String soundType = selectSoundType(note, version);
                    float pitch = calculatePitch(note.key, durationSeconds, version);
                    float volume = Math.min(0.8f, note.velocity / 127.0f * 0.7f);

                    // 生成音效样本
                    float[] soundSample = generateSoundSample(soundType, pitch, durationSeconds);

                    // 混合到缓冲区
                    int startSample = (int) (startSeconds * SAMPLE_RATE);
                    mixSoundSample(soundSample, leftBuffer, rightBuffer, startSample, volume);

                    // 如果是统一版且音符较长，添加重复效果
                    if ("unified".equals(version) && durationSeconds > 0.8) {
                        int repeats = (int) (durationSeconds / 0.3);
                        repeats = Math.min(3, Math.max(2, repeats));

                        for (int r = 1; r < repeats; r++) {
                            int repeatStart = startSample + (int)(r * 0.3 * SAMPLE_RATE);
                            float repeatVolume = volume * (1.0f - r * 0.3f);
                            if (repeatVolume < 0.1f) repeatVolume = 0.1f;
                            mixSoundSample(soundSample, leftBuffer, rightBuffer, repeatStart, repeatVolume);
                        }
                    }
                }

                // 应用淡入淡出
                applyFadeInOut(leftBuffer, rightBuffer);

                // 创建输出目录
                String downloadsDir = Paths.get(
                        MinecraftClient.getInstance().runDirectory.getAbsolutePath(),
                        "downloads", "maestrocraft", "audio"
                ).toString();

                Files.createDirectories(Paths.get(downloadsDir));

                // 生成输出文件名
                String baseName = new File(midiPath).getName().replace(".mid", "");
                String timestamp = DATE_FORMAT.format(new Date());
                String outputFile = String.format("%s_%s_%s.%s", baseName, version, timestamp, format);
                String outputPath = Paths.get(downloadsDir, outputFile).toString();

                // 保存文件
                if ("wav".equalsIgnoreCase(format)) {
                    WavWriter.writeWavFileWithProgress(outputPath, leftBuffer, rightBuffer,
                            new WavWriter.ProgressCallback() {
                                @Override
                                public void onProgress(float progress) {
                                    if (client != null && client.player != null) {
                                        client.player.sendMessage(
                                                net.minecraft.text.Text.literal("§7保存进度: " +
                                                        String.format("%.0f", progress * 100) + "%"),
                                                false
                                        );
                                    }
                                }

                                @Override
                                public void onComplete() {
                                    if (client != null && client.player != null) {
                                        client.player.sendMessage(
                                                net.minecraft.text.Text.literal("§a" + versionName + "WAV文件生成完成！"),
                                                false
                                        );
                                        client.player.sendMessage(
                                                net.minecraft.text.Text.literal("§7保存到: " + outputPath),
                                                false
                                        );
                                        File file = new File(outputPath);
                                        double sizeMB = file.length() / 1024.0 / 1024.0;
                                        client.player.sendMessage(
                                                net.minecraft.text.Text.literal("§7文件大小: " +
                                                        String.format("%.1f", sizeMB) + " MB"),
                                                false
                                        );
                                    }
                                }
                            });
                } else {
                    // 先生成WAV，再转MP3
                    String wavPath = outputPath.replace(".mp3", ".wav");
                    WavWriter.writeWavFile(wavPath, leftBuffer, rightBuffer);

                    // 转换为MP3
                    convertToMp3(wavPath, outputPath);
                }

            } catch (Exception e) {
                e.printStackTrace();
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("§c生成音频文件失败: " + e.getMessage()),
                            false
                    );
                }
            }
        }).start();
    }

    /**
     * 选择音效类型
     */
    private static String selectSoundType(NoteEvent note, String version) {
        switch (version) {
            case "unified":
                // 统一版：使用经验颗粒音效
                return "experience";

            case "enhanced":
                // 增强版：根据音高选择不同音效
                if (note.key < 48) return "stone";
                else if (note.key < 72) return "harp";
                else if (note.key < 96) return "flint";
                else return "experience";

            default: // standard
                // 标准版：使用竖琴音效
                return "harp";
        }
    }

    /**
     * 计算音高
     */
    private static float calculatePitch(int midiNote, double duration, String version) {
        float pitch = PitchCalculator.getCorrectPitch(midiNote);

        if ("unified".equals(version) && duration > 1.0) {
            // 统一版的长音符减慢效果
            float slowFactor = (float) (1.0 / Math.sqrt(1.0 + duration * 0.3));
            pitch *= slowFactor;
        }

        return Math.max(0.25f, Math.min(4.0f, pitch)); // 放宽音高范围
    }

    /**
     * 生成音效样本
     */
    private static float[] generateSoundSample(String soundType, float pitch, double duration) {
        String cacheKey = soundType + "_" + pitch + "_" + duration;

        if (SOUND_CACHE.containsKey(cacheKey)) {
            return SOUND_CACHE.get(cacheKey);
        }

        // 根据音效类型生成不同的波形
        float baseFrequency;
        float[] envelope;

        switch (soundType) {
            case "experience":
                baseFrequency = 800.0f; // 经验颗粒：高音
                envelope = new float[] {0.1f, 0.3f, 0.8f}; // 快速起振
                break;
            case "harp":
                baseFrequency = 440.0f; // 竖琴：A4
                envelope = new float[] {0.05f, 0.4f, 0.6f}; // 较慢衰减
                break;
            case "stone":
                baseFrequency = 200.0f; // 石头：低音
                envelope = new float[] {0.01f, 0.2f, 0.3f}; // 快速衰减
                break;
            case "flint":
                baseFrequency = 600.0f; // 打火石：中高音
                envelope = new float[] {0.02f, 0.1f, 0.4f}; // 尖锐
                break;
            default:
                baseFrequency = 440.0f;
                envelope = new float[] {0.1f, 0.3f, 0.5f};
        }

        // 应用音高调整
        float frequency = baseFrequency * pitch;

        // 计算样本数
        int sampleCount = (int) (Math.min(duration, 2.0) * SAMPLE_RATE); // 最长2秒

        float[] samples = new float[sampleCount];

        // 生成波形（带谐波）
        for (int i = 0; i < sampleCount; i++) {
            float t = (float) i / SAMPLE_RATE;

            // 主频率
            float value = (float) Math.sin(2 * Math.PI * frequency * t);

            // 添加谐波
            if (soundType.equals("harp")) {
                // 竖琴：添加二次和三次谐波
                value += 0.3f * Math.sin(2 * Math.PI * frequency * 2 * t);
                value += 0.1f * Math.sin(2 * Math.PI * frequency * 3 * t);
            } else if (soundType.equals("stone")) {
                // 石头：丰富谐波
                value += 0.5f * Math.sin(2 * Math.PI * frequency * 1.5f * t);
                value += 0.3f * Math.sin(2 * Math.PI * frequency * 2.5f * t);
            }

            // 应用包络
            float progress = (float) i / sampleCount;
            float envValue;
            if (progress < envelope[0]) {
                envValue = progress / envelope[0]; // 起音
            } else if (progress < envelope[0] + envelope[1]) {
                envValue = 1.0f; // 保持
            } else {
                envValue = 1.0f - (progress - envelope[0] - envelope[1]) / envelope[2]; // 衰减
                if (envValue < 0) envValue = 0;
            }

            value *= envValue;

            // 限制幅度
            value = Math.max(-0.8f, Math.min(0.8f, value));

            samples[i] = value;
        }

        SOUND_CACHE.put(cacheKey, samples);
        return samples;
    }

    /**
     * 混合音效样本到主缓冲区
     */
    private static void mixSoundSample(float[] sample, float[] leftBuffer, float[] rightBuffer,
                                       int startSample, float volume) {
        for (int i = 0; i < sample.length; i++) {
            int bufferIndex = startSample + i;
            if (bufferIndex >= leftBuffer.length) break;

            float value = sample[i] * volume;
            leftBuffer[bufferIndex] += value;
            rightBuffer[bufferIndex] += value * 0.95f; // 右声道稍小

            // 限制防止削波
            if (leftBuffer[bufferIndex] > 0.95f) leftBuffer[bufferIndex] = 0.95f;
            if (leftBuffer[bufferIndex] < -0.95f) leftBuffer[bufferIndex] = -0.95f;
            if (rightBuffer[bufferIndex] > 0.95f) rightBuffer[bufferIndex] = 0.95f;
            if (rightBuffer[bufferIndex] < -0.95f) rightBuffer[bufferIndex] = -0.95f;
        }
    }

    /**
     * 应用淡入淡出
     */
    private static void applyFadeInOut(float[] left, float[] right) {
        int fadeSamples = Math.min(SAMPLE_RATE / 2, left.length / 10); // 0.5秒或总长的10%

        // 淡入
        for (int i = 0; i < fadeSamples; i++) {
            float fade = (float) i / fadeSamples;
            left[i] *= fade;
            right[i] *= fade;
        }

        // 淡出
        for (int i = left.length - fadeSamples; i < left.length; i++) {
            if (i < 0) continue;
            float fade = (float) (left.length - i) / fadeSamples;
            left[i] *= fade;
            right[i] *= fade;
        }
    }

    /**
     * WAV转MP3
     */
    private static void convertToMp3(String wavPath, String mp3Path) throws IOException, InterruptedException {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client != null && client.player != null) {
            client.player.sendMessage(
                    net.minecraft.text.Text.literal("§e正在转换为MP3格式..."),
                    false
            );
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i", wavPath,
                    "-codec:a", "libmp3lame",
                    "-b:a", "192k", // 比特率
                    "-ac", "2", // 立体声
                    "-ar", "44100", // 采样率
                    "-y", // 覆盖
                    mp3Path
            );

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                // 删除临时WAV文件
                new File(wavPath).delete();

                if (client != null && client.player != null) {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("§aMP3转换完成！"),
                            false
                    );
                    File file = new File(mp3Path);
                    double sizeMB = file.length() / 1024.0 / 1024.0;
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("§7MP3文件大小: " +
                                    String.format("%.1f", sizeMB) + " MB"),
                            false
                    );
                }
            } else {
                throw new IOException("FFmpeg转换失败，错误码: " + exitCode);
            }

        } catch (Exception e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§eMP3转换失败，保留WAV文件"),
                        false
                );
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§c错误: " + e.getMessage()),
                        false
                );
            }
            throw e;
        }
    }

    /**
     * 创建测试音频文件
     */
    public static void createTestFile(String format) {
        new Thread(() -> {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                String downloadsDir = Paths.get(
                        client.runDirectory.getAbsolutePath(),
                        "downloads", "maestrocraft", "audio"
                ).toString();

                Files.createDirectories(Paths.get(downloadsDir));

                String timestamp = DATE_FORMAT.format(new Date());
                String testPath = Paths.get(downloadsDir,
                        "test_" + timestamp + "." + format).toString();

                if (client != null && client.player != null) {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("§e创建测试音频文件..."),
                            false
                    );
                }

                if ("wav".equalsIgnoreCase(format)) {
                    WavWriter.createSimpleTestTone(testPath);

                    if (client != null && client.player != null) {
                        client.player.sendMessage(
                                net.minecraft.text.Text.literal("§a测试WAV文件创建成功！"),
                                false
                        );
                        client.player.sendMessage(
                                net.minecraft.text.Text.literal("§7文件: " + testPath),
                                false
                        );
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 获取版本显示名称
     */
    private static String getVersionDisplayName(String version) {
        switch (version) {
            case "unified": return "统一短音效";
            case "enhanced": return "增强";
            case "standard": return "标准";
            default: return "音频";
        }
    }

    /**
     * 清理缓存
     */
    public static void clearCache() {
        SOUND_CACHE.clear();
    }
}