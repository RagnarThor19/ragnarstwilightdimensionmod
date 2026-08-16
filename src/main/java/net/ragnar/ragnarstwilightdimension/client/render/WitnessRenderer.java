package net.ragnar.ragnarstwilightdimension.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.ragnar.ragnarstwilightdimension.entity.WitnessEntity;

/**
 * Draws the witness in the player's own skin.
 *
 * <p>Not the default skin the rest of the dimension wears - <b>yours</b>, read off the client every
 * frame, which means every player in a multiplayer world sees a different thing standing in the same
 * field, and each of them sees themselves. It is the one place in the mod where the copy is not
 * wrong, and it is scaled to {@link WitnessEntity#HEIGHT} so it is not right either.
 *
 * <p>Both arm widths are built up front and the model is swapped per frame, because a skin is either
 * the wide kind or the slim kind and drawing a slim one on the wide model puts a strip of the wrong
 * pixels down the outside of both arms. It is a cheap swap - the two models are already built, and
 * only the reference moves.
 */
public class WitnessRenderer extends MobEntityRenderer<WitnessEntity, WitnessModel> {
	/** Used until the client has a player to read a skin from, which is a frame or two at most. */
	private static final Identifier FALLBACK = Identifier.ofVanilla("textures/entity/player/wide/steve.png");

	private final WitnessModel wide;
	private final WitnessModel slim;

	public WitnessRenderer(EntityRendererFactory.Context context) {
		super(context, new WitnessModel(context.getPart(EntityModelLayers.PLAYER), false),
				0.5F * WitnessEntity.MODEL_SCALE);

		this.wide = this.model;
		this.slim = new WitnessModel(context.getPart(EntityModelLayers.PLAYER_SLIM), true);
	}

	@Override
	public void render(WitnessEntity entity, float yaw, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider vertexConsumers, int light) {
		SkinTextures skin = skin();
		this.model = skin != null && skin.model() == SkinTextures.Model.SLIM ? this.slim : this.wide;

		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	/**
	 * The player model is built at 1.8 blocks, so everything drawn for this entity is taken up by the
	 * same factor its hitbox was. Applied here rather than baked into the model so the two cannot
	 * drift apart.
	 */
	@Override
	protected void scale(WitnessEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(WitnessEntity.MODEL_SCALE, WitnessEntity.MODEL_SCALE, WitnessEntity.MODEL_SCALE);
	}

	@Override
	public Identifier getTexture(WitnessEntity entity) {
		SkinTextures skin = skin();
		return skin == null ? FALLBACK : skin.texture();
	}

	private static SkinTextures skin() {
		AbstractClientPlayerEntity player = MinecraftClient.getInstance().player;
		return player == null ? null : player.getSkinTextures();
	}
}
