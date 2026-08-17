package net.ragnar.ragnarstwilightdimension;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.ragnar.ragnarstwilightdimension.client.BloodMoonClient;
import net.ragnar.ragnarstwilightdimension.client.StareClient;
import net.ragnar.ragnarstwilightdimension.client.WitnessClient;
import net.ragnar.ragnarstwilightdimension.client.TwilightMusic;
import net.ragnar.ragnarstwilightdimension.client.render.BloodSteveRenderer;
import net.ragnar.ragnarstwilightdimension.client.render.ChurchSteveRenderer;
import net.ragnar.ragnarstwilightdimension.client.render.GiantSteveRenderer;
import net.ragnar.ragnarstwilightdimension.client.render.PaleFigureRenderer;
import net.ragnar.ragnarstwilightdimension.client.render.SilhouetteRenderer;
import net.ragnar.ragnarstwilightdimension.client.render.WitnessRenderer;
import net.ragnar.ragnarstwilightdimension.client.render.WandererRenderer;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;

public class RagnarsTwilightDimensionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TwilightMusic.register();
        BloodMoonClient.register();
        StareClient.register();
        WitnessClient.register();
        EntityRendererRegistry.register(ModEntities.SILHOUETTE, SilhouetteRenderer::new);
        EntityRendererRegistry.register(ModEntities.PALE_FIGURE, PaleFigureRenderer::new);
        EntityRendererRegistry.register(ModEntities.WITNESS, WitnessRenderer::new);
        EntityRendererRegistry.register(ModEntities.WANDERER, WandererRenderer::new);
        EntityRendererRegistry.register(ModEntities.BLOOD_STEVE, BloodSteveRenderer::new);
        EntityRendererRegistry.register(ModEntities.GIANT_STEVE, GiantSteveRenderer::new);
        EntityRendererRegistry.register(ModEntities.CHURCH_STEVE, ChurchSteveRenderer::new);
    }
}
