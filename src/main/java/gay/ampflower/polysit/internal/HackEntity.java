/* Copyright 2025 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit.internal;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.ApiStatus;

/**
 * The code equivalent of a bodge wire.
 *
 * @author Ampflower
 * @since 0.8.5
 **/
@ApiStatus.Internal
public interface HackEntity {
	/** Delegates to player's handling. No-op */
	default void polysit$requestTeleportOnDismount(Vec3d position, Vec3d velocity) {
	}
}
