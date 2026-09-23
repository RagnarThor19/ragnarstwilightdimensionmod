package net.ragnar.ragnarstwilightdimension.client.render;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.entity.StuckPlayerEntity;

/**
 * The ordinary wide player model in the default skin, like every other figure here. No shadow: it
 * is standing inside the ground, and a shadow would be drawn on top of the grass.
 */
public class StuckPlayerRenderer extends MobEntityRenderer<StuckPlayerEntity, PlayerEntityModel<StuckPlayerEntity>> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

	public StuckPlayerRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.0F);
	}

	@Override
	public Identifier getTexture(StuckPlayerEntity entity) {
		return TEXTURE;
	}
}
