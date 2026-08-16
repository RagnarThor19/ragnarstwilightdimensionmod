package net.ragnar.ragnarstwilightdimension.client.render;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.RagnarsTwilightDimension;
import net.ragnar.ragnarstwilightdimension.entity.PaleFigureEntity;

/**
 * Draws the blank figure: the ordinary wide player model, in a texture that is nothing but white.
 *
 * <p>The outer layer - hat, jacket, sleeves, trouser legs - is switched off. On a skin that is one
 * flat colour those parts add nothing but a half-pixel of slop around every edge, and the edge is the
 * only thing this entity has. What is left is the exact player silhouette with no detail inside it.
 *
 * <p>No shadow, for the same reason the silhouette has none: it hangs thirty blocks up, and a patch
 * of shade arriving on the ground underneath the player would announce it before they looked.
 */
public class PaleFigureRenderer extends MobEntityRenderer<PaleFigureEntity, PlayerEntityModel<PaleFigureEntity>> {
	private static final Identifier TEXTURE =
			Identifier.of(RagnarsTwilightDimension.MOD_ID, "textures/entity/pale_figure.png");

	public PaleFigureRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.0F);

		this.model.hat.visible = false;
		this.model.jacket.visible = false;
		this.model.leftSleeve.visible = false;
		this.model.rightSleeve.visible = false;
		this.model.leftPants.visible = false;
		this.model.rightPants.visible = false;
	}

	@Override
	public Identifier getTexture(PaleFigureEntity entity) {
		return TEXTURE;
	}
}
