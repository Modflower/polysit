/* Copyright 2022 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package net.kjp12.polysit.mixin;// Created 2022-08-05T23:06:01

import net.kjp12.polysit.Main;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Bootstraps Main & the seat entity directly at registry-creation time.
 *
 * @implNote The static block is an implicit
 *           {@code @Inject(method = "&lt;clinit&gt;", at = @At("TAIL"))}.
 * @author Ampflower
 * @since 0.0.0
 **/
@Mixin(EntityType.class)
public class MixinEntityTypeBootstrap {
	static {
		Main.bootstrap();
	}
}
