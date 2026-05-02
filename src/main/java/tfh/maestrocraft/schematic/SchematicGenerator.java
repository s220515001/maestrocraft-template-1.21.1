package tfh.maestrocraft.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import tfh.maestrocraft.midi.MidiParser;
import tfh.maestrocraft.midi.NoteEvent;
import tfh.maestrocraft.util.InstrumentMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class SchematicGenerator {
    private static final int MAX_NOTES_PER_TICK = 3;
    private static final int TRACK_SPACING = 5;
    private static final BlockState STONE_SUPPORT = Blocks.STONE.getDefaultState();
    private static final double DEFAULT_TPS = 20.0;

    public void generate(MidiParser.MidiData midiData, String baseFilename) throws IOException {
        File schematicsDir = new File("./schematics");
        if (!schematicsDir.exists()) schematicsDir.mkdirs();

        StructureBuilder builder = new StructureBuilder();
        double microsecondsPerTick = (double) midiData.tempo / midiData.resolution;

        // ---------- 1. 收集所有音轨的全部 tick，排序去重 ----------
        TreeSet<Long> allTicksSet = new TreeSet<>();
        for (Map.Entry<Integer, List<NoteEvent>> entry : midiData.tracks.entrySet()) {
            for (NoteEvent note : entry.getValue()) {
                if (note.isNoteOn) {
                    allTicksSet.add(note.tick);
                }
            }
        }

        if (allTicksSet.isEmpty()) {
            addMasterSwitch(builder, 0);
            String filename = "./schematics/" + baseFilename + ".litematic";
            saveSchematic(builder, filename);
            System.out.println("Schematic saved to " + filename);
            return;
        }

        List<Long> globalTicks = new ArrayList<>(allTicksSet);

        // ---------- 2. 计算全局延迟序列（单位：红石刻）----------
        int[] globalDelays = new int[globalTicks.size() + 1];
        globalDelays[0] = Math.max(1, computeRedstoneDelay(globalTicks.get(0), microsecondsPerTick));
        for (int i = 1; i < globalTicks.size(); i++) {
            long diff = globalTicks.get(i) - globalTicks.get(i - 1);
            globalDelays[i] = Math.max(1, computeRedstoneDelay(diff, microsecondsPerTick));
        }

        // ---------- 3. 建立总开关 ----------
        addMasterSwitch(builder, midiData.tracks.size());

        // ---------- 4. 为每一音轨按全局节拍放置结构 ----------
        int trackIndex = 0;
        for (Map.Entry<Integer, List<NoteEvent>> entry : midiData.tracks.entrySet()) {
            List<NoteEvent> notes = entry.getValue();

            Map<Long, List<NoteEvent>> notesByTick = new LinkedHashMap<>();
            for (NoteEvent note : notes) {
                if (note.isNoteOn) {
                    notesByTick.computeIfAbsent(note.tick, k -> new ArrayList<>()).add(note);
                }
            }

            buildTrackSynced(builder, notesByTick, globalTicks, globalDelays, trackIndex);
            trackIndex++;
        }

        String filename = "./schematics/" + baseFilename + ".litematic";
        saveSchematic(builder, filename);

        // ---------- 5. 输出时间同步信息 ----------
        printTimingInfo(midiData, globalTicks, globalDelays, microsecondsPerTick);

        System.out.println("Schematic saved to " + filename);
    }

    /**
     * 计算从 from 指向 to 的水平方向（用于判断信号传递方向）
     */
    private Direction getDirectionToward(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx == 1) return Direction.EAST;
        if (dx == -1) return Direction.WEST;
        if (dz == 1) return Direction.SOUTH;
        if (dz == -1) return Direction.NORTH;
        return Direction.SOUTH; // 安全回退
    }

    /**
     * 总开关：按钮附着在石头上，通过强充能激活下方的红石线
     */
    private void addMasterSwitch(StructureBuilder builder, int trackCount) {
        // 石头方块 (0,2,0) 用于被按钮充能，并向下传递信号
        BlockPos switchBlock = new BlockPos(0, 2, 0);
        builder.setBlock(switchBlock, Blocks.STONE.getDefaultState());

        // 按钮放置在 (0,2,1)，facing = NORTH 表示按钮凸起朝北，附着在石头的南面
        // 按下后强充能石头 (0,2,0)
        BlockPos buttonPos = new BlockPos(0, 2, 1);
        builder.setBlock(buttonPos, Blocks.STONE_BUTTON.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
                .with(Properties.POWERED, false));

        // 红石线位于石头正下方 (0,1,0)，由被充能的石头激活
        builder.setBlock(new BlockPos(0, 1, 0), Blocks.REDSTONE_WIRE.getDefaultState());

        // 沿着 X 轴铺设红石线，连接到各个音轨
        int maxX = (trackCount - 1) * TRACK_SPACING;
        for (int x = 1; x <= maxX; x++) {
            builder.setBlock(new BlockPos(x, 1, 0), Blocks.REDSTONE_WIRE.getDefaultState());
        }

        // 底部支撑 (Y=0)
        for (int x = 0; x <= maxX; x++) {
            builder.setBlock(new BlockPos(x, 0, 0), STONE_SUPPORT);
        }
    }

    /**
     * 按全局节拍同步构建单个音轨的红石线路
     */
    private void buildTrackSynced(StructureBuilder builder,
                                  Map<Long, List<NoteEvent>> notesByTick,
                                  List<Long> globalTicks,
                                  int[] globalDelays,
                                  int trackIndex) {
        int trackX = trackIndex * TRACK_SPACING;
        int currentZ = 1;                         // Z=0 已被红石线占用
        BlockPos previousPos = new BlockPos(trackX, 1, 0); // 信号源头：红石线位置

        // ----- 起始延迟链 -----
        List<Integer> startDelays = splitIntoRepeaterDelays(globalDelays[0]);
        for (int j = 0; j < startDelays.size(); j++) {
            BlockPos rPos = new BlockPos(trackX, 1, currentZ);
            // 信号来自 previousPos，所以中继器输入端要对准 previousPos，
            // 而 facing 代表输出端，因此应设置为离开 previousPos 的方向
            Direction inputDirection = getDirectionToward(previousPos, rPos); // 从 previousPos 指向 rPos 的方向
            builder.setRepeater(rPos, startDelays.get(j), inputDirection.getOpposite());
            builder.setBlock(new BlockPos(trackX, 0, currentZ), STONE_SUPPORT);
            previousPos = rPos;
            currentZ++;
        }

        // ----- 遍历所有全局时刻点 -----
        for (int i = 0; i < globalTicks.size(); i++) {
            long tick = globalTicks.get(i);
            List<NoteEvent> tickNotes = notesByTick.getOrDefault(tick, Collections.emptyList());

            BlockPos nodePos = new BlockPos(trackX, 1, currentZ);
            if (!tickNotes.isEmpty()) {
                placeNode(builder, nodePos, tickNotes, trackX);
            } else {
                // 空节点仍需放置支撑块以传导信号
                builder.setBlock(nodePos, STONE_SUPPORT);
            }

            // 在节点之后放置到下一个时刻的中继器链
            if (i < globalTicks.size() - 1) {
                int delayToNext = globalDelays[i + 1];
                List<Integer> delays = splitIntoRepeaterDelays(delayToNext);
                currentZ++; // 中继器与节点之间留一空气格，保证正确充能

                BlockPos currentPrevious = nodePos;
                for (int j = 0; j < delays.size(); j++) {
                    BlockPos rPos = new BlockPos(trackX, 1, currentZ);
                    Direction inputDirection = getDirectionToward(currentPrevious, rPos);
                    builder.setRepeater(rPos, delays.get(j), inputDirection.getOpposite());
                    builder.setBlock(new BlockPos(trackX, 0, currentZ), STONE_SUPPORT);
                    currentPrevious = rPos;
                    currentZ++;
                }
            }
        }
    }

    /**
     * 将一个整数延迟拆分为多个中继器延迟（每个 ≤4 红石刻）
     */
    private List<Integer> splitIntoRepeaterDelays(int totalTicks) {
        List<Integer> delays = new ArrayList<>();
        int remaining = totalTicks;
        while (remaining > 0) {
            int d = Math.min(4, remaining);
            delays.add(d);
            remaining -= d;
        }
        if (delays.isEmpty()) delays.add(1);
        return delays;
    }

    /**
     * 将 MIDI tick 差值转换为红石刻延迟（1 红石刻 = 0.1 秒）
     */
    private int computeRedstoneDelay(long tickDiff, double microsecondsPerTick) {
        double seconds = tickDiff * microsecondsPerTick / 1_000_000.0;
        return (int) Math.round(seconds / 0.1);
    }

    /**
     * 在节点位置放置音符盒及其支撑块，并处理同时发声的副音符
     */
    private void placeNode(StructureBuilder builder, BlockPos nodePos, List<NoteEvent> notes, int trackX) {
        if (notes.isEmpty()) {
            builder.setBlock(nodePos, STONE_SUPPORT);
            return;
        }

        // 主音符
        NoteEvent main = notes.get(0);
        NoteBlockInstrument mainInstr = InstrumentMapper.getInstrument(main.instrument);
        BlockState supportBlock = getSupportBlockForInstrument(mainInstr);
        builder.setBlock(nodePos, supportBlock);
        builder.setNoteBlock(new BlockPos(nodePos.getX(), 2, nodePos.getZ()), main.getMinecraftNote(), mainInstr);

        // 副音符（最多 MAX_NOTES_PER_TICK - 1 个）
        for (int i = 1; i < Math.min(notes.size(), MAX_NOTES_PER_TICK); i++) {
            NoteEvent sub = notes.get(i);
            NoteBlockInstrument subInstr = InstrumentMapper.getInstrument(sub.instrument);
            BlockState subSupport = getSupportBlockForInstrument(subInstr);

            int dirX = (i % 2 == 1) ? -1 : 1; // 交替向东西两侧延伸

            // 分支中继器紧贴节点支撑块侧面
            BlockPos branchRep = new BlockPos(nodePos.getX() + dirX, 1, nodePos.getZ());
            // 信号来自 nodePos，因此中继器输入端要对准 nodePos
            Direction inputDirection = getDirectionToward(nodePos, branchRep);
            builder.setRepeater(branchRep, 1, inputDirection.getOpposite());
            builder.setBlock(new BlockPos(branchRep.getX(), 0, branchRep.getZ()), STONE_SUPPORT);

            // 分支支撑块位于中继器输出端外侧
            BlockPos outSupport = branchRep.offset(inputDirection.getOpposite(), 1);
            builder.setBlock(outSupport, subSupport);
            builder.setNoteBlock(new BlockPos(outSupport.getX(), 2, outSupport.getZ()), sub.getMinecraftNote(), subInstr);
        }
    }

    private BlockState getSupportBlockForInstrument(NoteBlockInstrument instrument) {
        switch (instrument) {
            case HARP: return Blocks.OAK_PLANKS.getDefaultState();
            case BASS: return Blocks.STONE.getDefaultState();
            case BELL: return Blocks.GOLD_BLOCK.getDefaultState();
            case FLUTE: return Blocks.CLAY.getDefaultState();
            case GUITAR: return Blocks.WHITE_WOOL.getDefaultState();
            case XYLOPHONE: return Blocks.BONE_BLOCK.getDefaultState();
            case IRON_XYLOPHONE: return Blocks.IRON_BLOCK.getDefaultState();
            case COW_BELL: return Blocks.SOUL_SAND.getDefaultState();
            case DIDGERIDOO: return Blocks.PUMPKIN.getDefaultState();
            case BIT: return Blocks.EMERALD_BLOCK.getDefaultState();
            case BANJO: return Blocks.HAY_BLOCK.getDefaultState();
            case PLING: return Blocks.GLOWSTONE.getDefaultState();
            case HAT: return Blocks.GLASS.getDefaultState();
            case SNARE: return Blocks.SAND.getDefaultState();
            case BASEDRUM: return Blocks.STONE.getDefaultState();
            default: return Blocks.OAK_PLANKS.getDefaultState();
        }
    }

    private void printTimingInfo(MidiParser.MidiData midiData,
                                 List<Long> globalTicks,
                                 int[] globalDelays,
                                 double microsecondsPerTick) {
        long lastTick = globalTicks.get(globalTicks.size() - 1);
        double musicTotalMicroseconds = lastTick * microsecondsPerTick;
        double musicTotalSeconds = musicTotalMicroseconds / 1_000_000.0;

        int totalRedstoneTicks = 0;
        for (int delay : globalDelays) {
            totalRedstoneTicks += delay;
        }

        int totalGameTicks = totalRedstoneTicks * 2;
        double redstonePlaySeconds = totalRedstoneTicks * 0.1;
        double targetTps = totalGameTicks / musicTotalSeconds;
        int tickRate = (int) Math.round(targetTps);

        System.out.println("========== 节奏同步信息 ==========");
        System.out.println("MIDI音乐总时长: " + String.format("%.2f", musicTotalSeconds) + " 秒");
        System.out.println("红石电路总红石刻: " + totalRedstoneTicks + " rt");
        System.out.println("红石电路总游戏刻: " + totalGameTicks + " gt");
        System.out.println("按默认20TPS播放需要: " + String.format("%.2f", redstonePlaySeconds) + " 秒");
        System.out.println();
        System.out.println(">>> 请使用以下指令调整游戏速度：");
        System.out.println(">>> /tick rate " + tickRate);
        System.out.println(">>> 这会让红石电路完美匹配MIDI音乐的节奏");
        System.out.println("===================================");
    }

    private void saveSchematic(StructureBuilder builder, String filename) throws IOException {
        NbtCompound schematicNbt = builder.toNbt();
        File file = new File(filename);
        file.getParentFile().mkdirs();
        NbtIo.writeCompressed(schematicNbt, Files.newOutputStream(Paths.get(filename)));
    }

    public void generateTestSchematicOnly() throws IOException {
        File schematicsDir = new File("./schematics");
        if (!schematicsDir.exists()) schematicsDir.mkdirs();
        StructureBuilder builder = new StructureBuilder();
        for (int x = 0; x < 2; x++)
            for (int y = 0; y < 2; y++)
                for (int z = 0; z < 2; z++)
                    builder.setBlock(new BlockPos(x, y, z), Blocks.DIRT.getDefaultState());
        saveSchematic(builder, "./schematics/test_dirt_blocks.litematic");
    }
}