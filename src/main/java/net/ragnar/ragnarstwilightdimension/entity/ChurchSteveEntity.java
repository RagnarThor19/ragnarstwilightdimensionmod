package net.ragnar.ragnarstwilightdimension.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Somebody is still coming to church.
 *
 * <p>He is sat in a pew two rows back from the altar, on the aisle end, facing the front. He does not
 * get up when you come in, he does not look round, and he does not look at you once in the whole time
 * you are in the building. Everything else in this dimension turns to face whoever walks in - the
 * {@link SilhouetteEntity} turns its neck, the {@link PaleFigureEntity} squares up completely - and
 * this one is the only thing in the mod that has somewhere else to be looking.
 *
 * <p>He can be killed, and that is the entire interaction available. He has ordinary player health,
 * takes damage from anything, and drops nothing, because he is not carrying anything and never was.
 * He does not fight back, does not flee, and does not so much as break the stare between the first
 * hit and the last one.
 *
 * <p><b>He is placed by worldgen, not by code.</b> The figure is baked into
 * {@code data/ragnarstwilightdimension/structure/twilight_church.nbt} as a structure entity, so the
 * church arrives with him already in it and he is placed exactly once, when the chunk first
 * generates. That is deliberate rather than convenient: killing him has to be permanent, and anything
 * that spawned him on chunk load would put him back in the seat the next time you walked in.
 *
 * <p>The cost of that is that the figure lives in the template. Re-exporting the church from a
 * creative world overwrites the file and drops him, and the seat coordinates below are then the only
 * record of where he was - see {@code tools/church_steve/} for the script that puts him back.
 */
public class ChurchSteveEntity extends MobEntity {
	/**
	 * Where he sits, in the church template's own coordinates: the pew stair at (13, 1, 8), which is
	 * the south side of the aisle, two rows back from the altar. The figure itself stands at
	 * (13.75, 1.5, 8.5) - a quarter block east of centre so he is in front of the backrest rather than
	 * inside it, and half a block up, on the seat surface. Baked into the template, repeated here
	 * because the template is not readable and this is where anybody would look for it.
	 */
	public static final Vec3d SEAT = new Vec3d(13.75, 1.5, 8.5);

	/** Width of the hitbox: an ordinary player's. */
	public static final float WIDTH = 0.6F;

	/**
	 * Height of the hitbox, measured from the seat rather than from the floor. A seated player model
	 * reaches about 1.25 blocks above its hips, so this covers the head with a little to spare and
	 * nothing below it - the legs stick out forwards, outside the box, and are not part of him as far
	 * as an arrow is concerned.
	 */
	public static final float HEIGHT = 1.3F;

	/**
	 * How far his head is turned off the line of the pew, in degrees. Negative is to his left.
	 *
	 * <p>The pews run north-south and every one of them faces the altar end squarely, so a figure
	 * sitting straight is looking at the wall beside the altar rather than at the altar. The seat is
	 * two blocks off the centre line and the lectern is nearly eight blocks ahead, which puts it
	 * fourteen and a half degrees to his left; his body stays square to the bench he is sitting on and
	 * his head makes up the difference.
	 *
	 * <p>Held as an angle relative to the body rather than as a position, so it survives the structure
	 * being placed at any of the four rotations. A stored block position would not.
	 */
	private static final float HEAD_TURN = -14.5F;

	/** Set once the first tick has pinned the angles, so they are not interpolated from zero. */
	private boolean settled;

	public ChurchSteveEntity(EntityType<? extends ChurchSteveEntity> type, World world) {
		super(type, world);

		// No AI, no gravity, no noise. He is furniture that bleeds.
		this.setAiDisabled(true);
		this.setNoGravity(true);
		this.setSilent(true);

		// Nothing about him is a spawned mob, so the mob cap and the despawn rules should not get a
		// vote. checkDespawn() below is the other half of this.
		this.setPersistent();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 0.0);
	}

	/**
	 * Holds the pose.
	 *
	 * <p>Yaw is never written here - it is whatever the structure placed him at, which is the pew's
	 * own bearing under whichever rotation the church came out at, and it is saved and reloaded by
	 * vanilla like any other entity's. Body and head are derived from it every tick instead, so the
	 * turn towards the altar cannot be lost to anything that fiddles with head yaw.
	 *
	 * <p>Runs on both sides. The server decides the angles and the client is told them by the entity
	 * tracker, but a client that computed nothing would spend the first frames after the entity comes
	 * into range lerping the head round from zero, which reads as him turning to look.
	 */
	@Override
	public void tick() {
		super.tick();

		float yaw = this.getYaw();
		this.setBodyYaw(yaw);
		this.setHeadYaw(yaw + HEAD_TURN);

		if (!this.settled) {
			this.prevYaw = yaw;
			this.prevBodyYaw = yaw;
			this.prevHeadYaw = yaw + HEAD_TURN;
			this.settled = true;
		}
	}

	/**
	 * Being hit does not move him.
	 *
	 * <p>Knockback on a seated figure would slide him off the bench and leave him hanging in the
	 * aisle, since he has no gravity to bring him back down. He takes the damage and stays where he
	 * is.
	 */
	@Override
	public void takeKnockback(double strength, double x, double z) {
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void pushAwayFrom(Entity entity) {
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean canUsePortals(boolean allowVehicles) {
		return false;
	}

	/** He was put here by the building, and only a death removes him. */
	@Override
	public void checkDespawn() {
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}
}
