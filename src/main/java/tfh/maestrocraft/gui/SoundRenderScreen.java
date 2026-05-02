package tfh.maestrocraft.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import tfh.maestrocraft.audio.*;

import java.nio.file.Paths;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SoundRenderScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget filenameField;
    private ButtonWidget playButton;
    private ButtonWidget stopButton;
    private ButtonWidget wavButton;  // 声明为字段
    private ButtonWidget mp3Button;  // 声明为字段
    private String statusMessage = "";
    private String currentFormat = "wav";

    public SoundRenderScreen(Screen parent) {
        super(Text.translatable("screen.maestrocraft.soundrender.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // MIDI文件名输入
        this.filenameField = new TextFieldWidget(
                this.textRenderer,
                centerX - 150,
                centerY - 90,
                300,
                20,
                Text.translatable("textfield.maestrocraft.midifilename")
        );
        this.filenameField.setPlaceholder(Text.translatable("textfield.maestrocraft.midiplaceholder"));
        this.addDrawableChild(this.filenameField);

        // 第一行：测试按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.maestrocraft.testpitch"),
                button -> testActualPitch()
        ).dimensions(centerX - 150, centerY - 60, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.maestrocraft.testshortsounds"),
                button -> UnifiedSoundPlayer.testShortSounds()
        ).dimensions(centerX - 45, centerY - 60, 90, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.maestrocraft.testsounds"),
                button -> testSoundCategories()
        ).dimensions(centerX + 50, centerY - 60, 100, 20).build());

        // 第二行：播放按钮
        this.playButton = ButtonWidget.builder(
                Text.translatable("button.maestrocraft.playsound"),
                button -> startStandardPlaying()
        ).dimensions(centerX - 150, centerY - 35, 100, 20).build();
        this.addDrawableChild(this.playButton);

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.maestrocraft.playunified"),
                button -> startUnifiedPlaying()
        ).dimensions(centerX - 45, centerY - 35, 90, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.maestrocraft.playenhanced"),
                button -> startEnhancedPlaying()
        ).dimensions(centerX + 50, centerY - 35, 100, 20).build());

        // 第三行：音频下载按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§2音频-标准版"),
                button -> downloadAudio("standard")
        ).dimensions(centerX - 150, centerY - 10, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§2音频-短音效"),
                button -> downloadAudio("unified")
        ).dimensions(centerX - 45, centerY - 10, 90, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§2音频-增强版"),
                button -> downloadAudio("enhanced")
        ).dimensions(centerX + 50, centerY - 10, 100, 20).build());

        // 第四行：格式选择和停止按钮
        // 先初始化格式选择按钮
        this.wavButton = ButtonWidget.builder(
                Text.literal("WAV"),
                button -> setFormat("wav")
        ).dimensions(centerX - 150, centerY + 15, 60, 20).build();

        this.mp3Button = ButtonWidget.builder(
                Text.literal("MP3"),
                button -> setFormat("mp3")
        ).dimensions(centerX - 85, centerY + 15, 60, 20).build();

        this.addDrawableChild(this.wavButton);
        this.addDrawableChild(this.mp3Button);

        // 测试按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("测试音频"),
                button -> testAudioGeneration()
        ).dimensions(centerX - 20, centerY + 15, 80, 20).build());

        // 第五行：停止和返回按钮
        this.stopButton = ButtonWidget.builder(
                Text.translatable("button.maestrocraft.stopsound"),
                button -> stopPlaying()
        ).dimensions(centerX - 150, centerY + 40, 100, 20).build();
        this.stopButton.active = false;
        this.addDrawableChild(this.stopButton);

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.maestrocraft.back"),
                button -> this.close()
        ).dimensions(centerX - 50, centerY + 40, 100, 20).build());

        // 初始化格式按钮状态
        updateFormatButtons();
    }

    private void testActualPitch() {
        if (client != null && client.player != null) {
            PitchCalculator.showAllPitches();
            PitchTester.playTestScale();
            client.player.sendMessage(
                    net.minecraft.text.Text.translatable("test.pitch.starting"),
                    false
            );
        }
    }

    private void testSoundCategories() {
        if (client != null && client.player != null) {
            new Thread(() -> {
                client.player.sendMessage(
                        net.minecraft.text.Text.translatable("test.sounds.starting"),
                        false
                );

                String[] categories = {"note_block", "doors", "fire_flint", "block_break", "experience_magic"};

                for (String category : categories) {
                    EnhancedSoundPlayer.testSoundCategory(category);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }).start();
        }
    }

    private void testAudioGeneration() {
        try {
            if (client != null && client.player != null) {
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§e创建测试音频文件..."),
                        false
                );
            }

            AudioFileGenerator.createTestFile(currentFormat);

        } catch (Exception e) {
            e.printStackTrace();
            if (client != null && client.player != null) {
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§c测试失败: " + e.getMessage()),
                        false
                );
            }
        }
    }

    private void startStandardPlaying() {
        startPlaying("standard", () -> FixedPitchSoundPlayer.playMidiAsMCSounds(getMidiPath()));
    }

    private void startUnifiedPlaying() {
        startPlaying("unified", () -> UnifiedSoundPlayer.playMidiWithUnifiedSounds(getMidiPath()));
    }

    private void startEnhancedPlaying() {
        startPlaying("enhanced", () -> EnhancedSoundPlayer.playMidiWithEnhancedSounds(getMidiPath()));
    }

    private void startPlaying(String type, Runnable player) {
        String filename = filenameField.getText();

        if (filename.isEmpty() || !filename.endsWith(".mid")) {
            statusMessage = "§c请输入有效的.mid文件名";
            return;
        }

        try {
            String midiPath = getMidiPath();

            if (this.client != null) {
                this.client.execute(() -> {
                    playButton.active = false;
                    stopButton.active = true;
                    statusMessage = "§e正在解析MIDI文件...";
                });
            }

            new Thread(() -> {
                try {
                    player.run();

                    if (this.client != null) {
                        this.client.execute(() -> {
                            playButton.active = true;
                            stopButton.active = false;
                            statusMessage = "§a播放完成！";
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    statusMessage = "§c播放失败: " + e.getMessage();

                    if (this.client != null) {
                        this.client.execute(() -> {
                            playButton.active = true;
                            stopButton.active = false;
                        });
                    }
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
            statusMessage = "§c文件错误: " + e.getMessage();
        }
    }

    private void downloadAudio(String version) {
        String filename = filenameField.getText();

        if (filename.isEmpty() || !filename.endsWith(".mid")) {
            statusMessage = "§c请输入有效的.mid文件名";
            return;
        }

        try {
            String midiPath = getMidiPath();

            // 检查文件是否存在
            File midiFile = new File(midiPath);
            if (!midiFile.exists()) {
                statusMessage = "§c找不到MIDI文件: " + filename;
                return;
            }

            statusMessage = "§e开始生成" + getVersionName(version) + "音频文件...";

            // 生成音频文件
            AudioFileGenerator.generateAudioFile(midiPath, version, currentFormat);

        } catch (Exception e) {
            e.printStackTrace();
            statusMessage = "§c准备下载失败: " + e.getMessage();
        }
    }

    private void setFormat(String format) {
        currentFormat = format;
        updateFormatButtons();

        String formatName = "wav".equals(format) ? "WAV" : "MP3";

        if (this.client != null && this.client.player != null) {
            if ("mp3".equals(format)) {
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§a已选择MP3格式"),
                        false
                );
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§e提示：MP3转换需要ffmpeg支持"),
                        false
                );
            } else {
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§a已选择WAV格式"),
                        false
                );
            }
        }
    }

    private void updateFormatButtons() {
        if (wavButton != null) {
            wavButton.setMessage(Text.literal(currentFormat.equals("wav") ? "§a▶ WAV" : "WAV"));
        }
        if (mp3Button != null) {
            mp3Button.setMessage(Text.literal(currentFormat.equals("mp3") ? "§a▶ MP3" : "MP3"));
        }
    }

    private void stopPlaying() {
        UnifiedSoundPlayer.stopPlaying();
        EnhancedSoundPlayer.stopPlaying();
        FixedPitchSoundPlayer.stopPlaying();
        playButton.active = true;
        stopButton.active = false;
        statusMessage = "§7已停止播放";
    }

    private String getMidiPath() {
        return Paths.get(
                MinecraftClient.getInstance().runDirectory.getAbsolutePath(),
                "MIDI", filenameField.getText()
        ).toString();
    }

    private String getVersionName(String version) {
        switch (version) {
            case "unified": return "统一短音效版";
            case "enhanced": return "增强版";
            default: return "标准版";
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 标题
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                this.title,
                centerX,
                centerY - 120,
                0xFFFFFF
        );

        // 标签
        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("label.maestrocraft.midifile"),
                centerX - 150,
                centerY - 105,
                0xCCCCCC
        );

        // 状态消息
        if (!statusMessage.isEmpty()) {
            String message = statusMessage;
            int color = 0xFFFFFF;

            if (message.startsWith("§")) {
                char colorCode = message.charAt(1);
                message = message.substring(2);

                switch (colorCode) {
                    case 'a': color = 0x00FF00; break;
                    case 'c': color = 0xFF0000; break;
                    case 'e': color = 0xFFFF00; break;
                    case '7': color = 0xAAAAAA; break;
                    default: color = 0xFFFFFF; break;
                }
            }

            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(message),
                    centerX,
                    centerY + 70,
                    color
            );
        }

        // 播放状态
        if (UnifiedSoundPlayer.isPlaying() || EnhancedSoundPlayer.isPlaying() || FixedPitchSoundPlayer.isPlaying()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.translatable("playback.status.playing"),
                    centerX,
                    centerY + 90,
                    0xFFFF00
            );
        }

        // 说明文本
        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("description.line1"),
                centerX - 150,
                centerY + 110,
                0x888888
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.translatable("description.line2"),
                centerX - 150,
                centerY + 125,
                0x888888
        );

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("§7当前格式: " + currentFormat.toUpperCase()),
                centerX - 150,
                centerY + 140,
                0x888888
        );

        // 版本信息
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("§7MaestroCraft v1.0"),
                this.width - 100,
                this.height - 20,
                0x666666
        );
    }

    @Override
    public void tick() {
        super.tick();

        // 更新按钮状态
        boolean isPlaying = UnifiedSoundPlayer.isPlaying() ||
                EnhancedSoundPlayer.isPlaying() ||
                FixedPitchSoundPlayer.isPlaying();

        if (isPlaying) {
            playButton.active = false;
            stopButton.active = true;
        }
    }

    @Override
    public void close() {
        stopPlaying();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}