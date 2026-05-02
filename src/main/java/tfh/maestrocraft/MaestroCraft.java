package tfh.maestrocraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import tfh.maestrocraft.gui.MidiImportScreen;
import tfh.maestrocraft.gui.SoundRenderScreen;

public class MaestroCraft implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 注册客户端初始化代码
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.currentScreen instanceof GameMenuScreen) {
				GameMenuScreen screen = (GameMenuScreen) client.currentScreen;

				// 获取现有按钮列表
				var buttons = Screens.getButtons(screen);

				// 添加"导入MIDI"按钮
				buttons.add(
						ButtonWidget.builder(Text.translatable("menu.maestrocraft.import"), button -> {
							client.setScreen(new MidiImportScreen(screen));
						}).dimensions(10, 10, 100, 20).build()
				);

				// 添加"渲染音效"按钮
				buttons.add(
						ButtonWidget.builder(Text.translatable("menu.maestrocraft.rendersound"), button -> {
							client.setScreen(new SoundRenderScreen(screen));
						}).dimensions(10, 35, 100, 20).build()
				);
			}
		});
	}
}