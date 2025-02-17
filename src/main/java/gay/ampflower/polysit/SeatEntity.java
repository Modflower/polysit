/* Copyright 2022 Ampflower
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package gay.ampflower.polysit;// Created 2022-08-05T21:27:35

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static net.minecraft.entity.decoration.ArmorStandEntity.ARMOR_STAND_FLAGS;

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
	private static final EntityAttributeInstance MAX_HEALTH_NULL = new EntityAttributeInstance(
			EntityAttributes.GENERIC_MAX_HEALTH, discard -> {
			});
	private static final Collection<EntityAttributeInstance> MAX_HEALTH_NULL_SINGLE = Collections
			.singleton(MAX_HEALTH_NULL);

	static {
		MAX_HEALTH_NULL.setBaseValue(0D);
	}

	/** Initialises the seat to be invisible and to have no gravity. */
	public SeatEntity(EntityType<? extends SeatEntity> type, World world) {
		super(type, world);
		this.setInvisible(true);
		this.setNoGravity(true);
	}

	public SeatEntity(World world, double x, double y, double z) {
		this(Main.SEAT, world);
		this.setPosition(x, y, z);
		this.resetPosition();
	}

	/**
	 * We're an armor stand now. Used to set an immovable & invisible 0x0x0 entity
	 * at a block.
	 */
	@Override
	public EntityType<?> getPolymerEntityType(ServerPlayerEntity player) {
		return EntityType.ARMOR_STAND;
	}

	/**
	 * Tells the client that we're a marker armor stand, and that we have no health.
	 */
	@Override
	public void modifyRawTrackedData(List<DataTracker.SerializedEntry<?>> data, ServerPlayerEntity player,
			boolean initial) {
		data.add(new DataTracker.Entry<>(ARMOR_STAND_FLAGS, (byte) 16).toSerialized());
		// This must be manually sent as there's no other mechanism we can use to send
		// this.
		if (player != null) {
			// Really, this shouldn't be null but apparently Polymer 0.3.13+1.19.3 is
			// slightly busted in that joining a world while sitting on a seat causes an
			// instant crash.
			// We can at least mitigate it here.
			player.networkHandler.sendPacket(new EntityAttributesS2CPacket(getId(), MAX_HEALTH_NULL_SINGLE));
		}
	}

	@Override
	protected void initDataTracker() {
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		// Avoids setting position on entity init
		final var version = nbt.getInt(Main.VERSION_TAG_NAME);
		if (version != Main.RUNTIME_VERSION) {
			this.setPos(this.getX(), this.getY() + Main.delta(version), this.getZ());
			// Required to suppress the packet
			this.resetPosition();
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt(Main.VERSION_TAG_NAME, Main.RUNTIME_VERSION);
	}

	/** Only save if being ridden. */
	@Override
	public boolean shouldSave() {
		var reason = getRemovalReason();
		if (reason != null && !reason.shouldSave()) {
			return false;
		}
		return hasPassengers();
	}

	/** Discard self when passengers are dismounted. */
	@Override
	public void removeAllPassengers() {
		super.removeAllPassengers();
		discard();
	}

	/** Discard self when the passenger is dismounted. */
	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		discard();
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
		setYaw(passenger.getYaw());
	}

	@Override
	public boolean damage(final DamageSource source, final float amount) {
		if (isInvulnerableTo(source)) {
			return false;
		}

		this.remove(RemovalReason.KILLED);
		return true;
	}

	@Override
	public boolean isInvulnerableTo(final DamageSource source) {
		return !source.isIn(DamageTypeTags.IS_EXPLOSION) && super.isInvulnerableTo(source);
	}

	private boolean isDiscardable() {
		final var world = this.getWorld();
		if (((world.getTime() + hashCode()) & 31) != 0) {
			return false;
		}

		return world.getBlockState(getAdjustedPos()).isAir();
	}

	private BlockPos getAdjustedPos() {
		return Main.blockPosOfFloored(getPos().add(0, Main.VERTICAL_CHECK_OFFSET, 0));
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}
}
