package net.ragnar.ragnarstwilightdimension;

import net.fabricmc.api.ModInitializer;
import net.ragnar.ragnarstwilightdimension.command.GravestoneCommand;
import net.ragnar.ragnarstwilightdimension.command.SilhouetteCommand;
import net.ragnar.ragnarstwilightdimension.entity.ModEntities;
import net.ragnar.ragnarstwilightdimension.block.ModBlocks;
import net.ragnar.ragnarstwilightdimension.particle.ModParticles;
import net.ragnar.ragnarstwilightdimension.command.BellCommand;
import net.ragnar.ragnarstwilightdimension.command.BlankCommand;
import net.ragnar.ragnarstwilightdimension.command.FaultCommand;
import net.ragnar.ragnarstwilightdimension.command.BloodMoonCommand;
import net.ragnar.ragnarstwilightdimension.command.StareCommand;
import net.ragnar.ragnarstwilightdimension.command.WitnessCommand;
import net.ragnar.ragnarstwilightdimension.command.WandererCommand;
import net.ragnar.ragnarstwilightdimension.event.BloodMoon;
import net.ragnar.ragnarstwilightdimension.event.Stare;
import net.ragnar.ragnarstwilightdimension.event.TwilightRespawn;
import net.ragnar.ragnarstwilightdimension.network.BloodMoonPayload;
import net.ragnar.ragnarstwilightdimension.network.StarePayload;
import net.ragnar.ragnarstwilightdimension.network.DeepPayload;
import net.ragnar.ragnarstwilightdimension.network.TheEntityPayload;
import net.ragnar.ragnarstwilightdimension.network.WitnessPayload;
import net.ragnar.ragnarstwilightdimension.entity.SilhouetteSpawner;
import net.ragnar.ragnarstwilightdimension.entity.WandererSpawner;
import net.ragnar.ragnarstwilightdimension.entity.BossBarWatch;
import net.ragnar.ragnarstwilightdimension.entity.WatcherSpawner;
import net.ragnar.ragnarstwilightdimension.entity.TheDeep;
import net.ragnar.ragnarstwilightdimension.entity.TheEntityFight;
import net.ragnar.ragnarstwilightdimension.entity.WitnessSpawner;
import net.ragnar.ragnarstwilightdimension.portal.TempleGate;
import net.ragnar.ragnarstwilightdimension.portal.TwilightPortal;
import net.ragnar.ragnarstwilightdimension.sound.ModSounds;
import net.ragnar.ragnarstwilightdimension.sound.ChurchBell;
import net.ragnar.ragnarstwilightdimension.sound.TwilightAmbience;
import net.ragnar.ragnarstwilightdimension.sound.TwilightLeviathan;
import net.ragnar.ragnarstwilightdimension.world.dimension.ModDimensions;
import net.ragnar.ragnarstwilightdimension.world.feature.ModFeatures;
import net.ragnar.ragnarstwilightdimension.world.gen.ModChunkGenerators;

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
		ModBlocks.initialize();
		ModParticles.initialize();
		ModChunkGenerators.initialize();
		BloodMoonPayload.register();
		StarePayload.register();
		WitnessPayload.register();
		TheEntityPayload.register();
		DeepPayload.register();
		TwilightPortal.register();
		TempleGate.register();
		SilhouetteSpawner.register();
		WandererSpawner.register();
		WitnessSpawner.register();
		WatcherSpawner.register();
		TheEntityFight.register();
		TheDeep.register();
		BossBarWatch.register();
		TwilightAmbience.register();
		TwilightLeviathan.register();
		ChurchBell.register();
		BloodMoon.register();
		Stare.register();
		TwilightRespawn.register();
		SilhouetteCommand.register();
		WandererCommand.register();
		BloodMoonCommand.register();
		GravestoneCommand.register();
		StareCommand.register();
		BellCommand.register();
		WitnessCommand.register();
		BlankCommand.register();
		FaultCommand.register();
		LOGGER.info("Twilight dimension loaded ({})", ModDimensions.TWILIGHT_WORLD.getValue());
	}

}
