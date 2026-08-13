package net.ragnar.ragnarstwilightdimension;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.ragnar.ragnarstwilightdimension.client.TwilightMusic;
import net.ragnar.ragnarstwilightdimension.client.render.SilhouetteRenderer;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;

public class RagnarsTwilightDimensionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TwilightMusic.register();
        EntityRendererRegistry.register(ModEntities.SILHOUETTE, SilhouetteRenderer::new);
    }
}
