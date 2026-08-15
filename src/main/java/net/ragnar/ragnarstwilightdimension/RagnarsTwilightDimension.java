package net.ragnar.ragnarstwilightdimension;

import net.fabricmc.api.ModInitializer;
import net.ragnar.ragnarstwilightdimension.command.GravestoneCommand;
import net.ragnar.ragnarstwilightdimension.command.SilhouetteCommand;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;
import net.ragnar.ragnarstwilightdimension.command.BloodMoonCommand;
import net.ragnar.ragnarstwilightdimension.command.WandererCommand;
import net.ragnar.ragnarstwilightdimension.event.BloodMoon;
import net.ragnar.ragnarstwilightdimension.event.TwilightRespawn;
import net.ragnar.ragnarstwilightdimension.network.BloodMoonPayload;
import net.ragnar.ragnarstwilightdimension.entity.SilhouetteSpawner;
import net.ragnar.ragnarstwilightdimension.entity.WandererSpawner;
import net.ragnar.ragnarstwilightdimension.portal.TwilightPortal;
import net.ragnar.ragnarstwilightdimension.sound.ModSounds;
import net.ragnar.ragnarstwilightdimension.sound.TwilightAmbience;
import net.ragnar.ragnarstwilightdimension.sound.TwilightLeviathan;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;
import net.ragnar.ragnarstwilightdimension.world.feature.ModFeatures;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RagnarsTwilightDimension implements ModInitializer {
	public static final String MOD_ID = "ragnarstwilightdimension";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModSounds.initialize();
		ModEntities.initialize();
		ModFeatures.initialize();
		BloodMoonPayload.register();
		TwilightPortal.register();
		SilhouetteSpawner.register();
		WandererSpawner.register();
		TwilightAmbience.register();
		TwilightLeviathan.register();
		BloodMoon.register();
		TwilightRespawn.register();
		SilhouetteCommand.register();
		WandererCommand.register();
		BloodMoonCommand.register();
		GravestoneCommand.register();
		LOGGER.info("Twilight dimension loaded ({})", ModDimensions.TWILIGHT_WORLD.getValue());
	}

}
