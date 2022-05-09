/* Copyright 2022 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit;// Created 2022-08-05T21:23:14

import eu.pb4.polymer.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.registry.Registry;

/**
 * Main bootstrap class for the seat, and allowing one to sit.
 *
 * @author Ampflower
 * @since 0.0.0
 **/
public class Main {
	/** Seat entity type. Disallows manual summoning, makes fire immune. */
	public static EntityType<SeatEntity> SEAT = registerEntity("polysit:seat",
			EntityType.Builder.<SeatEntity>create(SeatEntity::new, SpawnGroup.MISC).setDimensions(0, 0)
					.maxTrackingRange(10).disableSummon().makeFireImmune());

	/**
	 * Minimal bootstrap called from
	 * {@link gay.ampflower.polysit.mixin.MixinEntityTypeBootstrap}
	 */
	public static void bootstrap() {
	}

	/**
	 * Setups a {@link UseBlockCallback} to allow for one to sit on stairs & slabs.
	 */
	public static void main() {
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClient && hand == Hand.MAIN_HAND && player.getStackInHand(hand).isEmpty()) {
				var pos = hitResult.getBlockPos();
				var block = world.getBlockState(pos);
				// TODO: Special handling for stairs
				if (block.isIn(BlockTags.STAIRS) || block.isIn(BlockTags.SLABS)) {
					double x = pos.getX() + .5D;
					double y = pos.getY() + .3D;
					double z = pos.getZ() + .5D;
					var seat = new SeatEntity(world, x, y, z);
					if (!world.spawnEntity(seat)) {
						throw new AssertionError(seat + " invalid?!");
					}
					player.startRiding(seat);
					return ActionResult.SUCCESS;
				}
			}
			return ActionResult.PASS;
		});
	}

	public static <T extends Entity> EntityType<T> registerEntity(String id, EntityType.Builder<T> type) {
		var built = type.build(id);
		Registry.register(Registry.ENTITY_TYPE, id, built);
		PolymerEntityUtils.registerType(built);
		return built;
	}
}
