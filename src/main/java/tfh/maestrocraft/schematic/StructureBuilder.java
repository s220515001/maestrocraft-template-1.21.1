package tfh.maestrocraft.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.nbt.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;

public class StructureBuilder {
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Map<BlockPos, NbtCompound> tileEntities = new HashMap<>();
    private BlockPos min = new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    private BlockPos max = new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

    public void setBlock(BlockPos pos, BlockState state) {
        blocks.put(pos, state);
        updateBounds(pos);
    }

    public boolean hasBlockAt(BlockPos pos) {
        return blocks.containsKey(pos);
    }

    public void setNoteBlock(BlockPos pos, int note, NoteBlockInstrument instrument) {
        int adjustedNote = Math.max(0, Math.min(24, note));
        BlockState state = Blocks.NOTE_BLOCK.getDefaultState()
                .with(Properties.NOTE, adjustedNote)
                .with(Properties.INSTRUMENT, instrument);
        setBlock(pos, state);

        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", "minecraft:note_block");
        nbt.putByte("note", (byte) adjustedNote);
        nbt.putString("instrument", instrument.asString());
        tileEntities.put(pos, nbt);
    }

    /**
     * 放置中继器。
     * @param facing 必须为输出端朝向（Properties.HORIZONTAL_FACING）
     */
    public void setRepeater(BlockPos pos, int delay, Direction facing) {
        int adjustedDelay = Math.max(1, Math.min(4, delay));
        BlockState state = Blocks.REPEATER.getDefaultState()
                .with(Properties.DELAY, adjustedDelay)
                .with(Properties.HORIZONTAL_FACING, facing);
        setBlock(pos, state);
    }

    private void updateBounds(BlockPos pos) {
        min = new BlockPos(
                Math.min(min.getX(), pos.getX()),
                Math.min(min.getY(), pos.getY()),
                Math.min(min.getZ(), pos.getZ())
        );
        max = new BlockPos(
                Math.max(max.getX(), pos.getX()),
                Math.max(max.getY(), pos.getY()),
                Math.max(max.getZ(), pos.getZ())
        );
    }

    // 下面的 toNbt 等方法保持不变（省略，与原文件一致）
    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        root.putInt("Version", 7);
        root.putInt("MinecraftDataVersion", 3955);
        root.putInt("SubVersion", 1);

        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;
        int totalVolume = sizeX * sizeY * sizeZ;

        NbtCompound metadata = new NbtCompound();
        metadata.putString("Name", "MaestroCraft Schematic");
        metadata.putString("Author", "MaestroCraft");
        metadata.putString("Description", "Generated redstone music schematic");
        metadata.putLong("TimeCreated", System.currentTimeMillis());
        metadata.putLong("TimeModified", System.currentTimeMillis());
        metadata.putInt("RegionCount", 1);
        metadata.putInt("TotalVolume", totalVolume);
        metadata.putInt("TotalBlocks", blocks.size());

        NbtCompound enclosingSize = new NbtCompound();
        enclosingSize.putInt("x", sizeX);
        enclosingSize.putInt("y", sizeY);
        enclosingSize.putInt("z", sizeZ);
        metadata.put("EnclosingSize", enclosingSize);
        root.put("Metadata", metadata);

        NbtCompound regions = new NbtCompound();
        NbtCompound region = new NbtCompound();

        NbtCompound position = new NbtCompound();
        position.putInt("x", 0);
        position.putInt("y", 0);
        position.putInt("z", 0);
        region.put("Position", position);

        NbtCompound size = new NbtCompound();
        size.putInt("x", sizeX);
        size.putInt("y", sizeY);
        size.putInt("z", sizeZ);
        region.put("Size", size);

        NbtList palette = new NbtList();
        Map<BlockState, Integer> stateToId = new HashMap<>();
        List<BlockState> paletteList = new ArrayList<>();

        paletteList.add(Blocks.AIR.getDefaultState());
        stateToId.put(Blocks.AIR.getDefaultState(), 0);
        palette.add(createBlockStateNbt(Blocks.AIR.getDefaultState()));

        for (BlockState state : blocks.values()) {
            if (!stateToId.containsKey(state)) {
                int id = paletteList.size();
                paletteList.add(state);
                stateToId.put(state, id);
                palette.add(createBlockStateNbt(state));
            }
        }

        int bitsPerBlock = Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteList.size() - 1));
        long[] blockStatesArray = new long[(int) Math.ceil((sizeX * sizeY * sizeZ * bitsPerBlock) / 64.0)];

        int index = 0;
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = blocks.getOrDefault(pos, Blocks.AIR.getDefaultState());
                    int stateId = stateToId.get(state);
                    int longIndex = index * bitsPerBlock / 64;
                    int bitOffset = (index * bitsPerBlock) % 64;
                    blockStatesArray[longIndex] |= ((long) stateId) << bitOffset;
                    index++;
                }
            }
        }

        NbtList tileEntitiesList = new NbtList();
        for (Map.Entry<BlockPos, NbtCompound> entry : tileEntities.entrySet()) {
            BlockPos pos = entry.getKey();
            NbtCompound nbt = entry.getValue().copy();
            nbt.putInt("x", pos.getX() - min.getX());
            nbt.putInt("y", pos.getY() - min.getY());
            nbt.putInt("z", pos.getZ() - min.getZ());
            tileEntitiesList.add(nbt);
        }

        region.put("BlockStatePalette", palette);
        region.put("BlockStates", new NbtLongArray(blockStatesArray));
        region.put("TileEntities", tileEntitiesList);
        region.put("Entities", new NbtList());
        region.put("PendingBlockTicks", new NbtList());
        region.put("PendingFluidTicks", new NbtList());

        regions.put("MainRegion", region);
        root.put("Regions", regions);
        return root;
    }

    private NbtCompound createBlockStateNbt(BlockState state) {
        NbtCompound nbt = new NbtCompound();
        String blockId = state.getBlock().getRegistryEntry().registryKey().getValue().toString();
        nbt.putString("Name", blockId);
        if (!state.getEntries().isEmpty()) {
            NbtCompound properties = new NbtCompound();
            state.getEntries().forEach((property, value) -> {
                if (value instanceof Enum) {
                    properties.putString(property.getName(), ((Enum<?>) value).name().toLowerCase());
                } else {
                    properties.putString(property.getName(), value.toString());
                }
            });
            nbt.put("Properties", properties);
        }
        return nbt;
    }
}