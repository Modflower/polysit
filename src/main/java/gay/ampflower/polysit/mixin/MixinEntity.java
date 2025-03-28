/* Copyright 2025 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Ampflower
 * @since 0.8.5
 **/
@Mixin(Entity.class)
abstract class MixinEntity {
	@Shadow
	public abstract Vec3d getVelocity();

	@Inject(method = "requestTeleportAndDismount", at = @At("RETURN"))
	protected void onDismount(double x, double y, double z, CallbackInfo ci) {
	}
}
