/* Copyright 2022 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit;// Created 2022-08-05T21:27:35

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS;

/**
 * The ephemeral seat entity used to allow the player to have a sit pose
 * regardless of location.
 * <p>
 * <h3>Features</h3>
 * <ul>
 * <li>Emulates an armor stand when Polymer's present.</li>
 * <li>Follows the player's yaw to allow for legs to face forward.</li>
 * <li>Destroys itself whenever there's no passengers or block.</li>
 * <li>Fakes the entity data to claim that there's no health.</li>
 * <li>If an instance ever makes it to save the cycle, it only saves when
 * actively ridden.</li>
 * </ul>
 *
 * @author Ampflower
 * @since 0.0.0
 **/
public class SeatEntity extends Entity implements PolymerEntity {
	private static final AttributeInstance MAX_HEALTH_NULL = new AttributeInstance(
		Attributes.MAX_HEALTH, discard -> {
			});
	private static final Collection<AttributeInstance> MAX_HEALTH_NULL_SINGLE = Collections
			.singleton(MAX_HEALTH_NULL);

	static {
		MAX_HEALTH_NULL.setBaseValue(0D);
	}

	/** Initialises the seat to be invisible and to have no gravity. */
	public SeatEntity(EntityType<? extends SeatEntity> type, Level world) {
		super(type, world);
		this.setInvisible(true);
		this.setNoGravity(true);
	}

	public SeatEntity(Level world, double x, double y, double z) {
		this(Main.SEAT, world);
		this.setPos(x, y, z);
		this.setOldPosAndRot();
	}

	/**
	 * We're an armor stand now. Used to set an immovable & invisible 0x0x0 entity
	 * at a block.
	 */
	@Override
	public EntityType<?> getPolymerEntityType(final PacketContext context) {
		return EntityType.ARMOR_STAND;
	}

	/**
	 * Tells the client that we're a marker armor stand, and that we have no health.
	 */
	@Override
	public void modifyRawTrackedData(
		List<SynchedEntityData.DataValue<?>> data, ServerPlayer player,
			boolean initial) {
		data.add(new SynchedEntityData.DataItem<>(DATA_CLIENT_FLAGS, (byte) 16).value());
		// This must be manually sent as there's no other mechanism we can use to send
		// this.
		if (player != null) {
			// Really, this shouldn't be null but apparently Polymer 0.3.13+1.19.3 is
			// slightly busted in that joining a world while sitting on a seat causes an
			// instant crash.
			// We can at least mitigate it here.
			player.connection.send(new ClientboundUpdateAttributesPacket(getId(), MAX_HEALTH_NULL_SINGLE));
		}
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput nbt) {
		// Avoids setting position on entity init
		final var version = nbt.getIntOr(Main.VERSION_TAG_NAME, 0);
		if (version != Main.RUNTIME_VERSION) {
			this.setPosRaw(this.getX(), this.getY() + Main.delta(version), this.getZ());
			// Required to suppress the packet
			this.setOldPosAndRot();
		}
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput nbt) {
		nbt.putInt(Main.VERSION_TAG_NAME, Main.RUNTIME_VERSION);
	}

	/** Only save if being ridden. */
	@Override
	public boolean shouldBeSaved() {
		var reason = getRemovalReason();
		if (reason != null && !reason.shouldSave()) {
			return false;
		}
		return isVehicle();
	}

	/**
	 * Automatic cleanup and syncing yaw with the passenger.
	 */
	@Override
	public void tick() {
		// There's absolutely no reason for this entity to even move.
		super.tick();
		var passenger = getFirstPassenger();
		if (passenger == null || isDiscardable()) {
			discard();
			return;
		}
		setYRot(passenger.getYRot());
	}

	@Override
	public boolean hurtServer(final ServerLevel world, final DamageSource source, final float amount) {
		if (!source.isCreativePlayer() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}

		this.remove(RemovalReason.KILLED);
		return true;
	}

	@Override
	public void onExplosionHit(@Nullable final Entity entity) {
		// Allows the seat to be destroyed by TNT.
		this.remove(RemovalReason.KILLED);
	}

	protected boolean isDiscardable() {
		return this.level().getBlockState(getAdjustedPos()).isAir();
	}

	private BlockPos getAdjustedPos() {
		return Main.blockPosOfFloored(position().add(0, Main.VERTICAL_CHECK_OFFSET, 0));
	}
}
