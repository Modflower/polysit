/* Copyright 2024 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit;

import com.mojang.logging.LogUtils;
import gay.ampflower.polysit.mixin.AccessorEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Checks the collision of the player in question
 *
 * @author Ampflower
 * @since 0.6.0
 **/
public final class CollisionUtil {
	private static final Logger logger = LogUtils.getLogger();

	public static FittingPosition adjustFit(final Entity entity, double x, double y, double z) {
		// The entity in question shouldn't be able to be placed beyond their jump
		// height.
		double max = y + JumpHeightUtil.maxJumpHeight(entity) + entity.getHeight();
		double min = y;

		Box box = getSmallestPose(entity, x, y, z);
		Iterator<Box> itr = collisionBoxStream(entity, box.withMaxY(max)).iterator();

		while (itr.hasNext()) {
			final Box bound = itr.next();
			if (!bound.intersects(box)) {
				max = Math.min(max, bound.minY);
				continue;
			}
			box = box.offset(0, bound.maxY - min, 0);
			min = bound.maxY;
		}

		return new FittingPosition(min, getLargestFittingPose(entity, max - min));
	}

	public static Box getSmallestPose(Entity entity, double x, double y, double z) {
		return getSmallestPose(entity).getBoxAt(x, y, z);
	}

	public static EntityDimensions getSmallestPose(Entity entity) {
		final var standing = entity.getDimensions(entity.getPose());
		final var sneaking = entity.getDimensions(EntityPose.CROUCHING);
		final var swimming = entity.getDimensions(EntityPose.SWIMMING);

		return smallest(standing, sneaking, swimming);
	}

	public static EntityPose getLargestFittingPose(Entity entity, double y) {
		return largest(entity, y, entity.getPose(), EntityPose.STANDING, EntityPose.CROUCHING, EntityPose.SWIMMING);
	}

	@NotNull
	public static EntityDimensions smallest(EntityDimensions... dimensions) {
		if (dimensions.length == 0) {
			throw new IllegalArgumentException("length == 0");
		}

		EntityDimensions min = dimensions[0];

		for (int i = 1; i < dimensions.length; i++) {
			final var comparison = dimensions[i];
			if (comparison.height < min.height && comparison.width <= min.width) {
				min = comparison;
			}
		}

		return min;
	}

	@Nullable
	private static EntityPose largest(final Entity entity, final double maxHeight, EntityPose... poses) {
		if (poses.length == 0) {
			throw new IllegalArgumentException("poses.length == 0");
		}

		double height = 0;
		EntityPose fittingPose = null;

		for (final var pose : poses) {
			final var poseHeight = entity.getDimensions(pose).height;
			if (poseHeight < maxHeight && poseHeight > height) {
				height = poseHeight;
				fittingPose = pose;
			}
		}

		return fittingPose;
	}

	public static double ground(final Entity entity) {
		final double maxY = entity.getY();
		final double minY = maxY - JumpHeightUtil.maxJumpHeight(entity);

		final var box = box(entity.getX(), minY, entity.getZ(), entity.getWidth(), maxY);

		return collisionBoxStream(entity, box).mapToDouble(b -> b.maxY).max().orElse(Double.NEGATIVE_INFINITY);
	}

	public static boolean isClear(final Entity entity, final Entity seat, final double minY) {
		final double maxY = getEffectiveSittingHeight(entity, seat);
		final var box = box(seat.getX(), minY, seat.getZ(), entity.getWidth(), seat.getY() + maxY);

		return !collisions(entity, box).iterator().hasNext();
	}

	public static Box box(double x, double y, double z, double w, double my) {
		w /= 2;
		return new Box(x - w, y, z - w, x + w, my, z + w);
	}

	private static double getEffectiveSittingHeight(final Entity entity, final Entity seat) {
		// Funny catch: The entity already needs to be in sitting/standing pose for
		// getVehicleAttachmentPos to behave correctly.
		final var prevPose = entity.getPose();
		final var prevVehicle = entity.getVehicle();
		final var accessor = (AccessorEntity) entity;
		entity.setPose(EntityPose.SITTING);
		// Pehkui workaround - requires a vehicle to get the correct offset.
		// As the seat in question is the vehicle, it'll always be correct.
		accessor.setVehicle(seat);
		final double height = entity.getHeight() + entity.getHeightOffset();
		accessor.setVehicle(prevVehicle);
		entity.setPose(prevPose);

		return height;
	}

	private static Stream<Box> collisionBoxStream(Entity entity, Box box) {
		return collisionStream(entity, box).flatMap(voxel -> voxel.getBoundingBoxes().stream()).filter(box::intersects);
	}

	private static Stream<VoxelShape> collisionStream(Entity entity, Box box) {
		return StreamSupport.stream(entity.getWorld().getBlockCollisions(entity, box).spliterator(), false);
	}

	private static Iterable<VoxelShape> collisions(Entity entity, Box box) {
		return entity.getWorld().getBlockCollisions(entity, box);
	}

	public record FittingPosition(double y, EntityPose pose) {
	}
}
