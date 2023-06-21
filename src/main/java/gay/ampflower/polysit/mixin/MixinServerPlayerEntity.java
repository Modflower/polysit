/* Copyright 2023 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forcefully teleports the player on dismount.
 *
 * This fixes it and I hate the fact that it needs to exist.
 *
 * @author Ampflower
 * @since 0.3.1
 **/
@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity extends PlayerEntity {
	public MixinServerPlayerEntity(final World world, final BlockPos pos, final float yaw,
			final GameProfile gameProfile) {
		super(world, pos, yaw, gameProfile);
	}

	/**
	 * Forces a teleport packet, which for some reason is not sent.
	 *
	 * Blame Mojang for this one's existence.
	 */
	@Inject(method = "requestTeleportAndDismount", at = @At("RETURN"))
	private void onDismount(double x, double y, double z, CallbackInfo ci) {
		this.teleport(x, y, z);
	}
}
