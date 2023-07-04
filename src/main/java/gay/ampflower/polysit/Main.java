/* Copyright 2022 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit;// Created 2022-08-05T21:23:14

import com.mojang.brigadier.Command;
import com.mojang.logging.LogUtils;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * Main bootstrap class for the seat, and allowing one to sit.
 *
 * @author Ampflower
 * @since 0.0.0
 **/
public class Main {
	private static final Logger logger = LogUtils.getLogger();

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
			if (!world.isClient && hand == Hand.MAIN_HAND && (player.isOnGround() || player.hasVehicle())
					&& player.getStackInHand(hand).isEmpty() && hitResult.getSide() != Direction.DOWN) {
				var pos = hitResult.getBlockPos();
				if (!world.testBlockState(pos.up(), BlockState::isAir))
					return ActionResult.PASS;
				var block = world.getBlockState(pos);

				try {
					return sit(world, block, pos, player, false);
				} catch (AssertionError error) {
					player.sendMessage(Text.of("Assertion failed, please report to Polysit: " + error));
					logger.warn(
							"Assertion failed, please report to Polysit - https://github.com/Modflower/polysit/issues",
							error);
				}
			}
			return ActionResult.PASS;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("sit").executes(context -> {
				var source = context.getSource();
				var entity = source.getEntityOrThrow();

				if (entity.hasVehicle()) {
					source.sendError(Text.of("You're already sitting!"));
					return 0;
				}

				if (!entity.isOnGround()) {
					source.sendError(Text.of("Unable to sit while falling."));
					return 0;
				}

				var pos = entity.getBlockPos();
				var world = entity.getWorld();
				var state = world.getBlockState(pos);
				var shape = state.getCollisionShape(world, pos, ShapeContext.of(entity));

				// Skip if it's not solid or taller than 1 block.
				if (state.isAir() || shape.isEmpty() || shape.getMax(Direction.Axis.Y) > 1D) {
					pos = pos.down();
					state = world.getBlockState(pos);
					shape = state.getCollisionShape(world, pos, ShapeContext.of(entity));
				}

				if (state.isAir() || shape.isEmpty()) {
					source.sendError(Text.of("It appears you're trying to sit on air."));
					return 0;
				}

				try {
					if (sit(world, state, pos, entity, true).isAccepted()) {
						return Command.SINGLE_SUCCESS;
					}

					double x = entity.getX();
					double y = pos.getY() + Math.min(assertFinite(shape.getMax(Direction.Axis.Y), 's'), 1D) - 0.2D;
					double z = entity.getZ();

					sit(world, entity, x, y, z);
				} catch (AssertionError error) {
					source.sendError(Text.of("Assertion failed, please report to Polysit: " + error));
					logger.warn(
							"Assertion failed, please report to Polysit - https://github.com/Modflower/polysit/issues",
							error);
				}

				return Command.SINGLE_SUCCESS;
			}));
		});
	}

	public static ActionResult sit(@NotNull final World world, @NotNull final BlockState state,
			@NotNull final BlockPos pos, @NotNull final Entity entity, final boolean command) {
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

				BlockPos head;

				// Get the head of the bed block to mimic vanilla.
				if (state.get(BedBlock.PART) == BedPart.HEAD) {
					head = pos;
				} else {
					head = pos.offset(state.get(BedBlock.FACING));
					if (!world.getBlockState(head).isOf(state.getBlock())) {
						head = null;
					}
				}

				// Set the spawn point for the player as one would expect.
				if (head != null) {
					player.setSpawnPoint(world.getRegistryKey(), head, player.getYaw(), false, true);
				}
			}

			double x = pos.getX() + .5D;
			double y = pos.getY() + .3D;
			double z = pos.getZ() + .5D;
			sit(world, entity, x, y, z);
			return ActionResult.SUCCESS;
		}

		if (command && (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof FenceGateBlock)) {
			double x = pos.getX() + .5D;
			double y = pos.getY() + .75D;
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
		assertDistance(entity, seat);
		entity.startRiding(seat);
	}

	public static <T extends Entity> EntityType<T> registerEntity(String id, EntityType.Builder<T> type) {
		var built = type.build(id);
		Registry.register(Registries.ENTITY_TYPE, id, built);
		PolymerEntityUtils.registerType(built);
		return built;
	}

	public static BlockPos blockPosOfFloored(double x, double y, double z) {
		return new BlockPos(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
	}

	public static void assertDistance(Entity from, Entity to) {
		double d = from.distanceTo(to);
		if (assertFinite(d, 'd') > 25)
			throw new RuntimeException(to + " out of range of " + from);
	}

	public static double assertHori(double d, char coord) {
		if (d < -30000000 || d > 30000000)
			throw new RuntimeException(coord + ": " + d);
		return d;
	}

	public static double assertVert(double d, char coord) {
		if (d < -256 || d > 512)
			throw new RuntimeException(coord + ": " + d);
		return d;
	}

	public static double assertFinite(double d, char coord) {
		if (!Double.isFinite(d))
			throw new RuntimeException(coord + ": " + d);
		return d;
	}
}
