package net.ragnar.ragnarstwilightdimension.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Which block a block display is showing. Private in vanilla, for the same reason as everything in
 * {@link DisplayEntityAccessor}: the only intended driver of one of these is a command.
 */
@Mixin(DisplayEntity.BlockDisplayEntity.class)
public interface BlockDisplayAccessor {
	@Invoker("setBlockState")
	void callSetBlockState(BlockState state);
}
