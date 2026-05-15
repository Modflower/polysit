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
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import static net.minecraft.commands.Commands.literal;

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
		final var currentVersion = SharedConstants.WORLD_VERSION;

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
		EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0, 0)
			.clientTrackingRange(10).noSummon().fireImmune()
	);

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
			if (!world.isClientSide() && hand == InteractionHand.MAIN_HAND
				&& (player.onGround() || player.isPassenger() || player.isCreative())
				&& player.getItemInHand(hand).isEmpty() && hitResult.getDirection() != Direction.DOWN
			) {
				var pos = hitResult.getBlockPos();

				if (hitResult.distanceTo(player) > 5 * 5) {
					return InteractionResult.PASS;
				}

				final var block = world.getBlockState(pos);
				final var topHeight = getTopHeight(world, block, pos, player);
				final var relative = pos.getY() + topHeight - getEffectiveEntityY(player);

				if (relative > JumpHeightUtil.maxJumpHeight(player)) {
					return InteractionResult.PASS;
				}

				return sit(world, block, pos, player, topHeight, false);
			}
			return InteractionResult.PASS;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("sit").executes(context -> {
				var source = context.getSource();
				var entity = source.getEntityOrException();

				if (entity.isPassenger()) {
					entity.stopRiding();
					return Command.SINGLE_SUCCESS;
				}

				BlockPos pos;
				var world = entity.level();
				var ground = CollisionUtil.ground(entity);

				if (entity.getY() - ground > 1 || entity.fallDistance > 0.15F) {
					source.sendFailure(Component.nullToEmpty("It appears you're trying to sit on air."));
					return 0;
				}

				// Check if floored Y == ground, and move down if yes.
				if (ground % 1 == 0 || isAir(entity.getInBlockState(), entity.blockPosition(), entity)) {
					pos = blockPosOfFloored(entity.getX(), ground - 1, entity.getZ());
				} else {
					pos = blockPosOfFloored(entity.getX(), ground, entity.getZ());
				}
				var state = world.getBlockState(pos);
				var topHeight = getTopHeight(world, state, pos, entity);

				// Skip if it's not solid or taller than jump height.
				if (topHeight < 0.D) {
					source.sendFailure(Component.nullToEmpty("It appears you're trying to sit on air."));
					return 0;
				}

				if (sit(world, state, pos, entity, topHeight, true).consumesAction()) {
					return Command.SINGLE_SUCCESS;
				}

				double x = entity.getX();
				double y = ground + VERTICAL_SOLID_OFFSET;
				double z = entity.getZ();

				if (sit(world, entity, x, y, z, ground).consumesAction()) {
					return Command.SINGLE_SUCCESS;
				}

				source.sendFailure(Component.nullToEmpty("You can't sit here, your seat is obstructed."));

				return 0;
			}));
		});
	}

	public static double getEffectiveEntityY(Entity entity) {
		if (!entity.isPassenger()) {
			return CollisionUtil.ground(entity);
		}

		final var pos = entity.blockPosition();
		final var world = entity.level();
		final var block = world.getBlockState(pos);
		final var height = getTopHeight(world, block, pos, entity);

		return Math.max(pos.getY() + height, entity.getY());
	}

	public static double getTopHeight(BlockGetter world, BlockState state, BlockPos pos, Entity entity) {
		if (state.isAir()) {
			return -1.D;
		}

		return state.getCollisionShape(world, pos, CollisionContext.of(entity)).max(Direction.Axis.Y);
	}

	private static boolean isAir(BlockState state, BlockPos pos, Entity entity) {
		return state.isAir() || state.getCollisionShape(entity.level(), pos, CollisionContext.of(entity))
			.isEmpty();
	}

	private static boolean maySleep(final Entity entity, final BlockPos pos) {
		if (entity instanceof ServerPlayer player) {
			final var result = EntitySleepEvents.ALLOW_SLEEPING.invoker().allowSleep(player, pos);
			if (result != null) {
				return false;
			}
		}

		return !entity.level().isBrightOutside();
	}

	public static InteractionResult sit(
		@NotNull final Level world, @NotNull final BlockState state,
			@NotNull final BlockPos pos, @NotNull final Entity entity, final double topHeight, final boolean command) {
		final double minY = pos.getY() + topHeight;

		if (state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM) {
			var direction = state.getValue(StairBlock.FACING).getOpposite();
			// Note: Outer vs. Inner for the same side will require the same offset.
			var corner = switch (state.getValue(StairBlock.SHAPE)) {
				case INNER_LEFT, OUTER_LEFT -> direction.getCounterClockWise().getUnitVec3i();
				case INNER_RIGHT, OUTER_RIGHT -> direction.getClockWise().getUnitVec3i();
				default -> Vec3i.ZERO;
			};

			double x = pos.getX() + HORIZONTAL_CENTER_OFFSET + ((direction.getStepX() + corner.getX()) * .2D);
			double y = pos.getY() + VERTICAL_SLAB_OFFSET;
			double z = pos.getZ() + HORIZONTAL_CENTER_OFFSET + ((direction.getStepZ() + corner.getZ()) * .2D);
			return sit(world, entity, x, y, z, minY);
		}

		if (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
			double x = pos.getX() + HORIZONTAL_CENTER_OFFSET;
			double y = pos.getY() + VERTICAL_SLAB_OFFSET;
			double z = pos.getZ() + HORIZONTAL_CENTER_OFFSET;
			return sit(world, entity, x, y, z, minY);
		}

		if (state.getBlock() instanceof BedBlock && !maySleep(entity, pos)) {
			if (!command && entity instanceof ServerPlayer player) {
				// Let the bed explode as it should normally.
				final var rule = world.environmentAttributes()
					.getValue(EnvironmentAttributes.BED_RULE, pos);
				if (rule.explodes()) {
					return InteractionResult.PASS;
				}

				BlockPos head;

				// Get the head of the bed block to mimic vanilla.
				if (state.getValue(BedBlock.PART) == BedPart.HEAD) {
					head = pos;
				} else {
					head = pos.relative(state.getValue(BedBlock.FACING));
					if (!world.getBlockState(head).is(state.getBlock())) {
						head = null;
					}
				}

				// Set the spawn point for the player as one would expect.
				if (head != null && EntitySleepEvents.ALLOW_SETTING_SPAWN.invoker().allowSettingSpawn(player, head)) {
					final var spawnPoint = LevelData.RespawnData.of(
						world.dimension(),
						head,
						player.getYRot(),
						player.getXRot()
					);

					player.setRespawnPosition(new ServerPlayer.RespawnConfig(spawnPoint, false), true);
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

		return InteractionResult.PASS;
	}

	public static InteractionResult sit(
		Level world,
		Entity entity,
		double seatX,
		double seatY,
		double seatZ,
		double minY
	) {
		var seat = new SeatEntity(world, seatX, seatY, seatZ);

		if (seat.isDiscardable() || !CollisionUtil.isClear(entity, seat, minY)) {
			seat.discard();
			return InteractionResult.PASS;
		}

		if (!world.addFreshEntity(seat)) {
			seat.discard();
			return InteractionResult.FAIL;
		}

		entity.startRiding(seat);

		return InteractionResult.SUCCESS;
	}

	public static <T extends Entity> EntityType<T> registerEntity(String id, EntityType.Builder<T> type) {
		final var identifier = Identifier.parse(id);
		final var built = type.build(ResourceKey.create(Registries.ENTITY_TYPE, identifier));
		Registry.register(BuiltInRegistries.ENTITY_TYPE, identifier, built);
		PolymerEntityUtils.registerType(built);
		return built;
	}

	public static BlockPos blockPosOfFloored(double x, double y, double z) {
		return new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
	}

	public static BlockPos blockPosOfFloored(Vec3 vec3d) {
		return blockPosOfFloored(vec3d.x, vec3d.y, vec3d.z);
	}

	static Identifier id(String value) {
		return Identifier.fromNamespaceAndPath("polysit", value);
	}
}
