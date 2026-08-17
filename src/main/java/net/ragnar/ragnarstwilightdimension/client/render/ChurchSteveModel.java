package net.ragnar.ragnarstwilightdimension.client.render;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.ragnar.ragnarstwilightdimension.entity.ChurchSteveEntity;

/**
 * The player model, sat down.
 *
 * <p>Vanilla has exactly one seated pose and it belongs to {@code EntityModel.riding}, which the
 * renderer sets from {@code entity.hasVehicle()} every frame. Putting the figure in it that way would
 * mean giving him something to ride - a second entity under every pew, saved, ticked and killable in
 * its own right - for an animation that is six numbers. The six numbers are here instead, and he
 * rides nothing.
 *
 * <p>They are vanilla's own values, lifted from {@code BipedEntityModel.setAngles}, so what you get
 * is exactly the pose a player has in a boat: thighs forward and level, knees turned out a little,
 * arms dropped in front. The model has no knee joint, so the legs go out straight - that is a limit
 * of the player model and every seated thing in the game has it.
 */
public class ChurchSteveModel extends PlayerEntityModel<ChurchSteveEntity> {
	/** Thigh angle. About 81 degrees off vertical, which is not quite level - a slouch, not a plank. */
	private static final float LEG_PITCH = -1.4137167F;

	/** How far the knees turn out, and the matching lean on each leg that keeps the feet under them. */
	private static final float LEG_YAW = 0.31415927F;
	private static final float LEG_ROLL = 0.07853982F;

	/** Arms forward off the shoulder, so the hands come down over the lap rather than through it. */
	private static final float ARM_PITCH = -0.62831855F;

	public ChurchSteveModel(ModelPart root, boolean slim) {
		super(root, slim);
	}

	@Override
	public void setAngles(ChurchSteveEntity entity, float limbAngle, float limbDistance,
						  float animationProgress, float headYaw, float headPitch) {
		super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

		this.rightLeg.pitch = LEG_PITCH;
		this.rightLeg.yaw = LEG_YAW;
		this.rightLeg.roll = LEG_ROLL;

		this.leftLeg.pitch = LEG_PITCH;
		this.leftLeg.yaw = -LEG_YAW;
		this.leftLeg.roll = -LEG_ROLL;

		// Added rather than assigned, so the idle sway vanilla put on the arms survives. It is the only
		// movement he has: he is otherwise completely still, and a completely still player model looks
		// like a paused game rather than a person sitting quietly.
		this.rightArm.pitch += ARM_PITCH;
		this.leftArm.pitch += ARM_PITCH;

		// The overlay layer is copied from the base parts by the superclass before any of the above
		// happened, so the ones that moved have to be copied again or the second skin layer stays
		// standing up.
		this.rightPants.copyTransform(this.rightLeg);
		this.leftPants.copyTransform(this.leftLeg);
		this.rightSleeve.copyTransform(this.rightArm);
		this.leftSleeve.copyTransform(this.leftArm);
	}
}
