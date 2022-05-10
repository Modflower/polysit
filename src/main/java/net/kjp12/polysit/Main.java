/* Copyright 2022 KJP12
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package net.kjp12.polysit;// Created 2022-08-05T21:23:14

import eu.pb4.polymer.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.registry.Registry;

/**
 * Main bootstrap class for the seat, and allowing one to sit.
 *
 * @author KJP12
 * @since 0.0.0
 **/
public class Main {
	/** Seat entity type. Disallows manual summoning, makes fire immune. */
	public static EntityType<SeatEntity> SEAT = registerEntity("polysit:seat",
			EntityType.Builder.<SeatEntity>create(SeatEntity::new, SpawnGroup.MISC).setDimensions(0, 0)
					.maxTrackingRange(10).disableSummon().makeFireImmune());

	/**
	 * Minimal bootstrap called from
	 * {@link net.kjp12.polysit.mixin.MixinEntityTypeBootstrap}
	 */
	public static void bootstrap() {
	}

	/**
	 * Setups a {@link UseBlockCallback} to allow for one to sit on stairs & slabs.
	 */
	public static void main() {
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClient && hand == Hand.MAIN_HAND && player.getStackInHand(hand).isEmpty()
					&& hitResult.getSide() != Direction.DOWN) {
				var pos = hitResult.getBlockPos();
				if (!world.testBlockState(pos.up(), BlockState::isAir))
					return ActionResult.PASS;
				var block = world.getBlockState(pos);

				if (block.getBlock() instanceof StairsBlock && block.get(StairsBlock.HALF) == BlockHalf.BOTTOM) {
					var direction = block.get(StairsBlock.FACING).getOpposite();
					// Note: Outer vs. Inner for the same side will require the same offset.
					var corner = switch (block.get(StairsBlock.SHAPE)) {
						case INNER_LEFT, OUTER_LEFT -> direction.rotateYCounterclockwise().getVector();
						case INNER_RIGHT, OUTER_RIGHT -> direction.rotateYClockwise().getVector();
						default -> Vec3i.ZERO;
					};

					double x = pos.getX() + .5D + ((direction.getOffsetX() + corner.getX()) * .2D);
					double y = pos.getY() + .3D;
					double z = pos.getZ() + .5D + ((direction.getOffsetZ() + corner.getZ()) * .2D);
					var seat = new SeatEntity(world, x, y, z);
					if (!world.spawnEntity(seat)) {
						throw new AssertionError(seat + " invalid?!");
					}
					player.startRiding(seat);
					return ActionResult.SUCCESS;
				}

				if (block.getBlock() instanceof SlabBlock && block.get(SlabBlock.TYPE) == SlabType.BOTTOM) {
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
