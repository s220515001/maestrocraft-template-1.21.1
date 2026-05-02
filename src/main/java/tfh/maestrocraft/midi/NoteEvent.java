package tfh.maestrocraft.midi;

public class NoteEvent {
    public final int key;
    public final int velocity;
    public final int channel;        // MIDI通道 (0-15)
    public final int instrument;     // MIDI乐器号 (0-127)
    public final long tick;
    public final boolean isNoteOn;
    public long endTick; // 音符结束时间（如果为note off事件则设置）

    public NoteEvent(int key, int velocity, int channel, int instrument, long tick, boolean isNoteOn) {
        this.key = key;
        this.velocity = velocity;
        this.channel = channel;
        this.instrument = instrument;
        this.tick = tick;
        this.isNoteOn = isNoteOn;
        this.endTick = -1; // 初始化为-1表示未设置
    }

    /**
     * 设置音符结束时间
     */
    public void setEndTick(long endTick) {
        this.endTick = endTick;
    }

    /**
     * 获取音符持续时间（ticks）
     */
    public long getDurationTicks() {
        if (endTick > tick) {
            return endTick - tick;
        }
        return 0;
    }

    /**
     * 获取音符持续时间（秒）
     */
    public double getDurationSeconds(double microsecondsPerTick) {
        long durationTicks = getDurationTicks();
        return (durationTicks * microsecondsPerTick) / 1_000_000.0;
    }

    public int getMinecraftNote() {
        // MIDI音符范围: 0-127
        // Minecraft音符范围: 0-24 (但我们可以扩展)

        // C5(60)对应Minecraft音符0
        int note = key - 60;

        // 确保在合适的八度内
        if (note < 0) {
            // 太低，提高八度
            while (note < 0) {
                note += 12;
            }
        } else if (note > 24) {
            // 太高，降低八度
            while (note > 24) {
                note -= 12;
            }
        }

        // 最终检查范围
        return Math.max(0, Math.min(24, note));
    }

    /**
     * 获取音符名（调试用）
     */
    public String getNoteName() {
        String[] noteNames = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int octave = (key / 12) - 1;
        int noteIndex = key % 12;
        return noteNames[noteIndex] + octave + " (" + key + ")";
    }

    /**
     * 获取原始MIDI音符 (0-127)
     */
    public int getMidiNote() {
        return key;
    }
}