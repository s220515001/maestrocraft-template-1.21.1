package tfh.maestrocraft.audio;

public class PitchCalculator {

    /**
     * 正确的Minecraft音高计算（经过实际验证）
     * 关键公式：pitch = 2^((midiNote - 60) / 12)
     */

    /**
     * 获取正确的音高
     */
    public static float getCorrectPitch(int midiNote) {
        // C5 = MIDI 60 = pitch 1.0
        int semitonesFromC5 = midiNote - 60;
        float octaves = semitonesFromC5 / 12.0f;

        // pitch = 2^octaves
        float pitch = (float) Math.pow(2.0, octaves);

        // Minecraft限制范围
        return clampPitch(pitch);
    }

    /**
     * 带时长调整的音高
     */
    public static float getPitchWithDuration(int midiNote, double durationSeconds) {
        float pitch = getCorrectPitch(midiNote);

        // 长音符适当降低音高模拟减慢效果
        if (durationSeconds > 1.0) {
            float slowFactor = (float) Math.max(0.85, 1.0 / Math.sqrt(durationSeconds));
            pitch *= slowFactor;
        }

        return clampPitch(pitch);
    }

    /**
     * 音高范围限制
     */
    private static float clampPitch(float pitch) {
        if (pitch < 0.5f) return 0.5f;
        if (pitch > 2.0f) return 2.0f;
        return pitch;
    }

    /**
     * 获取音符名称
     */
    public static String getNoteName(int midiNote) {
        String[] noteNames = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int octave = (midiNote / 12) - 1;
        int noteIndex = midiNote % 12;
        return noteNames[noteIndex] + octave;
    }

    /**
     * 显示所有音高计算结果
     */
    public static void showAllPitches() {
        System.out.println("=== 正确音高计算 ===");
        System.out.println("公式: pitch = 2^((MIDI音符 - 60) / 12)");
        System.out.println("范围: 0.5 - 2.0");
        System.out.println("\nMIDI音符 | 音符名 | 计算pitch");
        System.out.println("--------|--------|-----------");

        // 显示整个可听范围的音高
        for (int note = 48; note <= 84; note += 12) {
            float pitch = getCorrectPitch(note);
            String name = getNoteName(note);
            String comment = "";

            if (note == 48) comment = " (C4，比C5低八度)";
            else if (note == 60) comment = " (C5，基准音)";
            else if (note == 72) comment = " (C6，比C5高八度)";
            else if (note == 84) comment = " (C7，比C5高两个八度)";

            System.out.println(String.format("   %2d   |   %-4s  |   %.4f  %s",
                    note, name, pitch, comment));
        }

        // 验证八度关系
        System.out.println("\n=== 八度关系验证 ===");
        float pitchC5 = getCorrectPitch(60);
        float pitchC4 = getCorrectPitch(48);
        float pitchC6 = getCorrectPitch(72);

        System.out.println(String.format("C4 (48): %.4f", pitchC4));
        System.out.println(String.format("C5 (60): %.4f (基准)", pitchC5));
        System.out.println(String.format("C6 (72): %.4f", pitchC6));
        System.out.println(String.format("C6/C5 = %.4f (应该接近2.0)", pitchC6 / pitchC5));
        System.out.println(String.format("C5/C4 = %.4f (应该接近2.0)", pitchC5 / pitchC4));

        // 验证半音间隔
        System.out.println("\n=== 半音间隔验证 ===");
        float pitchC = getCorrectPitch(60);
        float pitchCSharp = getCorrectPitch(61);
        float semitoneRatio = pitchCSharp / pitchC;
        System.out.println(String.format("C5 -> C#5: %.4f / %.4f = %.4f",
                pitchCSharp, pitchC, semitoneRatio));
        System.out.println(String.format("理论值: 2^(1/12) = %.4f", Math.pow(2, 1.0/12)));
    }
}