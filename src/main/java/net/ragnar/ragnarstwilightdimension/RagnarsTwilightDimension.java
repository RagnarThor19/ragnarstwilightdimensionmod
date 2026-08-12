package net.ragnar.ragnarstwilightdimension;

import net.fabricmc.api.ModInitializer;
import net.ragnar.ragnarstwilightdimension.portal.TwilightPortal;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;

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
		TwilightPortal.register();
		LOGGER.info("Twilight dimension loaded ({})", ModDimensions.TWILIGHT_WORLD.getValue());
	}

}
