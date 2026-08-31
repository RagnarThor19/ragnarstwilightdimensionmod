package net.ragnar.ragnarstwilightdimension.client.render;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;
import net.ragnar.ragnarstwilightdimension.entity.TheEntity;

/**
 * Draws The Entity, which is drawn exactly like the blank figure because it is the blank figure.
 *
 * <p>The same texture, the same model, the same switched-off outer layer, and no growth spurt for
 * being a boss. That is the whole idea of it: the thing standing in the middle of the disc is the
 * same shape as the one that has been hanging over players in the twilight since the beginning, at
 * the same size, and the only difference is that this one is going to move.
 *
 * <p>It keeps a shadow, unlike {@link PaleFigureRenderer}. That one has none because a patch of shade
 * arriving on the ground would give away something that is meant to be noticed late; this one is
 * announced by a boss bar and thirty seconds of music, and for the fifteen seconds it is walking
 * around on the snow the shadow is worth having - it is the only thing on a flat white floor that
 * says where it is about to be.
 */
public class TheEntityRenderer extends MobEntityRenderer<TheEntity, PlayerEntityModel<TheEntity>> {
	private static final Identifier TEXTURE =
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "textures/entity/pale_figure.png");

	public TheEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5F);

		// On a skin that is one flat colour the outer layer adds nothing but a half-pixel of slop around
		// every edge, and the edge is the only thing this entity has.
		this.model.hat.visible = false;
		this.model.jacket.visible = false;
		this.model.leftSleeve.visible = false;
		this.model.rightSleeve.visible = false;
		this.model.leftPants.visible = false;
		this.model.rightPants.visible = false;
	}

	@Override
	public Identifier getTexture(TheEntity entity) {
		return TEXTURE;
	}
}
