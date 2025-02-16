/* Copyright 2025 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * @author Ampflower
 * @since 0.7.1
 **/
public class Client implements ClientModInitializer {
	private static KeyBinding sitBinding;

	@Override
	public void onInitializeClient() {
		sitBinding = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.polysit.sit", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.polysit.gameplay"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (sitBinding.wasPressed()) {
				final var play = client.getNetworkHandler();

				if (play != null) {
					play.sendCommand("sit");
				}
			}
		});
	}
}
