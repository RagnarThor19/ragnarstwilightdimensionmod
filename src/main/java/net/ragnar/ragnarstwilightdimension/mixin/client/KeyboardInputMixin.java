package net.ragnar.ragnarstwilightdimension.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.ragnarstwilightdimension.client.StareClient;
import net.ragnar.ragnarstwilightdimension.client.WitnessClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The two places in the mod where the keyboard stops being the player's.
 *
 * <p>Injected after vanilla has read the keys rather than in front of it, so the keys themselves are
 * still tracked - what is thrown away is only this tick's worth of intent. Releasing and re-pressing
 * a key during either of these behaves exactly as it would have; it just does not go anywhere.
 *
 * <p>The stare takes everything for three seconds. The witness takes only the legs, for the four
 * seconds it stands there pointing, and walks them at itself - see {@link #twilight$walkThem}. Both
 * leave gravity alone: somebody caught mid-fall keeps falling, because what is being taken away is
 * the player's control and not the world's.
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
	/**
	 * How fast the witness's pull walks somebody, as a share of an ordinary walk.
	 *
	 * <p>Deliberately a crawl. Being marched at full speed reads as the game moving the player, which
	 * is a cutscene; being drawn in at a fifth of a walk reads as being unable to stop, which is a
	 * different feeling entirely and the one worth having. It also means the four seconds only close
	 * a few blocks, so a player who was already backing off does not arrive in its reach at all.
	 *
	 * <p>Under vanilla's 0.8 sprint threshold, so nothing the player holds can turn the crawl into a
	 * run.
	 */
	private static final float PULL_SPEED = 0.2F;

	@Inject(method = "tick", at = @At("TAIL"))
	private void twilight$dropInput(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
		KeyboardInput input = (KeyboardInput) (Object) this;

		if (StareClient.isLocked()) {
			input.movementForward = 0.0F;
			input.movementSideways = 0.0F;
			input.jumping = false;
			input.sneaking = false;
			return;
		}

		if (WitnessClient.isPulling()) {
			twilight$walkThem(input);
		}
	}

	/**
	 * Points the player's legs at the witness, whatever they are pressing.
	 *
	 * <p>The camera is deliberately untouched. Movement input is given in the player's own frame -
	 * forwards and sideways relative to wherever they happen to be facing - so walking them towards a
	 * fixed point in the world while they are free to spin on the spot means resolving that world
	 * direction into their frame every tick. The two axes below are the player's forward vector
	 * {@code (-sin yaw, cos yaw)} and their left vector {@code (cos yaw, sin yaw)}, and the input is
	 * simply the direction of travel projected onto each.
	 *
	 * <p>Written this way rather than by overwriting the velocity because everything the player is
	 * standing in still gets a say: ice is still slippery, water still slows them, a wall still stops
	 * them. They are walking. They are only not choosing where.
	 */
	private void twilight$walkThem(KeyboardInput input) {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) {
			return;
		}

		Vec3d target = WitnessClient.pullTarget();
		double dx = target.x - player.getX();
		double dz = target.z - player.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);

		// Already there. Anything below this and the direction is noise, and a player standing inside
		// the thing would be shoved back and forth by the rounding.
		if (length < 0.35) {
			input.movementForward = 0.0F;
			input.movementSideways = 0.0F;
			return;
		}

		dx /= length;
		dz /= length;

		float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		double sin = MathHelper.sin(yaw);
		double cos = MathHelper.cos(yaw);

		input.movementForward = (float) (dz * cos - dx * sin) * PULL_SPEED;
		input.movementSideways = (float) (dx * cos + dz * sin) * PULL_SPEED;

		// No jumping out of it, and no sneaking to the edge of a drop to be safe from the shove that
		// ends it. Walking is the only thing left.
		input.jumping = false;
		input.sneaking = false;
	}
}
