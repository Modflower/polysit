/* Copyright 2022 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package net.kjp12.polysit;// Created 2022-08-05T21:23:14

import com.mojang.brigadier.Command;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import static net.minecraft.server.command.CommandManager.literal;

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
	 * {@link net.kjp12.polysit.mixin.MixinEntityTypeBootstrap}
	 */
	public static void bootstrap() {
	}

	/**
	 * Setups a {@link UseBlockCallback} to allow for one to sit on stairs & slabs.
	 */
	public static void main() {
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClient && hand == Hand.MAIN_HAND
					&& (player.isOnGround() || player.isInvulnerableTo(DamageSource.FALL))
					&& player.getStackInHand(hand).isEmpty() && hitResult.getSide() != Direction.DOWN) {
				var pos = hitResult.getBlockPos();
				if (!world.testBlockState(pos.up(), BlockState::isAir))
					return ActionResult.PASS;
				var block = world.getBlockState(pos);

				return sit(world, block, pos, player, false);
			}
			return ActionResult.PASS;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("sit").executes(context -> {
				var source = context.getSource();
				var entity = source.getEntityOrThrow();

				if (!entity.isOnGround() && !entity.isInvulnerableTo(DamageSource.FALL)) {
					source.sendError(Text.of("Unable to sit while falling."));
					return 0;
				}

				var pos = entity.getBlockPos();
				var world = entity.getWorld();
				var state = world.getBlockState(pos);
				var shape = state.getCollisionShape(world, pos, ShapeContext.of(entity));

				if (state.isAir() || shape.isEmpty()) {
					pos = pos.down();
					state = world.getBlockState(pos);
					shape = state.getCollisionShape(world, pos, ShapeContext.of(entity));
				}

				if (sit(world, state, pos, entity, true).isAccepted()) {
					return Command.SINGLE_SUCCESS;
				}

				double x = entity.getX();
				double y = pos.getY() + Math.min(shape.getMax(Direction.Axis.Y), 1D) - 0.2D;
				double z = entity.getZ();

				sit(world, entity, x, y, z);

				return Command.SINGLE_SUCCESS;
			}));
		});
	}

	public static ActionResult sit(World world, BlockState state, BlockPos pos, Entity entity, boolean command) {
		if (state.getBlock() instanceof StairsBlock && state.get(StairsBlock.HALF) == BlockHalf.BOTTOM) {
			var direction = state.get(StairsBlock.FACING).getOpposite();
			// Note: Outer vs. Inner for the same side will require the same offset.
			var corner = switch (state.get(StairsBlock.SHAPE)) {
				case INNER_LEFT, OUTER_LEFT -> direction.rotateYCounterclockwise().getVector();
				case INNER_RIGHT, OUTER_RIGHT -> direction.rotateYClockwise().getVector();
				default -> Vec3i.ZERO;
			};

			double x = pos.getX() + .5D + ((direction.getOffsetX() + corner.getX()) * .2D);
			double y = pos.getY() + .3D;
			double z = pos.getZ() + .5D + ((direction.getOffsetZ() + corner.getZ()) * .2D);
			sit(world, entity, x, y, z);
			return ActionResult.SUCCESS;
		}

		if (state.getBlock() instanceof SlabBlock && state.get(SlabBlock.TYPE) == SlabType.BOTTOM) {
			double x = pos.getX() + .5D;
			double y = pos.getY() + .3D;
			double z = pos.getZ() + .5D;
			sit(world, entity, x, y, z);
			return ActionResult.SUCCESS;
		}

		if (state.getBlock() instanceof BedBlock && world.isDay()) {
			if (!command && entity instanceof ServerPlayerEntity player) {
				// Let the bed explode as it should normally.
				if (!BedBlock.isBedWorking(world)) {
					return ActionResult.PASS;
				}

				// Set the spawn point for the player as one would expect.
				player.setSpawnPoint(world.getRegistryKey(), pos, player.getYaw(), false, true);
			}

			double x = pos.getX() + .5D;
			double y = pos.getY() + .3D;
			double z = pos.getZ() + .5D;
			sit(world, entity, x, y, z);
			return ActionResult.SUCCESS;
		}

		return ActionResult.PASS;
	}

	public static void sit(World world, Entity entity, double x, double y, double z) {
		var seat = new SeatEntity(world, x, y, z);
		if (!world.spawnEntity(seat)) {
			throw new AssertionError(seat + " invalid?!");
		}
		entity.startRiding(seat);
	}

	public static <T extends Entity> EntityType<T> registerEntity(String id, EntityType.Builder<T> type) {
		var built = type.build(id);
		Registry.register(Registries.ENTITY_TYPE, id, built);
		PolymerEntityUtils.registerType(built);
		return built;
	}
}
