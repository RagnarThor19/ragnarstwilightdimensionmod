package net.ragnar.ragnarstwilightdimension.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.EndPortalBlockEntityRenderer;
import net.ragnar.ragnarstwilightdimension.block.TemplePortalBlockEntity;

/**
 * Draws the temple portal, in a colour that is not the End's.
 *
 * <p>The starfield in a portal is not a texture that can be tinted - it is a shader, given two
 * textures and a depth, and the colours it layers them in are written into the shader itself. There
 * is no uniform to turn.
 *
 * <p>What there is, is a second one. Vanilla ships {@code rendertype_end_gateway} alongside
 * {@code rendertype_end_portal} for the gateways in the End, built the same way out of the same two
 * textures and coloured differently, and the renderer picks between them through this one
 * overridable method. So the whole of the difference is the line below: same effect, same depth,
 * same everything, drawn through the other shader.
 *
 * <p>Doing it this way rather than shipping a custom core shader means there is nothing here that
 * can fail to compile on somebody's driver, and nothing to keep working across a Minecraft update.
 */
@Environment(EnvType.CLIENT)
public class TemplePortalRenderer extends EndPortalBlockEntityRenderer<TemplePortalBlockEntity> {
	public TemplePortalRenderer(BlockEntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	protected RenderLayer getLayer() {
		return RenderLayer.getEndGateway();
	}
}
