package net.ragnar.ragnarstwilightdimension.client.render;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;
import net.ragnar.ragnarstwilightdimension.entity.WitnessEntity;

/**
 * The player model with one thing added: an arm that is up.
 *
 * <p>Vanilla has poses for holding a bow and for holding a crossbow and for nothing else, so the
 * pointing has to be written here. It is the only reason this class exists - everything below the
 * shoulder is vanilla's, animated by vanilla, and the whole read of the entity is those three lines
 * of arm angle.
 *
 * <p>The jab comes off {@code handSwingProgress}, which vanilla is already tracking and syncing
 * because the entity swings its hand like anything else does. Riding on it rather than keeping a
 * timer means the punch is exactly as long as a punch and lands on the same tick the server thinks
 * it does.
 */
public class WitnessModel extends PlayerEntityModel<WitnessEntity> {
	/** Arm angle when it is pointing upwards. Radians, and -PI would be straight up. */
	private static final float SKY_PITCH = -2.55F;

	/** Arm angle when it has given up on the sky and is pointing at the player. */
	private static final float PLAYER_PITCH = -1.62F;

	/** How far the arm travels on a jab. Short - it is not throwing a punch, it is insisting. */
	private static final float JAB = 0.42F;

	public WitnessModel(ModelPart root, boolean slim) {
		super(root, slim);
	}

	@Override
	public void setAngles(WitnessEntity entity, float limbAngle, float limbDistance, float animationProgress,
						  float headYaw, float headPitch) {
		super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

		WitnessEntity.ArmPose pose = entity.getArmPose();
		if (pose == WitnessEntity.ArmPose.LOWERED) {
			return;
		}

		// sin over the swing gives a single out-and-back, which is the jab. It is added rather than
		// assigned so the arm never leaves the position it is holding.
		float jab = MathHelper.sin(entity.handSwingProgress * MathHelper.PI) * JAB;

		this.rightArm.pitch = (pose == WitnessEntity.ArmPose.SKY ? SKY_PITCH : PLAYER_PITCH) + jab;
		this.rightArm.yaw = 0.0F;
		this.rightArm.roll = 0.0F;

		// The left one is left hanging on purpose. Both arms up is a gesture; one arm up is a person
		// who has seen something.
		this.leftArm.pitch = 0.0F;
		this.leftArm.yaw = 0.0F;
		this.leftArm.roll = 0.0F;

		this.rightSleeve.copyTransform(this.rightArm);
		this.leftSleeve.copyTransform(this.leftArm);
	}
}
