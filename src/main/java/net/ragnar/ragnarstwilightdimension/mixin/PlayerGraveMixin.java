package net.ragnar.ragnarstwilightdimension.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.ragnar.ragnarstwilightdimension.event.PlayerGrave;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends a player's belongings to a grave instead of the floor, in the twilight only.
 *
 * <p>{@code dropInventory} is the hook rather than either of the death events, because it is the one
 * point that is both late enough and early enough. A totem of undying resolves before it, so nothing
 * here can bury somebody who then goes on living; the inventory is still full when it is entered, so
 * there is nothing to catch and put back. It is also where vanilla's own keep-inventory check lives,
 * which is the check {@link PlayerGrave} has to make the same decision on.
 *
 * <p>Cancelling it skips {@code LivingEntity.dropInventory} as well, which for a player is empty.
 * Experience is not part of this and still drops where they died.
 */
@Mixin(PlayerEntity.class)
public class PlayerGraveMixin {
	@Inject(method = "dropInventory", at = @At("HEAD"), cancellable = true)
	private void twilight$buryInstead(CallbackInfo ci) {
		// Only ever true server-side, so the client's copy of the player is left alone.
		if ((Object) this instanceof ServerPlayerEntity player && PlayerGrave.bury(player)) {
			ci.cancel();
		}
	}
}
