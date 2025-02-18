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
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
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
import net.minecraft.util.math.Vec3d;
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
	public static final double UPDATE_HEIGHT_OFFSET = 0.20D;
	public static final double VERTICAL_SLAB_OFFSET;
	public static final double VERTICAL_FENCE_OFFSET;
	public static final double VERTICAL_SOLID_OFFSET;
	public static final double VERTICAL_CHECK_OFFSET;

	static final String VERSION_TAG_NAME = "polysit:runtimeVersion";
	static final int RUNTIME_VERSION;
	private static final double[] OFFSET_DELTA = { 0, UPDATE_HEIGHT_OFFSET };

	static {
		final var currentVersion = SharedConstants.getGameVersion().getSaveVersion().getId();

		// No need to pollute the class fields.
		final double verticalSolidOffset = -0.20D;
		final double verticalSlabOffset = 0.30D;
		final double verticalFenceOffset = 1 + verticalSolidOffset;
		final int updateChangingOffset = 3572; // 1.20.2-pre.1; <=23w35a are no-boots

		// Adjusts offset for >1.20.2-rc.1.
		if (currentVersion >= updateChangingOffset) {
			VERTICAL_SLAB_OFFSET = verticalSlabOffset + UPDATE_HEIGHT_OFFSET;
			VERTICAL_FENCE_OFFSET = verticalFenceOffset + UPDATE_HEIGHT_OFFSET;
			VERTICAL_SOLID_OFFSET = verticalSolidOffset + UPDATE_HEIGHT_OFFSET;
			VERTICAL_CHECK_OFFSET = -UPDATE_HEIGHT_OFFSET;
			RUNTIME_VERSION = 1;
		} else {
			VERTICAL_SLAB_OFFSET = verticalSlabOffset;
			VERTICAL_FENCE_OFFSET = verticalFenceOffset;
			VERTICAL_SOLID_OFFSET = verticalSolidOffset;
			VERTICAL_CHECK_OFFSET = 0;
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

				if (hitResult.squaredDistanceTo(player) > 5 * 5) {
					return ActionResult.PASS;
				}

				final var block = world.getBlockState(pos);
				final var topHeight = getTopHeight(world, block, pos, player);
				final var relative = pos.getY() + topHeight - getEffectiveEntityY(player);

				if (relative > JumpHeightUtil.maxJumpHeight(player)) {
					return ActionResult.PASS;
				}

				return sit(world, block, pos, player, topHeight, false);
			}
			return ActionResult.PASS;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("sit").executes(context -> {
				var source = context.getSource();
				var entity = source.getEntityOrThrow();

				if (entity.hasVehicle()) {
					entity.stopRiding();
					return Command.SINGLE_SUCCESS;
				}

				BlockPos pos;
				var world = entity.getWorld();
				var ground = CollisionUtil.ground(entity);

				if (entity.getY() - ground > 1 || entity.fallDistance > 0.15F) {
					source.sendError(Text.of("It appears you're trying to sit on air."));
					return 0;
				}

				// Check if floored Y == ground, and move down if yes.
				if (ground % 1 == 0 || isAir(entity.getBlockStateAtPos(), entity.getBlockPos(), entity)) {
					pos = blockPosOfFloored(entity.getX(), ground - 1, entity.getZ());
				} else {
					pos = blockPosOfFloored(entity.getX(), ground, entity.getZ());
				}
				var state = world.getBlockState(pos);
				var topHeight = getTopHeight(world, state, pos, entity);

				// Skip if it's not solid or taller than jump height.
				if (topHeight < 0.D) {
					source.sendError(Text.of("It appears you're trying to sit on air."));
					return 0;
				}

				if (sit(world, state, pos, entity, topHeight, true).isAccepted()) {
					return Command.SINGLE_SUCCESS;
				}

				double x = entity.getX();
				double y = ground + VERTICAL_SOLID_OFFSET;
				double z = entity.getZ();

				if (sit(world, entity, x, y, z, ground).isAccepted()) {
					return Command.SINGLE_SUCCESS;
				}

				source.sendError(Text.of("You can't sit here, your seat is obstructed."));

				return 0;
			}));
		});
	}

	public static double getEffectiveEntityY(Entity entity) {
		if (!entity.hasVehicle()) {
			return CollisionUtil.ground(entity);
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

		return state.getCollisionShape(world, pos, ShapeContext.of(entity)).getMax(Direction.Axis.Y);
	}

	private static boolean isAir(BlockState state, BlockPos pos, Entity entity) {
		return state.isAir() || state.getCollisionShape(entity.getWorld(), pos, ShapeContext.of(entity)).isEmpty();
	}

	public static ActionResult sit(@NotNull final World world, @NotNull final BlockState state,
			@NotNull final BlockPos pos, @NotNull final Entity entity, final double topHeight, final boolean command) {
		final double minY = pos.getY() + topHeight;

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
			return sit(world, entity, x, y, z, minY);
		}

		if (state.getBlock() instanceof SlabBlock && state.get(SlabBlock.TYPE) == SlabType.BOTTOM) {
			double x = pos.getX() + HORIZONTAL_CENTER_OFFSET;
			double y = pos.getY() + VERTICAL_SLAB_OFFSET;
			double z = pos.getZ() + HORIZONTAL_CENTER_OFFSET;
			return sit(world, entity, x, y, z, minY);
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
			double y = pos.getY() + getTopHeight(world, state, pos, entity) + VERTICAL_SOLID_OFFSET;
			double z = pos.getZ() + HORIZONTAL_CENTER_OFFSET;
			return sit(world, entity, x, y, z, minY);
		}

		if (command && (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof FenceGateBlock)) {
			double x = pos.getX() + HORIZONTAL_CENTER_OFFSET;
			double y = pos.getY() + VERTICAL_FENCE_OFFSET;
			double z = pos.getZ() + HORIZONTAL_CENTER_OFFSET;
			return sit(world, entity, x, y, z, minY);
		}

		return ActionResult.PASS;
	}

	public static ActionResult sit(World world, Entity entity, double seatX, double seatY, double seatZ, double minY) {
		var seat = new SeatEntity(world, seatX, seatY, seatZ);

		if (seat.isDiscardable() || !CollisionUtil.isClear(entity, seat, minY)) {
			seat.discard();
			return ActionResult.PASS;
		}

		if (!world.spawnEntity(seat)) {
			seat.discard();
			return ActionResult.FAIL;
		}

		entity.startRiding(seat);

		return ActionResult.SUCCESS;
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

	public static BlockPos blockPosOfFloored(Vec3d vec3d) {
		return blockPosOfFloored(vec3d.x, vec3d.y, vec3d.z);
	}
}
