/* Copyright 2023 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit;

import gay.ampflower.polysit.mixin.AccessorLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Unnecessarily accurate and fast jump height utilities.
 * <p>
 * Effectively the difference operator from {@code travel() { y += v; v = (v -
 * 0.08) * 0.98 }} eventually turned back into a curve with a well-defined
 * derivative.
 * </p>
 * <p>
 * The Maxima script for solving the equations, if it is ever needed again.
 * 
 * <pre>
 * load("solve_rec")$
 *
 * vy_expl: solve_rec(vy[t+1] = (vy[t] - b) * decay, vy[t], vy[0] = v0);
 * vy(t) := ev(rhs(vy_expl))$
 *
 * y_expl: y[t] = sum(vy(i), i, 0, t), simpsum=true;
 * y(t) := ev(rhs(y_expl))$
 *
 * &#x2f;* We have to manually factor decay^t out *&#x2f;
 * tzero: expand(solve(expand(vy(t)/decay^t) = 0, t));
 *
 * print("Call 'y(t), tzero, b=0.08, decay=0.98;' to get the expression for ymax")$
 * y(t), tzero, b=0.08, decay=0.98;
 * </pre>
 * </p>
 *
 * @author Moxie Amethyst
 * @author Ampflower
 * @since 0.5
 **/
public final class JumpHeightUtil {

	public static double maxJumpHeight(Entity entity) {
		if (entity instanceof LivingEntity livingEntity) {
			return maxJumpHeight(livingEntity);
		}
		return 1.D;
	}

	public static double maxJumpHeight(LivingEntity entity) {
		return maxJumpHeight(((AccessorLivingEntity) entity).invokeGetJumpVelocity());
	}

	/**
	 * Gets the maximum height for a jump using a given velocity.
	 *
	 * @param velocity The Y velocity to find the maximum relative Y of.
	 * @return The maximum Y of the velocity.
	 */
	public static double maxJumpHeight(double velocity) {
		return heightAtTime(velocity, zeroTangent(velocity));
	}

	/**
	 * Gets the Y position of a given entity when given velocity and ticks.
	 *
	 * @param velocity The Y velocity to define the curve with.
	 * @param ticks    The time in ticks, as the X coordinate.
	 * @return The height from the velocity after given time in ticks.
	 */
	public static double heightAtTime(double velocity, double ticks) {
		return -50 * (velocity + 3.92) * (Math.pow(0.98, ticks) - 1D) - 3.92 * ticks;
	}

	/**
	 * Gets the point where the graph is at a zero-slope.
	 *
	 * @param velocity The Y velocity to define the curve with.
	 * @return The time in ticks where the graph will have a 0-point slope, defining
	 *         the maximum of the curve.
	 */
	public static double zeroTangent(double velocity) {
		return 49.4983 * Math.log(Math.fma(0.25768759333570745, velocity, 1.010135365875973));
	}
}
