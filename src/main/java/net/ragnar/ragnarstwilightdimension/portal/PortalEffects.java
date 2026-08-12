package net.ragnar.ragnarstwilightdimension.portal;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Particles and sound for the ruin portals.
 *
 * <p>Everything here is deliberately built out of vanilla sounds pitched down rather than custom
 * audio. Note that the client clamps pitch to the range 0.5 - 2.0, so 0.5 is as low as a sound can
 * go; anything lower is silently treated as 0.5.
 */
public final class PortalEffects {
	// --- tweak these to taste -------------------------------------------------
	private static final float SHATTER_VOLUME = 0.55F;
	private static final float DRONE_VOLUME = 0.45F;
	private static final float CHIME_VOLUME = 0.35F;

	private static final float LOWEST_PITCH = 0.5F;   // client-side floor
	private static final float SHATTER_PITCH = 0.6F;
	private static final float CHIME_PITCH = 0.55F;
	// --------------------------------------------------------------------------

	private PortalEffects() {
	}

	/** The eye shattering and the portal swallowing you, played at the frame you used. */
	public static void departure(ServerWorld world, BlockPos frame) {
		Vec3d c = Vec3d.ofCenter(frame, 1.0);

		world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.ENDER_EYE)),
				c.x, c.y, c.z, 24, 0.22, 0.12, 0.22, 0.06);
		world.spawnParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 18, 0.28, 0.35, 0.28, 0.05);
		world.spawnParticles(ParticleTypes.WHITE_ASH, c.x, c.y + 0.4, c.z, 12, 0.5, 0.3, 0.5, 0.005);

		// dry crack of the eye giving out, then a long hollow drop
		world.playSound(null, frame, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS,
				SHATTER_VOLUME, LOWEST_PITCH);
		world.playSound(null, frame, SoundEvents.ENTITY_ENDER_EYE_DEATH, SoundCategory.BLOCKS,
				0.7F, SHATTER_PITCH);
		world.playSound(null, frame, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS,
				DRONE_VOLUME, LOWEST_PITCH);
	}

	/** The other side settling around you. */
	public static void arrival(ServerWorld world, Vec3d pos) {
		BlockPos at = BlockPos.ofFloored(pos);

		world.spawnParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1.0, pos.z, 30, 0.3, 0.5, 0.3, 0.08);
		world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z, 6, 0.3, 0.3, 0.3, 0.01);
		world.spawnParticles(ParticleTypes.ASH, pos.x, pos.y + 1.2, pos.z, 14, 0.8, 0.5, 0.8, 0.002);

		world.playSound(null, at, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS,
				DRONE_VOLUME, LOWEST_PITCH);
		world.playSound(null, at, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS,
				CHIME_VOLUME, CHIME_PITCH);
	}
}
