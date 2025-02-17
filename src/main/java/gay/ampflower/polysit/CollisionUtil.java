/* Copyright 2024 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit;

import com.mojang.logging.LogUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

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
		double max = y + JumpHeightUtil.maxJumpHeight(entity);
		double maxFit = Double.POSITIVE_INFINITY;
		double min = y;

		logger.debug("{} => [{}, {}] @ ({}, {}, {})", entity, min, max, x, y, z);

		Box box = getSmallestPose(entity, x, y, z);
		final var collisions = entity.getWorld().getBlockCollisions(entity, box);

		for (final var collision : collisions) {
			for (final var bound : collision.getBoundingBoxes()) {
				if (!bound.intersects(box)) {
					logger.debug("Skipping box {} as unimportant to {}'s position", bound, entity);
					continue;
				}
				if (bound.minY > box.minY && bound.maxY > box.maxY) {
					maxFit = Math.min(maxFit, bound.minY);
					// TODO: Verify that this would work in most cases.
					// It does seem to in limited testing.
					logger.debug("Skipping box {} as above {} for {}", bound, box, entity);
					continue;
				}
				if (bound.maxY > min && bound.maxY < max) {
					box = box.offset(0, bound.maxY - min, 0);
					min = bound.maxY;
				}
			}
		}

		return new FittingPosition(min, getLargestFittingPose(entity, maxFit - min));
	}

	public static Box getSmallestPose(Entity entity, double x, double y, double z) {
		final var standing = entity.getDimensions(entity.getPose());
		final var sneaking = entity.getDimensions(EntityPose.CROUCHING);
		final var swimming = entity.getDimensions(EntityPose.SWIMMING);

		return smallest(standing, sneaking, swimming).getBoxAt(x, y, z);
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
			if (comparison.height < min.height && comparison.width < min.width) {
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

		return StreamSupport.stream(entity.getWorld().getBlockCollisions(entity, box).spliterator(), true)
				.flatMap(shape -> shape.getBoundingBoxes().stream()).filter(box::intersects).mapToDouble(b -> b.maxY)
				.max().orElse(Double.NEGATIVE_INFINITY);
	}

	public static boolean isClear(final Entity entity, final double seatX, final double seatY, final double seatZ,
			double minY) {
		final double maxY = getEffectiveSittingHeight(entity);
		final var box = box(seatX, minY, seatZ, entity.getWidth(), seatY + maxY);

		return !entity.getWorld().getBlockCollisions(entity, box).iterator().hasNext();
	}

	public static Box box(double x, double y, double z, double w, double my) {
		w /= 2;
		return new Box(x - w, y, z - w, x + w, my, z + w);
	}

	private static float getEffectiveSittingHeight(final Entity entity) {
		final float height = entity.getDimensions(EntityPose.SITTING).height;

		final float scale = entity instanceof LivingEntity living ? living.getScaleFactor() : 1.f;

		if(height <= 1.5F) {
			return height * scale;
		}

		return (height - (float)Main.VERTICAL_SLAB_OFFSET) * scale;
	}

	public record FittingPosition(double y, EntityPose pose) {
	}
}
