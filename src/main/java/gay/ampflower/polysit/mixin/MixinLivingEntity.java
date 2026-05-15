/* Copyright 2022 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit.mixin;// Created 2022-08-05T23:31:06

import gay.ampflower.polysit.CollisionUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Prevents players from phasing through blocks, especially when the block has
 * nothing underneath.
 *
 * @author Ampflower
 * @since 0.0.0
 **/
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {
	public MixinLivingEntity(EntityType<?> type, Level world) {
		super(type, world);
	}

	@ModifyArg(
		method = "dismountVehicle",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;<init>(DDD)V"),
		index = 1
	)
	private double modifyY(double x, double y, double z) {
		final var fit = CollisionUtil.adjustFit(this, x, y, z);
		if (fit.pose() != null) {
			this.setPose(fit.pose());
		}
		return fit.y();
	}
}
