package tfh.maestrocraft.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import tfh.maestrocraft.midi.MidiParser;
import tfh.maestrocraft.midi.NoteEvent;

import java.util.*;
import java.util.concurrent.*;

public class EnhancedSoundPlayer {
    private static ScheduledExecutorService scheduler;
    private static final Random RANDOM = Random.create();
    private static final Map<Integer, ScheduledFuture<?>> ACTIVE_TASKS = new ConcurrentHashMap<>();
    private static final Map<Integer, SoundInstance> ACTIVE_SOUNDS = new ConcurrentHashMap<>();
    private static final Map<Integer, SoundEvent> NOTE_SOUND_MAP = new ConcurrentHashMap<>();

    // 音效分类库 - 更丰富的音效选择
    private static final Map<String, List<String>> SOUND_CATEGORIES = new HashMap<>();

    static {
        initializeSoundCategories();
        System.out.println("音效库初始化完成");
        printSoundStatistics();
    }

    private static void initializeSoundCategories() {
        // 音符盒类音效
        SOUND_CATEGORIES.put("note_block", Arrays.asList(
                "block.note_block.harp",
                "block.note_block.bass",
                "block.note_block.bell",
                "block.note_block.flute",
                "block.note_block.guitar",
                "block.note_block.xylophone",
                "block.note_block.iron_xylophone",
                "block.note_block.cow_bell",
                "block.note_block.didgeridoo",
                "block.note_block.bit",
                "block.note_block.banjo",
                "block.note_block.pling",
                "block.note_block.hat",
                "block.note_block.snare",
                "block.note_block.basedrum",
                "block.note_block.chime"
        ));

        // 门类音效
        SOUND_CATEGORIES.put("doors", Arrays.asList(
                "block.wooden_door.open",
                "block.wooden_door.close",
                "block.iron_door.open",
                "block.iron_door.close",
                "block.trapdoor.open",
                "block.trapdoor.close",
                "block.fence_gate.open",
                "block.fence_gate.close",
                "block.wooden_trapdoor.open",
                "block.wooden_trapdoor.close",
                "block.iron_trapdoor.open",
                "block.iron_trapdoor.close"
        ));

        // 火/打火石音效
        SOUND_CATEGORIES.put("fire_flint", Arrays.asList(
                "item.flintandsteel.use",
                "block.fire.ambient",
                "block.fire.extinguish",
                "entity.creeper.primed",
                "entity.tnt.primed",
                "block.lava.ambient",
                "block.lava.pop"
        ));

        // 方块破坏音效
        SOUND_CATEGORIES.put("block_break", Arrays.asList(
                "block.stone.break",
                "block.gravel.break",
                "block.grass.break",
                "block.wood.break",
                "block.glass.break",
                "block.metal.break",
                "block.sand.break",
                "block.cloth.break",
                "block.anvil.break",
                "block.anvil.destroy",
                "block.anvil.land",
                "block.glass.hit"
        ));

        // 经验/魔法音效
        SOUND_CATEGORIES.put("experience_magic", Arrays.asList(
                "entity.experience_orb.pickup",
                "entity.experience_orb.touch",
                "block.enchantment_table.use",
                "entity.evoker.cast_spell",
                "entity.illusioner.cast_spell",
                "block.beacon.activate",
                "block.beacon.ambient",
                "entity.player.levelup"
        ));

        // 动物音效
        SOUND_CATEGORIES.put("animals", Arrays.asList(
                "entity.cow.ambient",
                "entity.pig.ambient",
                "entity.sheep.ambient",
                "entity.chicken.ambient",
                "entity.wolf.ambient",
                "entity.cat.ambient",
                "entity.ocelot.ambient",
                "entity.horse.ambient",
                "entity.donkey.ambient",
                "entity.parrot.ambient",
                "entity.bat.ambient",
                "entity.rabbit.ambient"
        ));

        // 工具/武器音效
        SOUND_CATEGORIES.put("tools_weapons", Arrays.asList(
                "entity.arrow.hit",
                "entity.arrow.shoot",
                "entity.trident.hit",
                "entity.trident.throw",
                "item.shield.block",
                "item.axe.strip",
                "item.hoe.till",
                "item.shovel.flatten",
                "entity.splash_potion.break",
                "entity.lingering_potion.throw"
        ));

        // 环境音效
        SOUND_CATEGORIES.put("ambient", Arrays.asList(
                "ambient.cave",
                "weather.rain",
                "weather.rain.above",
                "block.water.ambient",
                "block.lava.ambient",
                "block.fire.ambient",
                "ambient.basalt_deltas.loop",
                "ambient.nether_wastes.loop",
                "ambient.soul_sand_valley.loop",
                "ambient.warped_forest.loop"
        ));

        // 实体音效
        SOUND_CATEGORIES.put("entities", Arrays.asList(
                "entity.player.attack.weak",
                "entity.player.attack.strong",
                "entity.player.hurt",
                "entity.villager.ambient",
                "entity.villager.hurt",
                "entity.zombie.ambient",
                "entity.skeleton.ambient",
                "entity.creeper.primed",
                "entity.enderman.stare",
                "entity.ghast.ambient",
                "entity.witch.ambient"
        ));

        // 红石/机械音效
        SOUND_CATEGORIES.put("redstone_mechanical", Arrays.asList(
                "block.lever.click",
                "block.button.click",
                "block.piston.extend",
                "block.piston.contract",
                "block.dispenser.dispense",
                "block.dispenser.fail",
                "block.comparator.click",
                "block.repeater.click",
                "block.chest.open",
                "block.chest.close",
                "block.ender_chest.open",
                "block.ender_chest.close"
        ));
    }

    private static void printSoundStatistics() {
        System.out.println("=== 音效库统计 ===");
        int totalSounds = 0;
        for (Map.Entry<String, List<String>> entry : SOUND_CATEGORIES.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue().size() + " 个音效");
            totalSounds += entry.getValue().size();
        }
        System.out.println("总计: " + totalSounds + " 个音效");
    }

    /**
     * 根据音符参数选择音效类别
     */
    private static String selectCategoryForNote(int midiNote, int velocity, double duration) {
        // 根据音符的音高范围、力度和持续时间选择音效类别

        if (midiNote >= 60 && midiNote <= 72) { // 中音区
            if (duration > 1.0) return "note_block"; // 长音符使用音符盒
            if (velocity > 90) return "experience_magic"; // 强音使用经验音效
            return "tools_weapons";
        } else if (midiNote < 48) { // 低音区
            if (velocity > 80) return "block_break"; // 强低音使用破坏音
            if (duration > 0.8) return "doors"; // 长低音使用门音效
            return "animals";
        } else if (midiNote < 60) { // 中低音区
            return "redstone_mechanical";
        } else if (midiNote > 84) { // 高音区
            if (velocity > 85) return "fire_flint"; // 强高音使用打火石
            return "ambient";
        } else { // 中高音区
            if (duration > 0.5) return "note_block";
            return "entities";
        }
    }

    /**
     * 智能获取音效
     */
    private static SoundEvent getSoundForNote(int noteKey, int velocity, double durationSeconds) {
        // 生成缓存键
        Integer cacheKey = noteKey * 1000 + velocity * 10 + (int)(durationSeconds * 100);

        // 检查缓存
        SoundEvent cached = NOTE_SOUND_MAP.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 选择音效类别
        String category = selectCategoryForNote(noteKey, velocity, durationSeconds);
        List<String> soundIds = SOUND_CATEGORIES.get(category);

        if (soundIds == null || soundIds.isEmpty()) {
            // 回退到默认类别
            soundIds = SOUND_CATEGORIES.get("note_block");
        }

        // 根据音符参数选择具体音效
        int index = Math.abs(noteKey * 13 + velocity * 7 + (int)(durationSeconds * 100)) % soundIds.size();
        String selectedSoundId = soundIds.get(index);

        // 获取SoundEvent
        SoundEvent soundEvent = getSoundEvent(selectedSoundId);
        if (soundEvent == null) {
            // 如果获取失败，尝试其他音效
            for (String soundId : soundIds) {
                soundEvent = getSoundEvent(soundId);
                if (soundEvent != null) break;
            }
        }

        // 缓存结果
        if (soundEvent != null) {
            NOTE_SOUND_MAP.put(cacheKey, soundEvent);
        }

        return soundEvent;
    }

    /**
     * 获取音效事件
     */
    private static SoundEvent getSoundEvent(String soundId) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getNetworkHandler() != null) {
                Registry<SoundEvent> registry = client.getNetworkHandler().getRegistryManager()
                        .get(Registries.SOUND_EVENT.getKey());
                return registry.get(Identifier.of(soundId));
            }
        } catch (Exception e) {
            System.err.println("无法获取音效: " + soundId + " - " + e.getMessage());
        }
        return null;
    }

    /**
     * 计算音高
     */
    private static float calculatePitch(int midiNote) {
        // MIDI音符69(A4) = pitch 1.0
        int semitones = midiNote - 69;
        float pitch = (float) Math.pow(2.0, semitones / 12.0);

        // Minecraft限制范围
        return Math.max(0.5f, Math.min(2.0f, pitch));
    }

    /**
     * 创建音效实例
     */
    private static SoundInstance createSoundInstance(SoundEvent soundEvent, float pitch, float volume,
                                                     boolean looping, double durationSeconds) {
        if (soundEvent == null) {
            soundEvent = getSoundEvent("block.note_block.harp");
        }

        if (looping && durationSeconds > 1.0) {
            // 循环音效用于长音符
            return new PositionedSoundInstance(
                    soundEvent.getId(),
                    SoundCategory.RECORDS,
                    volume,
                    pitch,
                    RANDOM,
                    false,
                    0,
                    SoundInstance.AttenuationType.NONE,
                    0.0, 0.0, 0.0,
                    true
            );
        } else {
            // 单次播放音效
            return PositionedSoundInstance.master(soundEvent, pitch, volume);
        }
    }

    /**
     * 播放MIDI的增强音效版
     */
    public static void playMidiWithEnhancedSounds(String midiPath) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) {
                System.err.println("Minecraft客户端不可用");
                return;
            }

            System.out.println("开始播放增强音效版: " + midiPath);

            // 解析MIDI
            MidiParser parser = new MidiParser();
            MidiParser.MidiData midiData = parser.parse(midiPath);

            // 收集所有音符
            List<NoteEvent> allNotes = new ArrayList<>();
            for (List<NoteEvent> trackNotes : midiData.tracks.values()) {
                allNotes.addAll(trackNotes);
            }

            if (allNotes.isEmpty()) {
                System.err.println("MIDI文件中没有音符");
                return;
            }

            allNotes.sort(Comparator.comparingLong(n -> n.tick));

            // 时间转换
            double microsecondsPerTick = (double) midiData.tempo / midiData.resolution;

            // 停止之前的播放
            stopPlaying();

            // 创建调度器
            scheduler = Executors.newScheduledThreadPool(8);

            // 清理缓存
            NOTE_SOUND_MAP.clear();

            // 安排播放
            int taskId = 0;
            Map<String, Integer> soundCategoryStats = new HashMap<>();

            for (NoteEvent note : allNotes) {
                if (!note.isNoteOn) continue;

                // 计算时间参数
                double startSeconds = (note.tick * microsecondsPerTick) / 1_000_000.0;
                double durationSeconds = note.getDurationSeconds(microsecondsPerTick);
                if (durationSeconds <= 0) durationSeconds = 0.5;

                // 获取音效
                SoundEvent soundEvent = getSoundForNote(note.key, note.velocity, durationSeconds);
                String category = selectCategoryForNote(note.key, note.velocity, durationSeconds);

                // 统计音效使用情况
                soundCategoryStats.put(category, soundCategoryStats.getOrDefault(category, 0) + 1);

                // 计算音高和音量
                float pitch = calculatePitch(note.key);
                float volume = Math.min(1.0f, note.velocity / 127.0f * 0.6f);

                // 根据持续时间选择播放策略
                boolean isLongNote = durationSeconds > 1.0;

                if (isLongNote && soundEvent != null) {
                    scheduleLongNote(client, note, startSeconds, durationSeconds,
                            soundEvent, pitch, volume, taskId++);
                } else if (soundEvent != null) {
                    scheduleShortNote(client, note, startSeconds, soundEvent, pitch, volume, taskId++);
                }
            }

            // 打印音效使用统计
            System.out.println("\n=== 音效使用统计 ===");
            for (Map.Entry<String, Integer> entry : soundCategoryStats.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue() + " 次");
            }

            // 安排完成消息
            if (!allNotes.isEmpty()) {
                long lastEndTick = 0;
                for (NoteEvent note : allNotes) {
                    long noteEnd = note.endTick > 0 ? note.endTick : note.tick + midiData.resolution;
                    lastEndTick = Math.max(lastEndTick, noteEnd);
                }

                double totalSeconds = (lastEndTick * microsecondsPerTick) / 1_000_000.0;

                scheduler.schedule(() -> {
                    client.execute(() -> {
                        client.player.sendMessage(
                                net.minecraft.text.Text.literal("§a增强音效播放完成！使用了多种音效"),
                                false
                        );
                    });
                    System.out.println("播放完成，总时长: " + totalSeconds + "秒");
                }, (long) (totalSeconds * 1000) + 1000, TimeUnit.MILLISECONDS);
            }

        } catch (Exception e) {
            System.err.println("播放失败: " + e.getMessage());
            e.printStackTrace();
            stopPlaying();
        }
    }

    /**
     * 安排长音符播放
     */
    private static void scheduleLongNote(MinecraftClient client, NoteEvent note,
                                         double startSeconds, double durationSeconds,
                                         SoundEvent soundEvent, float pitch, float volume, int taskId) {

        // 开始播放
        ScheduledFuture<?> startTask = scheduler.schedule(() -> {
            client.execute(() -> {
                try {
                    SoundInstance sound = createSoundInstance(soundEvent, pitch, volume, true, durationSeconds);
                    client.getSoundManager().play(sound);
                    ACTIVE_SOUNDS.put(taskId, sound);

                    System.out.println(String.format("长音符: %s 持续%.2fs 音高%.2f [%s]",
                            note.getNoteName(), durationSeconds, pitch,
                            soundEvent.getId().getPath()));

                } catch (Exception e) {
                    System.err.println("播放长音符失败: " + e.getMessage());
                }
            });
        }, (long) (startSeconds * 1000), TimeUnit.MILLISECONDS);

        ACTIVE_TASKS.put(taskId, startTask);

        // 停止播放
        ScheduledFuture<?> stopTask = scheduler.schedule(() -> {
            client.execute(() -> {
                SoundInstance sound = ACTIVE_SOUNDS.remove(taskId);
                if (sound != null) {
                    client.getSoundManager().stop(sound);
                }
            });
        }, (long) ((startSeconds + durationSeconds) * 1000), TimeUnit.MILLISECONDS);

        ACTIVE_TASKS.put(taskId + 100000, stopTask);
    }

    /**
     * 安排短音符播放
     */
    private static void scheduleShortNote(MinecraftClient client, NoteEvent note,
                                          double startSeconds, SoundEvent soundEvent,
                                          float pitch, float volume, int taskId) {

        ScheduledFuture<?> playTask = scheduler.schedule(() -> {
            client.execute(() -> {
                try {
                    SoundInstance sound = createSoundInstance(soundEvent, pitch, volume, false, 0);
                    client.getSoundManager().play(sound);

                    if (taskId < 10) { // 只打印前10个音符的详细信息
                        System.out.println(String.format("短音符: %s 音高%.2f [%s]",
                                note.getNoteName(), pitch, soundEvent.getId().getPath()));
                    }

                } catch (Exception e) {
                    System.err.println("播放短音符失败: " + e.getMessage());
                }
            });
        }, (long) (startSeconds * 1000), TimeUnit.MILLISECONDS);

        ACTIVE_TASKS.put(taskId, playTask);
    }

    /**
     * 停止所有播放
     */
    public static void stopPlaying() {
        try {
            // 取消所有任务
            if (scheduler != null && !scheduler.isShutdown()) {
                for (ScheduledFuture<?> task : ACTIVE_TASKS.values()) {
                    if (task != null && !task.isDone()) {
                        task.cancel(false);
                    }
                }
                scheduler.shutdownNow();
                scheduler = null;
            }

            ACTIVE_TASKS.clear();

            // 停止所有声音
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                for (SoundInstance sound : ACTIVE_SOUNDS.values()) {
                    if (sound != null) {
                        client.getSoundManager().stop(sound);
                    }
                }
                client.getSoundManager().stopAll();
            }

            ACTIVE_SOUNDS.clear();
            NOTE_SOUND_MAP.clear();

            System.out.println("已停止所有播放");

        } catch (Exception e) {
            System.err.println("停止播放失败: " + e.getMessage());
        }
    }

    /**
     * 测试单个音效类别
     */
    public static void testSoundCategory(String category) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        List<String> soundIds = SOUND_CATEGORIES.get(category);
        if (soundIds == null || soundIds.isEmpty()) {
            client.player.sendMessage(
                    net.minecraft.text.Text.literal("§c音效类别不存在: " + category),
                    false
            );
            return;
        }

        new Thread(() -> {
            try {
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§e开始测试音效类别: " + category),
                        false
                );

                for (int i = 0; i < Math.min(8, soundIds.size()); i++) {
                    String soundId = soundIds.get(i);
                    SoundEvent sound = getSoundEvent(soundId);

                    if (sound != null) {
                        final int index = i + 1;
                        final String finalSoundId = soundId;
                        client.execute(() -> {
                            client.getSoundManager().play(
                                    PositionedSoundInstance.master(sound, 1.0f, 0.5f)
                            );

                            client.player.sendMessage(
                                    net.minecraft.text.Text.literal("§7" + index + ". " + finalSoundId),
                                    false
                            );
                        });

                        Thread.sleep(800);
                    }
                }

                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§a音效测试完成: " + category),
                        false
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 测试所有音效类别
     */
    public static void testAllCategories() {
        for (String category : SOUND_CATEGORIES.keySet()) {
            testSoundCategory(category);
            try {
                Thread.sleep(8000); // 每个类别间隔8秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 获取当前播放状态
     */
    public static boolean isPlaying() {
        return scheduler != null && !scheduler.isShutdown() && !ACTIVE_TASKS.isEmpty();
    }
}