package tfh.maestrocraft.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import tfh.maestrocraft.midi.MidiParser;
import tfh.maestrocraft.schematic.SchematicGenerator;

import java.nio.file.Paths;

public class MidiImportScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget filenameField;

    public MidiImportScreen(Screen parent) {
        super(Text.translatable("screen.maestrocraft.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Filename input field
        this.filenameField = new TextFieldWidget(
                this.textRenderer,
                centerX - 150,
                centerY - 30,
                300,
                20,
                Text.translatable("textfield.maestrocraft.filename")
        );
        this.filenameField.setPlaceholder(Text.translatable("textfield.maestrocraft.placeholder"));
        this.addDrawableChild(this.filenameField);

        // Generate button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.maestrocraft.generate"),
                button -> this.generateSchematic()
        ).dimensions(centerX - 50, centerY + 10, 100, 20).build());

        // Back button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("button.maestrocraft.back"),
                button -> this.close()
        ).dimensions(centerX - 50, centerY + 40, 100, 20).build());
    }

    private void generateSchematic() {
        String filename = filenameField.getText();
        if (filename.isEmpty() || !filename.endsWith(".mid")) {
            // Show error message
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.translatable("message.maestrocraft.invalid_filename"), false);
            }
            return;
        }

        // 先运行测试
        try {
            SchematicGenerator generator = new SchematicGenerator();
            generator.generateTestSchematicOnly(); // 改为调用正确的方法
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.translatable("Test schematic created"), false);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.translatable("Test failed: " + e.getMessage()), false);
            }
            return;
        }

        // 然后处理MIDI文件
        try {
            String midiPath = Paths.get(MinecraftClient.getInstance().runDirectory.getAbsolutePath(), "MIDI", filename).toString();
            MidiParser parser = new MidiParser();
            MidiParser.MidiData midiData = parser.parse(midiPath);

            SchematicGenerator generator = new SchematicGenerator();
            generator.generate(midiData, filename.replace(".mid", ""));

            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.translatable("message.maestrocraft.success"), false);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.translatable("message.maestrocraft.error", e.getMessage()), false);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Title
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                this.title,
                centerX,
                centerY - 60,
                0xFFFFFF
        );

        // Path info
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.translatable("text.maestrocraft.path",
                        Paths.get(MinecraftClient.getInstance().runDirectory.getAbsolutePath(), "MIDI").toString()),
                centerX,
                centerY - 45,
                0xAAAAAA
        );
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}