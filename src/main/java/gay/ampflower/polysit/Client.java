/* Copyright 2025 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * @author Ampflower
 * @since 0.7.1
 **/
public class Client implements ClientModInitializer {
	private static final KeyMapping.Category polysitKeybind = KeyMapping.Category.register(Main.id("gameplay"));

	private static KeyMapping sitBinding;

	@Override
	public void onInitializeClient() {
		sitBinding = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.polysit.sit", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, polysitKeybind));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (sitBinding.consumeClick()) {
				final var play = client.getConnection();

				if (play != null && play.getCommands().findNode(List.of("sit")) != null) {
					play.sendCommand("sit");
				}
			}
		});
	}
}
