/* Copyright 2023 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit.mixin;

import gay.ampflower.polysit.internal.HackEntity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Forcefully teleports the player on dismount.
 *
 * This fixes it and I hate the fact that it needs to exist.
 *
 * @author Ampflower
 * @since 0.3.1
 **/
@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity implements HackEntity {
	@Shadow
	public ServerPlayNetworkHandler networkHandler;

	/**
	 * Modified requestTeleport for also sending the current velocity. Enforces that
	 * the client can't just shove the player because there's a block edge to go to.
	 *
	 * @since 0.8.5
	 */
	@Override
	@ApiStatus.Internal
	public void polysit$requestTeleportOnDismount(final Vec3d position, final Vec3d velocity) {
		this.networkHandler.requestTeleport(new PlayerPosition(position, velocity, 0.F, 0.F), PositionFlag.ROT);
	}
}
