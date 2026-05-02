package tfh.maestrocraft.audio;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WavWriter {
    private static final int SAMPLE_RATE = 44100;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int CHANNELS = 2;

    /**
     * 修复的WAV文件写入方法
     */
    public static void writeWavFile(String filename, float[] leftChannel, float[] rightChannel) throws IOException {
        int sampleCount = leftChannel.length;
        int dataSize = sampleCount * CHANNELS * (BITS_PER_SAMPLE / 8);
        int chunkSize = 36 + dataSize;
        int byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8;
        int blockAlign = CHANNELS * BITS_PER_SAMPLE / 8;

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(filename))) {
            // RIFF头
            writeString(out, "RIFF");
            writeLittleEndianInt(out, chunkSize);
            writeString(out, "WAVE");

            // fmt子块
            writeString(out, "fmt ");
            writeLittleEndianInt(out, 16); // Subchunk1Size
            writeLittleEndianShort(out, 1); // AudioFormat (PCM = 1)
            writeLittleEndianShort(out, CHANNELS); // NumChannels
            writeLittleEndianInt(out, SAMPLE_RATE); // SampleRate
            writeLittleEndianInt(out, byteRate); // ByteRate
            writeLittleEndianShort(out, blockAlign); // BlockAlign
            writeLittleEndianShort(out, BITS_PER_SAMPLE); // BitsPerSample

            // data子块
            writeString(out, "data");
            writeLittleEndianInt(out, dataSize); // Subchunk2Size

            // 写入音频数据
            for (int i = 0; i < sampleCount; i++) {
                // 左声道
                writeLittleEndianShort(out, floatToShort(leftChannel[i]));
                // 右声道
                writeLittleEndianShort(out, floatToShort(rightChannel[i]));
            }
        }
    }

    /**
     * 修复的WAV文件写入方法（带进度回调）
     */
    public static void writeWavFileWithProgress(String filename, float[] leftChannel, float[] rightChannel,
                                                ProgressCallback callback) throws IOException {
        int sampleCount = leftChannel.length;
        int dataSize = sampleCount * CHANNELS * (BITS_PER_SAMPLE / 8);
        int chunkSize = 36 + dataSize;
        int byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8;
        int blockAlign = CHANNELS * BITS_PER_SAMPLE / 8;

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(filename))) {
            // RIFF头
            writeString(out, "RIFF");
            writeLittleEndianInt(out, chunkSize);
            writeString(out, "WAVE");

            // fmt子块
            writeString(out, "fmt ");
            writeLittleEndianInt(out, 16); // Subchunk1Size
            writeLittleEndianShort(out, 1); // AudioFormat (PCM = 1)
            writeLittleEndianShort(out, CHANNELS); // NumChannels
            writeLittleEndianInt(out, SAMPLE_RATE); // SampleRate
            writeLittleEndianInt(out, byteRate); // ByteRate
            writeLittleEndianShort(out, blockAlign); // BlockAlign
            writeLittleEndianShort(out, BITS_PER_SAMPLE); // BitsPerSample

            // data子块
            writeString(out, "data");
            writeLittleEndianInt(out, dataSize); // Subchunk2Size

            // 写入音频数据
            for (int i = 0; i < sampleCount; i++) {
                // 左声道
                writeLittleEndianShort(out, floatToShort(leftChannel[i]));
                // 右声道
                writeLittleEndianShort(out, floatToShort(rightChannel[i]));

                // 进度回调
                if (callback != null && i % 10000 == 0) {
                    float progress = (float) i / sampleCount;
                    callback.onProgress(progress);
                }
            }

            if (callback != null) {
                callback.onComplete();
            }
        }
    }

    /**
     * 写入字符串
     */
    private static void writeString(DataOutputStream out, String str) throws IOException {
        out.writeBytes(str);
    }

    /**
     * 写入小端序整数
     */
    private static void writeLittleEndianInt(DataOutputStream out, int value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(value);
        out.write(buffer.array());
    }

    /**
     * 写入小端序短整数
     */
    private static void writeLittleEndianShort(DataOutputStream out, short value) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(value);
        out.write(buffer.array());
    }

    /**
     * 写入小端序短整数（int参数）
     */
    private static void writeLittleEndianShort(DataOutputStream out, int value) throws IOException {
        writeLittleEndianShort(out, (short) value);
    }

    /**
     * 浮点数转换为16位短整数
     */
    private static short floatToShort(float value) {
        // 限制在-1.0到1.0之间
        value = Math.max(-1.0f, Math.min(1.0f, value));
        return (short) (value * 32767.0f);
    }

    /**
     * 创建测试WAV文件
     */
    public static void createTestTone(String filename, float frequency, float duration) throws IOException {
        int sampleCount = (int) (SAMPLE_RATE * duration);
        float[] left = new float[sampleCount];
        float[] right = new float[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            float t = (float) i / SAMPLE_RATE;
            float value = (float) Math.sin(2 * Math.PI * frequency * t);

            // 应用淡入淡出
            float envelope = 1.0f;
            if (i < sampleCount * 0.1) {
                envelope = i / (float)(sampleCount * 0.1);
            } else if (i > sampleCount * 0.9) {
                envelope = 1.0f - (i - sampleCount * 0.9f) / (sampleCount * 0.1f);
            }

            value *= envelope;
            left[i] = value * 0.8f;
            right[i] = value * 0.8f;
        }

        writeWavFile(filename, left, right);
    }

    /**
     * 创建简单的1kHz测试音
     */
    public static void createSimpleTestTone(String filename) throws IOException {
        createTestTone(filename, 1000.0f, 3.0f);
    }

    /**
     * 进度回调接口
     */
    public interface ProgressCallback {
        void onProgress(float progress);
        void onComplete();
    }
}