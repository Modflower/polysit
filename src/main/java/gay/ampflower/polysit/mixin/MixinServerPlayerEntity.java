/* Copyright 2023 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
	@Shadow
	public ServerPlayNetworkHandler networkHandler;

	@Shadow
	public abstract void requestTeleport(final double destX, final double destY, final double destZ);

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
		// Modified requestTeleport for also sending the current velocity.
		// Enforces that the client can't just shove the player because there's a block
		// edge to go to.
		this.networkHandler.requestTeleport(new PlayerPosition(new Vec3d(x, y, z), this.getVelocity(), 0.F, 0.F),
				PositionFlag.ROT);
	}

	/**
	 * Forces a passenger update packet to the player, removing the chance for the
	 * client to assert movement.
	 */
	@Inject(method = "startRiding", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;requestTeleport(Lnet/minecraft/entity/player/PlayerPosition;Ljava/util/Set;)V"))
	private void onMount(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> ci) {
		this.networkHandler.sendPacket(new EntityPassengersSetS2CPacket(vehicle));
	}
}
