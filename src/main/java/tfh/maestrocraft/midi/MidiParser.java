package tfh.maestrocraft.midi;

import javax.sound.midi.*;
import java.io.File;
import java.util.*;

public class MidiParser {
    public static class MidiData {
        public Map<Integer, List<NoteEvent>> tracks;
        public int resolution;
        public int tempo; // 微秒每四分音符
    }

    public MidiData parse(String filePath) throws Exception {
        MidiData midiData = new MidiData();
        Map<Integer, List<NoteEvent>> tracks = new HashMap<>();
        Sequence sequence = MidiSystem.getSequence(new File(filePath));

        midiData.resolution = sequence.getResolution();
        midiData.tempo = 500000; // 默认120BPM (500000微秒/节拍)

        System.out.println("MIDI format: " + sequence.getDivisionType() + ", resolution: " + sequence.getResolution());

        int trackNumber = 0;

        // 解析所有轨道
        for (Track track : sequence.getTracks()) {
            List<NoteEvent> notes = new ArrayList<>();
            Map<Integer, Integer> channelInstruments = new HashMap<>();
            Map<Integer, NoteEvent> activeNotes = new HashMap<>(); // 用于匹配note on/off

            // 初始化所有通道的乐器为钢琴
            for (int i = 0; i < 16; i++) {
                channelInstruments.put(i, 0); // 默认乐器为钢琴
            }

            // 首先解析该轨道的所有事件
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();

                // 解析tempo信息
                if (message instanceof MetaMessage) {
                    MetaMessage meta = (MetaMessage) message;
                    if (meta.getType() == 0x51) { // Tempo meta event
                        byte[] data = meta.getData();
                        if (data.length == 3) {
                            int tempo = (data[0] & 0xFF) << 16 |
                                    (data[1] & 0xFF) << 8 |
                                    (data[2] & 0xFF);
                            midiData.tempo = tempo;
                            System.out.println("Found tempo: " + tempo + " microseconds per quarter note");
                        }
                    }
                }
                // 解析程序变更（乐器变更）
                else if (message instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) message;

                    if (sm.getCommand() == ShortMessage.PROGRAM_CHANGE) {
                        int channel = sm.getChannel();
                        int program = sm.getData1();
                        channelInstruments.put(channel, program);
                        System.out.println("Track " + trackNumber + ", channel " + channel + ": instrument changed to " + program);
                    }
                }
            }

            // 再次遍历，解析音符事件（使用正确的乐器信息）
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();

                if (message instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) message;
                    int channel = sm.getChannel();

                    if (sm.getCommand() == ShortMessage.NOTE_ON) {
                        int key = sm.getData1();
                        int velocity = sm.getData2();
                        long tick = event.getTick();
                        int instrument = channelInstruments.get(channel);

                        if (velocity > 0) {
                            // Note On事件
                            NoteEvent noteEvent = new NoteEvent(key, velocity, channel, instrument, tick, true);
                            notes.add(noteEvent);
                            // 记录为活跃音符
                            int noteId = channel * 128 + key; // 创建唯一ID
                            activeNotes.put(noteId, noteEvent);

                            System.out.println("Note ON: key=" + key + ", channel=" + channel + ", tick=" + tick);
                        } else {
                            // Velocity=0 表示Note Off
                            int noteId = channel * 128 + key;
                            NoteEvent noteEvent = activeNotes.get(noteId);
                            if (noteEvent != null) {
                                noteEvent.setEndTick(tick);
                                activeNotes.remove(noteId);
                                System.out.println("Note OFF: key=" + key + ", duration=" + (tick - noteEvent.tick) + " ticks");
                            }
                        }
                    } else if (sm.getCommand() == ShortMessage.NOTE_OFF) {
                        // 明确的Note Off事件
                        int key = sm.getData1();
                        int noteId = channel * 128 + key;
                        NoteEvent noteEvent = activeNotes.get(noteId);
                        if (noteEvent != null) {
                            noteEvent.setEndTick(event.getTick());
                            activeNotes.remove(noteId);
                            System.out.println("Note OFF: key=" + key + ", duration=" + (event.getTick() - noteEvent.tick) + " ticks");
                        }
                    }
                }
            }

            // 清理未结束的音符（设置默认持续时间）
            for (NoteEvent noteEvent : activeNotes.values()) {
                noteEvent.setEndTick(noteEvent.tick + midiData.resolution * 4); // 默认1小节
            }

            if (!notes.isEmpty()) {
                tracks.put(trackNumber, notes);
                System.out.println("Track " + trackNumber + ": " + notes.size() + " notes");

                // 打印一些音符的持续时间信息
                for (int i = 0; i < Math.min(5, notes.size()); i++) {
                    NoteEvent note = notes.get(i);
                    if (note.endTick > 0) {
                        System.out.println("Note " + i + ": key=" + note.key + ", duration=" +
                                note.getDurationTicks() + " ticks");
                    }
                }
            }

            trackNumber++;
        }

        midiData.tracks = tracks;
        return midiData;
    }
}