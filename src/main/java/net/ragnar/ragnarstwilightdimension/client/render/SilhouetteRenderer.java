package net.ragnar.ragnarstwilightdimension.client.render;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.entity.SilhouetteEntity;

/**
 * Draws the silhouette with the ordinary wide player model and the default skin, so at fog range it
 * is indistinguishable from another player standing there.
 *
 * <p>Two deliberate omissions: there is no shadow (radius 0), and it is not drawn at all inside
 * {@link #MIN_VISIBLE_DISTANCE}. Between them there is nothing to inspect up close - no shadow on
 * the grass giving away an empty patch of fog, and no model to walk up to.
 */
public class SilhouetteRenderer extends MobEntityRenderer<SilhouetteEntity, PlayerEntityModel<SilhouetteEntity>> {
	private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

	/**
	 * Closer than this and it simply is not rendered. Kept well inside the entity's flee distance of
	 * 10 so it acts purely as a backstop - if it sat near the flee distance it would cut off the
	 * departure animations halfway through and make every exit look like a disappearance.
	 */
	private static final double MIN_VISIBLE_DISTANCE = 4.0;

	public SilhouetteRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.0F);
	}

	@Override
	public Identifier getTexture(SilhouetteEntity entity) {
		return TEXTURE;
	}

	@Override
	public boolean shouldRender(SilhouetteEntity entity, Frustum frustum, double x, double y, double z) {
		// The grave watcher is the one you are meant to walk right up to. Its head only comes round at
		// two blocks, well inside the cutoff, so applying the cutoff would hide it at precisely the
		// moment there is finally something to see.
		if (!entity.isGraveWatcher()
				&& entity.squaredDistanceTo(x, y, z) < MIN_VISIBLE_DISTANCE * MIN_VISIBLE_DISTANCE) {
			return false;
		}
		return super.shouldRender(entity, frustum, x, y, z);
	}
}
