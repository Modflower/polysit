/* Copyright 2023 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forcefully teleports the player on dismount.
 *
 * This fixes it and I hate the fact that it needs to exist.
 *
 * @author Ampflower
 * @since 0.3.1
 **/
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerEntity extends MixinEntity {
	@Shadow
	public ServerGamePacketListenerImpl connection;

	/**
	 * Modified requestTeleport for also sending the current velocity. Enforces that
	 * the client can't just shove the player because there's a block edge to go to.
	 *
	 * @since 0.9.5
	 */
	@Override
	protected void onDismount(final double x, final double y, final double z, final CallbackInfo ci) {
		this.connection.teleport(
			new PositionMoveRotation(new Vec3(x, y, z), this.getDeltaMovement(), 0.F, 0.F),
			Relative.ROTATION
		);
	}
}
