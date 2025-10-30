/* Copyright 2023 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit.mixin;

import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Debug;
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
@Mixin(ServerPlayerEntity.class)
@Debug(export = true)
public abstract class MixinServerPlayerEntity extends MixinEntity {
	@Shadow
	public ServerPlayNetworkHandler networkHandler;

	/**
	 * Modified requestTeleport for also sending the current velocity. Enforces that
	 * the client can't just shove the player because there's a block edge to go to.
	 *
	 * @since 0.9.5
	 */
	@Override
	protected void onDismount(final double x, final double y, final double z, final CallbackInfo ci) {
		System.out.printf("%f, %f, %f, %s\n", x, y, z, ci);
		this.networkHandler.requestTeleport(
				new EntityPosition(new Vec3d(x, y, z), this.getVelocity(), 0.F, 0.F),
				PositionFlag.ROT
		);
	}
}
