package net.ragnar.ragnarstwilightdimension.client.render;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.entity.BloodSteveEntity;

/**
 * Ordinary player model, ordinary size, default skin, ordinary shadow. Nothing here is dressed up -
 * what is running at you out of the red is exactly what it appears to be.
 */
public class BloodSteveRenderer extends MobEntityRenderer<BloodSteveEntity, PlayerEntityModel<BloodSteveEntity>> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

	public BloodSteveRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5F);
	}

	@Override
	public Identifier getTexture(BloodSteveEntity entity) {
		return TEXTURE;
	}
}
