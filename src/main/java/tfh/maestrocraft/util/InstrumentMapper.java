package tfh.maestrocraft.util;

import net.minecraft.block.enums.NoteBlockInstrument;

import java.util.HashMap;
import java.util.Map;

public class InstrumentMapper {
    private static final Map<Integer, NoteBlockInstrument> INSTRUMENT_MAP = new HashMap<>();

    static {
        // Map MIDI program numbers to Minecraft instruments

        // 钢琴类 (0-7)
        for (int i = 0; i <= 7; i++) {
            INSTRUMENT_MAP.put(i, NoteBlockInstrument.HARP);
        }

        // 键盘打击乐 (8-15)
        INSTRUMENT_MAP.put(8, NoteBlockInstrument.HARP);      // Celesta
        INSTRUMENT_MAP.put(9, NoteBlockInstrument.HARP);      // Glockenspiel
        INSTRUMENT_MAP.put(10, NoteBlockInstrument.BELL);     // Music Box
        INSTRUMENT_MAP.put(11, NoteBlockInstrument.HARP);     // Vibraphone
        INSTRUMENT_MAP.put(12, NoteBlockInstrument.HARP);     // Marimba
        INSTRUMENT_MAP.put(13, NoteBlockInstrument.HARP);     // Xylophone
        INSTRUMENT_MAP.put(14, NoteBlockInstrument.HARP);     // Tubular Bells
        INSTRUMENT_MAP.put(15, NoteBlockInstrument.HARP);     // Dulcimer

        // 风琴类 (16-23) - 全部使用竖琴
        for (int i = 16; i <= 23; i++) {
            INSTRUMENT_MAP.put(i, NoteBlockInstrument.HARP);
        }

        // 吉他类 (24-31)
        for (int i = 24; i <= 31; i++) {
            INSTRUMENT_MAP.put(i, NoteBlockInstrument.GUITAR);
        }

        // 贝斯类 (32-39)
        for (int i = 32; i <= 39; i++) {
            INSTRUMENT_MAP.put(i, NoteBlockInstrument.BASS);
        }

        // 弦乐类 (40-47) - 使用竖琴
        for (int i = 40; i <= 47; i++) {
            INSTRUMENT_MAP.put(i, NoteBlockInstrument.HARP);
        }

        // 铜管类 (56-63) - 使用竖琴
        for (int i = 56; i <= 63; i++) {
            INSTRUMENT_MAP.put(i, NoteBlockInstrument.HARP);
        }

        // 簧片类 (64-71) - 使用竖琴
        for (int i = 64; i <= 71; i++) {
            INSTRUMENT_MAP.put(i, NoteBlockInstrument.HARP);
        }

        // 笛类 (72-79) - 使用竖琴
        for (int i = 72; i <= 79; i++) {
            INSTRUMENT_MAP.put(i, NoteBlockInstrument.HARP);
        }

        // 合成器类 (80-87) - 使用竖琴
        for (int i = 80; i <= 87; i++) {
            INSTRUMENT_MAP.put(i, NoteBlockInstrument.HARP);
        }

        // 特殊处理打击乐通道 (MIDI通道10，program 0-127)
        // 在MIDI中，通道10是打击乐通道，但程序号不适用
        // 我们在这里不做特殊处理，由解析器处理

        // 为所有未映射的程序号提供默认值
        for (int i = 0; i < 128; i++) {
            INSTRUMENT_MAP.putIfAbsent(i, NoteBlockInstrument.HARP);
        }
    }

    public static NoteBlockInstrument getInstrument(int midiProgram) {
        return INSTRUMENT_MAP.getOrDefault(midiProgram, NoteBlockInstrument.HARP);
    }
}