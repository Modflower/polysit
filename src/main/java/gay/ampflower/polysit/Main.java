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
import net.minecraft.SharedConstants;
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
import net.minecraft.world.BlockView;
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

	public static final double HORIZONTAL_CENTER_OFFSET = 0.5D;
	public static final double UPDATE_HEIGHT_OFFSET = 0.25D;
	public static final double VERTICAL_SLAB_OFFSET;
	public static final double VERTICAL_FENCE_OFFSET;

	static final String VERSION_TAG_NAME = "polysit:runtimeVersion";
	static final int RUNTIME_VERSION;
	private static final double[] OFFSET_DELTA = { 0, UPDATE_HEIGHT_OFFSET };

	static {
		final var currentVersion = SharedConstants.getGameVersion().getSaveVersion().getId();

		// No need to pollute the class fields.
		final double verticalSlabOffset = 0.35D;
		final double verticalFenceOffset = 0.75D;
		final int updateChangingOffset = 3572; // 1.20.2-pre.1; <=23w35a are no-boots

		// Adjusts offset for >1.20.2-rc.1.
		if (currentVersion >= updateChangingOffset) {
			VERTICAL_SLAB_OFFSET = verticalSlabOffset + UPDATE_HEIGHT_OFFSET;
			VERTICAL_FENCE_OFFSET = verticalFenceOffset + UPDATE_HEIGHT_OFFSET;
			RUNTIME_VERSION = 1;
		} else {
			VERTICAL_SLAB_OFFSET = verticalSlabOffset;
			VERTICAL_FENCE_OFFSET = verticalFenceOffset;
			RUNTIME_VERSION = 0;
		}
	}

	static double delta(int runtimeVersion) {
		if (runtimeVersion > RUNTIME_VERSION) {
			return -sum(OFFSET_DELTA, RUNTIME_VERSION + 1, runtimeVersion);
		}
		if (runtimeVersion < RUNTIME_VERSION) {
			return sum(OFFSET_DELTA, runtimeVersion + 1, RUNTIME_VERSION);
		}
		return 0D;
	}

	private static double sum(double[] array, int from, int to) {
		double sum = 0D;
		for (; from <= to; from++) {
			sum += array[from];
		}
		return sum;
	}

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
			if (!world.isClient && hand == Hand.MAIN_HAND
					&& (player.isOnGround() || player.hasVehicle() || player.isCreative())
					&& player.getStackInHand(hand).isEmpty() && hitResult.getSide() != Direction.DOWN) {
				var pos = hitResult.getBlockPos();
				// TODO: Make this check the entire collision area where the player fits.
				// This would accounts for Pehkui and other mods that modify the hit box.
				if (!world.testBlockState(pos.up(), BlockState::isAir))
					return ActionResult.PASS;

				final var block = world.getBlockState(pos);
				final var topHeight = getTopHeight(world, block, pos, player);
				final var relative = pos.getY() + topHeight - getEffectiveEntityY(player);

				if (relative > JumpHeightUtil.maxJumpHeight(player)) {
					return ActionResult.PASS;
				}

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
				var topHeight = getTopHeight(world, state, pos, entity);

				// Skip if it's not solid or taller than jump height.
				if (topHeight < 0.D || topHeight > JumpHeightUtil.maxJumpHeight(entity)) {
					pos = pos.down();
					state = world.getBlockState(pos);
					topHeight = getTopHeight(world, state, pos, entity);
				}

				if (topHeight < 0.D) {
					source.sendError(Text.of("It appears you're trying to sit on air."));
					return 0;
				}

				try {
					if (sit(world, state, pos, entity, true).isAccepted()) {
						return Command.SINGLE_SUCCESS;
					}

					double x = entity.getX();
					double y = pos.getY() + assertFinite(topHeight, 's') - 0.2D;
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

	public static double getEffectiveEntityY(Entity entity) {
		if (!entity.hasVehicle()) {
			return entity.getY();
		}

		final var pos = entity.getBlockPos();
		final var world = entity.getWorld();
		final var block = world.getBlockState(pos);
		final var height = getTopHeight(world, block, pos, entity);

		return Math.max(pos.getY() + height, entity.getY());
	}

	public static double getTopHeight(BlockView world, BlockState state, BlockPos pos, Entity entity) {
		if (state.isAir()) {
			return -1.D;
		}

		if (state.getBlock() instanceof StairsBlock && state.get(StairsBlock.HALF) == BlockHalf.BOTTOM) {
			return 0.5D;
		}

		final var shape = state.getCollisionShape(world, pos, ShapeContext.of(entity));

		return shape.getMax(Direction.Axis.Y);
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

			double x = pos.getX() + HORIZONTAL_CENTER_OFFSET + ((direction.getOffsetX() + corner.getX()) * .2D);
			double y = pos.getY() + VERTICAL_SLAB_OFFSET;
			double z = pos.getZ() + HORIZONTAL_CENTER_OFFSET + ((direction.getOffsetZ() + corner.getZ()) * .2D);
			sit(world, entity, x, y, z);
			return ActionResult.SUCCESS;
		}

		if (state.getBlock() instanceof SlabBlock && state.get(SlabBlock.TYPE) == SlabType.BOTTOM) {
			double x = pos.getX() + HORIZONTAL_CENTER_OFFSET;
			double y = pos.getY() + VERTICAL_SLAB_OFFSET;
			double z = pos.getZ() + HORIZONTAL_CENTER_OFFSET;
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

			double x = pos.getX() + HORIZONTAL_CENTER_OFFSET;
			double y = pos.getY() + VERTICAL_SLAB_OFFSET;
			double z = pos.getZ() + HORIZONTAL_CENTER_OFFSET;
			sit(world, entity, x, y, z);
			return ActionResult.SUCCESS;
		}

		if (command && (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof FenceGateBlock)) {
			double x = pos.getX() + HORIZONTAL_CENTER_OFFSET;
			double y = pos.getY() + VERTICAL_FENCE_OFFSET;
			double z = pos.getZ() + HORIZONTAL_CENTER_OFFSET;
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
