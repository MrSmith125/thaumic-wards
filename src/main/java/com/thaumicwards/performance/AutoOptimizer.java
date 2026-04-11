package com.thaumicwards.performance;

import com.thaumicwards.core.ThaumicWards;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Automatically optimizes mod configs on server startup for large modpack performance.
 * Detects loaded mods and applies proven performance tweaks to their config files.
 * All changes are logged so admins know exactly what was modified.
 */
public class AutoOptimizer {

    private static final List<String> changelog = new ArrayList<>();
    private static boolean hasRun = false;

    public static void runOptimizations(MinecraftServer server) {
        if (hasRun) return;
        hasRun = true;
        changelog.clear();

        ThaumicWards.LOGGER.info("=== Thaumic Wards AutoOptimizer starting ===");
        ThaumicWards.LOGGER.info("Scanning loaded mods and applying performance optimizations...");

        Path configDir = FMLPaths.CONFIGDIR.get();
        Path serverConfigDir = FMLPaths.GAMEDIR.get().resolve("world").resolve("serverconfig");

        int totalChanges = 0;

        // Ice and Fire
        if (isLoaded("iceandfire")) {
            totalChanges += optimizeIceAndFire(configDir);
        }

        // Alex's Mobs
        if (isLoaded("alexsmobs")) {
            totalChanges += optimizeAlexsMobs(configDir);
        }

        // Quark
        if (isLoaded("quark")) {
            totalChanges += optimizeQuark(configDir);
        }

        // Mekanism
        if (isLoaded("mekanism")) {
            totalChanges += optimizeMekanism(configDir);
        }

        // Applied Energistics 2
        if (isLoaded("appliedenergistics2")) {
            totalChanges += optimizeAE2(configDir, serverConfigDir);
        }

        // PneumaticCraft
        if (isLoaded("pneumaticcraft")) {
            totalChanges += optimizePneumaticCraft(configDir);
        }

        // Immersive Engineering
        if (isLoaded("immersiveengineering")) {
            totalChanges += optimizeImmersiveEngineering(configDir, serverConfigDir);
        }

        // Botania
        if (isLoaded("botania")) {
            totalChanges += optimizeBotania(configDir);
        }

        // Mowzie's Mobs
        if (isLoaded("mowziesmobs")) {
            totalChanges += optimizeMowziesMobs(configDir);
        }

        // Scaling Health
        if (isLoaded("scalinghealth")) {
            totalChanges += optimizeScalingHealth(configDir);
        }

        // ModernFix
        if (isLoaded("modernfix")) {
            totalChanges += optimizeModernFix(configDir);
        }

        // FerriteCore
        if (isLoaded("ferritecore")) {
            totalChanges += optimizeFerriteCore(configDir);
        }

        // Ars Nouveau
        if (isLoaded("ars_nouveau")) {
            totalChanges += optimizeArsNouveau(configDir);
        }

        // Refined Storage + Cable Tiers
        if (isLoaded("refinedstorage")) {
            totalChanges += optimizeRefinedStorage(configDir, serverConfigDir);
        }
        if (isLoaded("cabletiers")) {
            totalChanges += optimizeCableTiers(configDir);
        }

        // Valkyrien Skies
        if (isLoaded("valkyrienskies")) {
            totalChanges += optimizeValkyrienSkies(configDir);
        }

        // FTB Ultimine
        if (isLoaded("ftbultimine")) {
            totalChanges += optimizeFTBUltimine(serverConfigDir);
        }

        // FTB Essentials
        if (isLoaded("ftbessentials")) {
            totalChanges += optimizeFTBEssentials(serverConfigDir);
        }

        // Thermal / Pipez
        if (isLoaded("pipez")) {
            totalChanges += optimizePipez(serverConfigDir);
        }

        // Apotheosis
        if (isLoaded("apotheosis")) {
            totalChanges += optimizeApotheosis(configDir);
        }

        // Forestry
        if (isLoaded("forestry")) {
            totalChanges += optimizeForestry(configDir);
        }

        // ProjectE
        if (isLoaded("projecte")) {
            totalChanges += optimizeProjectE(configDir);
        }

        // Tinkers' Construct
        if (isLoaded("tconstruct")) {
            totalChanges += optimizeTinkersConstruct(configDir);
        }

        // InControl - write spawn rules if empty
        if (isLoaded("incontrol")) {
            totalChanges += optimizeInControl(configDir);
        }

        // Extreme Reactors
        if (isLoaded("bigreactors")) {
            totalChanges += optimizeExtremeReactors(configDir);
        }

        // RFTools Builder
        if (isLoaded("rftoolsbuilder")) {
            totalChanges += optimizeRFToolsBuilder(serverConfigDir);
        }

        // RFTools Utility (screens)
        if (isLoaded("rftoolsutility")) {
            totalChanges += optimizeRFToolsUtility(serverConfigDir);
        }

        // server.properties
        totalChanges += optimizeServerProperties();

        ThaumicWards.LOGGER.info("=== AutoOptimizer complete: {} optimizations applied ===", totalChanges);
        for (String change : changelog) {
            ThaumicWards.LOGGER.info("  [OPT] {}", change);
        }
    }

    // --- Per-Mod Optimizers ---

    private static int optimizeIceAndFire(Path configDir) {
        Path file = configDir.resolve("iceandfire-common.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("\"Dragon Max Pathfinding Nodes\" = 5000", "\"Dragon Max Pathfinding Nodes\" = 1500");
        changes.put("\"Dragon Target Search Length\" = 128", "\"Dragon Target Search Length\" = 48");
        changes.put("\"Dragon Wander From Home Distance\" = 40", "\"Dragon Wander From Home Distance\" = 20");
        changes.put("\"Dragons Dig When Stuck\" = true", "\"Dragons Dig When Stuck\" = false");
        changes.put("\"Dragon Block Break Cooldown\" = 5", "\"Dragon Block Break Cooldown\" = 40");
        changes.put("\"Tamed Dragon Griefing\" = true", "\"Tamed Dragon Griefing\" = false");
        changes.put("\"Myrmex Colony Max Size\" = 80", "\"Myrmex Colony Max Size\" = 30");
        changes.put("\"Myrmex Maximum Wander Radius\" = 50", "\"Myrmex Maximum Wander Radius\" = 30");
        changes.put("\"Villagers Fear Dragons\" = true", "\"Villagers Fear Dragons\" = false");
        changes.put("\"Animals Fear Dragons\" = true", "\"Animals Fear Dragons\" = false");
        changes.put("\"Generate Dragon Cave Chance\" = 180", "\"Generate Dragon Cave Chance\" = 600");
        changes.put("\"Generate Dragon Roost Chance\" = 360", "\"Generate Dragon Roost Chance\" = 800");
        changes.put("\"Hydra Caves Gen Chance\" = 100", "\"Hydra Caves Gen Chance\" = 400");
        changes.put("\"Spawn Cyclops Cave Chance\" = 100", "\"Spawn Cyclops Cave Chance\" = 350");
        changes.put("\"Spawn Wandering Cyclops Chance\" = 100", "\"Spawn Wandering Cyclops Chance\" = 350");
        changes.put("\"Sea Serpent Griefing\" = true", "\"Sea Serpent Griefing\" = false");
        changes.put("\"Cyclops Griefing\" = true", "\"Cyclops Griefing\" = false");
        changes.put("\"Chunk Load Summon Crystal\" = true", "\"Chunk Load Summon Crystal\" = false");
        changes.put("\"Pixies Steal Items\" = true", "\"Pixies Steal Items\" = false");
        return applyTomlChanges(file, changes, "Ice and Fire");
    }

    private static int optimizeAlexsMobs(Path configDir) {
        Path file = configDir.resolve("alexsmobs.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("tigerSpawnWeight = 100", "tigerSpawnWeight = 30");
        changes.put("straddlerSpawnWeight = 85", "straddlerSpawnWeight = 30");
        changes.put("warpedToadSpawnWeight = 80", "warpedToadSpawnWeight = 30");
        changes.put("capuchinMonkeySpawnWeight = 55", "capuchinMonkeySpawnWeight = 20");
        changes.put("gorillaSpawnWeight = 50", "gorillaSpawnWeight = 20");
        changes.put("crocodileSpawnWeight = 40", "crocodileSpawnWeight = 15");
        changes.put("gazelleSpawnWeight = 40", "gazelleSpawnWeight = 15");
        changes.put("elephantSpawnWeight = 30", "elephantSpawnWeight = 10");
        changes.put("raccoonStealFromChests = true", "raccoonStealFromChests = false");
        changes.put("seagullStealing = true", "seagullStealing = false");
        changes.put("mungusBiomeTransformationType = 2", "mungusBiomeTransformationType = 0");
        changes.put("leafcutterAntColonySize = 20", "leafcutterAntColonySize = 10");
        return applyTomlChanges(file, changes, "Alex's Mobs");
    }

    private static int optimizeQuark(Path configDir) {
        Path file = configDir.resolve("quark-common.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("\"Use Fast Worldgen\" = false", "\"Use Fast Worldgen\" = true");
        changes.put("\"Lush Underground Biome\" = true", "\"Lush Underground Biome\" = false");
        changes.put("\"Elder Prismarine Underground Biome\" = true", "\"Elder Prismarine Underground Biome\" = false");
        changes.put("\"Glowshroom Underground Biome\" = true", "\"Glowshroom Underground Biome\" = false");
        changes.put("\"Permafrost Underground Biome\" = true", "\"Permafrost Underground Biome\" = false");
        changes.put("\"Slime Underground Biome\" = true", "\"Slime Underground Biome\" = false");
        changes.put("\"Brimstone Underground Biome\" = true", "\"Brimstone Underground Biome\" = false");
        changes.put("\"Cave Crystal Underground Biome\" = true", "\"Cave Crystal Underground Biome\" = false");
        changes.put("\"Overgrown Underground Biome\" = true", "\"Overgrown Underground Biome\" = false");
        changes.put("\"Spider Nest Underground Biome\" = true", "\"Spider Nest Underground Biome\" = false");
        changes.put("\"Sandstone Underground Biome\" = true", "\"Sandstone Underground Biome\" = false");
        changes.put("\"Mega Caves\" = true", "\"Mega Caves\" = false");
        changes.put("\"Big Dungeon\" = true", "\"Big Dungeon\" = false");
        changes.put("\"Feeding Trough\" = true", "\"Feeding Trough\" = false");
        changes.put("\"Items In Backpack Tick\" = true", "\"Items In Backpack Tick\" = false");
        changes.put("\"Villagers Follow Emeralds\" = true", "\"Villagers Follow Emeralds\" = false");
        return applyTomlChanges(file, changes, "Quark");
    }

    private static int optimizeMekanism(Path configDir) {
        int count = 0;
        Path mekDir = configDir.resolve("Mekanism");

        // general.toml - radiation, digital miner, laser, pump
        Path general = mekDir.resolve("general.toml");
        Map<String, String> genChanges = new LinkedHashMap<>();
        genChanges.put("chunkCheckRadius = 5", "chunkCheckRadius = 2");
        genChanges.put("radioactiveWasteBarrelProcessTicks = 20", "radioactiveWasteBarrelProcessTicks = 40");
        genChanges.put("ticksPerMine = 80", "ticksPerMine = 120");
        genChanges.put("maxRadius = 32", "maxRadius = 24");
        genChanges.put("maxPlenisherNodes = 4000", "maxPlenisherNodes = 2000");
        genChanges.put("maxPumpRange = 80", "maxPumpRange = 48");
        genChanges.put("range = 64", "range = 32"); // laser
        genChanges.put("blockDeactivationDelay = 60", "blockDeactivationDelay = 100");
        count += applyTomlChanges(general, genChanges, "Mekanism");

        // gear.toml - meka-tool
        Path gear = mekDir.resolve("gear.toml");
        Map<String, String> gearChanges = new LinkedHashMap<>();
        gearChanges.put("maxTeleportReach = 100", "maxTeleportReach = 48");
        gearChanges.put("extendedMining = true", "extendedMining = false");
        count += applyTomlChanges(gear, gearChanges, "Mekanism Gear");

        // additions-common.toml - baby mobs
        Path additions = mekDir.resolve("additions-common.toml");
        Map<String, String> addChanges = new LinkedHashMap<>();
        // Baby endermen teleport constantly, others add unnecessary entity count
        count += applyTomlChanges(additions, addChanges, "Mekanism Additions");

        // generators.toml - turbine flow rates
        Path generators = mekDir.resolve("generators.toml");
        Map<String, String> genrChanges = new LinkedHashMap<>();
        genrChanges.put("turbineVentGasFlow = 32000.0", "turbineVentGasFlow = 16000.0");
        genrChanges.put("condenserRate = 64000", "condenserRate = 32000");
        count += applyTomlChanges(generators, genrChanges, "Mekanism Generators");

        return count;
    }

    private static int optimizeAE2(Path configDir, Path serverConfigDir) {
        Path file = configDir.resolve("appliedenergistics2-common.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("craftingCalculationTimePerTick = 5", "craftingCalculationTimePerTick = 3");
        changes.put("formationPlaneEntityLimit = 128", "formationPlaneEntityLimit = 32");
        return applyTomlChanges(file, changes, "Applied Energistics 2");
    }

    private static int optimizePneumaticCraft(Path configDir) {
        Path file = configDir.resolve("pneumaticcraft-common.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("max_programming_area = 250000", "max_programming_area = 25000");
        changes.put("fluid_tank_update_rate = 10", "fluid_tank_update_rate = 40");
        changes.put("drones_render_held_item = true", "drones_render_held_item = false");
        changes.put("drone_debugger_path_particles = true", "drone_debugger_path_particles = false");
        changes.put("block_hit_particles = true", "block_hit_particles = false");
        changes.put("disable_kerosene_lamp_fake_air_block = false", "disable_kerosene_lamp_fake_air_block = true");
        changes.put("stuck_drone_teleport_ticks = 20", "stuck_drone_teleport_ticks = 10");
        changes.put("max_drone_charging_station_search_range = 80", "max_drone_charging_station_search_range = 48");
        return applyTomlChanges(file, changes, "PneumaticCraft");
    }

    private static int optimizeImmersiveEngineering(Path configDir, Path serverConfigDir) {
        Path file = serverConfigDir.resolve("immersiveengineering-server.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("enableWireDamage = true", "enableWireDamage = false");
        changes.put("particles = true", "particles = false");
        return applyTomlChanges(file, changes, "Immersive Engineering");
    }

    private static int optimizeBotania(Path configDir) {
        Path file = configDir.resolve("botania-common.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("flowerBindingForceCheck = true", "flowerBindingForceCheck = false");
        changes.put("chargeAnimation = true", "chargeAnimation = false");
        changes.put("traceTime = 400", "traceTime = 200");
        return applyTomlChanges(file, changes, "Botania");
    }

    private static int optimizeMowziesMobs(Path configDir) {
        Path file = configDir.resolve("mowziesmobs-common.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("spawn_rate = 70", "spawn_rate = 20"); // Foliaath
        return applyTomlChanges(file, changes, "Mowzie's Mobs");
    }

    private static int optimizeArsNouveau(Path configDir) {
        Path file = configDir.resolve("ars_nouveau-common.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("updateInterval = 5", "updateInterval = 10");
        changes.put("drygmyMaxProgress = 20", "drygmyMaxProgress = 30");
        changes.put("drygmyQuantityCap = 5", "drygmyQuantityCap = 3");
        changes.put("sylphManaCost = 250", "sylphManaCost = 500");
        changes.put("wstalkerWeight = 50", "wstalkerWeight = 25");
        changes.put("whunterWeight = 50", "whunterWeight = 25");
        changes.put("wguardianWeight = 50", "wguardianWeight = 25");
        changes.put("hunterHuntsAnimals = true", "hunterHuntsAnimals = false");
        return applyTomlChanges(file, changes, "Ars Nouveau");
    }

    private static int optimizeScalingHealth(Path configDir) {
        Path file = configDir.resolve("scaling-health").resolve("game_settings.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        // These are the most critical Scaling Health changes
        // Exact key names depend on the TOML structure; will be refined after testing
        return applyTomlChanges(file, changes, "Scaling Health");
    }

    private static int optimizeModernFix(Path configDir) {
        Path file = configDir.resolve("modernfix-mixins.properties");
        if (!Files.exists(file)) return 0;
        int count = 0;
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String original = content;

            // Enable memory-saving mixins (only if not already set)
            if (!content.contains("mixin.perf.clear_mixin_classinfo=true")) {
                content += "\nmixin.perf.clear_mixin_classinfo=true\n";
                log("ModernFix", "Enabled clear_mixin_classinfo (frees mixin metadata after launch)");
                count++;
            }
            if (!content.contains("mixin.perf.deduplicate_location=true")) {
                content += "mixin.perf.deduplicate_location=true\n";
                log("ModernFix", "Enabled deduplicate_location (deduplicates ResourceLocations)");
                count++;
            }
            if (!content.contains("mixin.perf.reuse_datapacks=true")) {
                content += "mixin.perf.reuse_datapacks=true\n";
                log("ModernFix", "Enabled reuse_datapacks (faster /reload)");
                count++;
            }

            if (!content.equals(original)) {
                Files.write(file, content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            ThaumicWards.LOGGER.warn("Failed to optimize ModernFix config: {}", e.getMessage());
        }
        return count;
    }

    private static int optimizeFerriteCore(Path configDir) {
        Path file = configDir.resolve("ferritecore-mixin.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("compactFastMap = false", "compactFastMap = true");
        return applyTomlChanges(file, changes, "FerriteCore");
    }

    private static int optimizeRefinedStorage(Path configDir, Path serverConfigDir) {
        // RS server config is in world/serverconfig
        return 0; // Tick rate changes need server config, handled separately
    }

    private static int optimizeCableTiers(Path configDir) {
        Path file = configDir.resolve("cabletiers-common.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("ultraimporterspeed = 6", "ultraimporterspeed = 3");
        changes.put("ultraexporterspeed = 6", "ultraexporterspeed = 3");
        changes.put("ultraconstructorspeed = 6", "ultraconstructorspeed = 3");
        changes.put("ultradestructorspeed = 6", "ultradestructorspeed = 3");
        changes.put("ultradiskmanipulatorspeed = 6", "ultradiskmanipulatorspeed = 3");
        return applyTomlChanges(file, changes, "Cable Tiers");
    }

    private static int optimizeValkyrienSkies(Path configDir) {
        Path file = configDir.resolve("valkyrienskies").resolve("vs_core_server.json");
        if (!Files.exists(file)) return 0;
        int count = 0;
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String original = content;

            content = content.replace("\"physicsTicksPerGameTick\": 3", "\"physicsTicksPerGameTick\": 1");
            content = content.replace("\"lodDetail\": 4096", "\"lodDetail\": 512");
            content = content.replace("\"shipLoadDistance\": 128.0", "\"shipLoadDistance\": 64.0");
            content = content.replace("\"shipUnloadDistance\": 196.0", "\"shipUnloadDistance\": 96.0");

            if (!content.equals(original)) {
                Files.write(file, content.getBytes(StandardCharsets.UTF_8));
                log("Valkyrien Skies", "Reduced physicsTicksPerGameTick 3->1, lodDetail 4096->512, load distances halved");
                count++;
            }
        } catch (IOException e) {
            ThaumicWards.LOGGER.warn("Failed to optimize VS config: {}", e.getMessage());
        }

        // Also disable mob spawns on ships
        Path vsServer = configDir.resolve("valkyrienskies").resolve("vs_server.json");
        if (Files.exists(vsServer)) {
            try {
                String content = new String(Files.readAllBytes(vsServer), StandardCharsets.UTF_8);
                String original = content;
                content = content.replace("\"allowMobSpawns\": true", "\"allowMobSpawns\": false");
                content = content.replace("\"aiOnShips\": true", "\"aiOnShips\": false");
                if (!content.equals(original)) {
                    Files.write(vsServer, content.getBytes(StandardCharsets.UTF_8));
                    log("Valkyrien Skies", "Disabled mob spawns and AI on ships");
                    count++;
                }
            } catch (IOException e) {
                ThaumicWards.LOGGER.warn("Failed to optimize VS server config: {}", e.getMessage());
            }
        }
        return count;
    }

    private static int optimizeFTBUltimine(Path serverConfigDir) {
        Path file = serverConfigDir.resolve("ftbultimine-server.snbt");
        if (!Files.exists(file)) return 0;
        int count = 0;
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String original = content;
            content = content.replace("max_blocks: 64", "max_blocks: 16");
            if (!content.equals(original)) {
                Files.write(file, content.getBytes(StandardCharsets.UTF_8));
                log("FTB Ultimine", "Reduced max_blocks 64->16 (prevents lag spikes)");
                count++;
            }
        } catch (IOException e) {
            ThaumicWards.LOGGER.warn("Failed to optimize FTB Ultimine: {}", e.getMessage());
        }
        return count;
    }

    private static int optimizeFTBEssentials(Path serverConfigDir) {
        Path file = serverConfigDir.resolve("ftbessentials.snbt");
        if (!Files.exists(file)) return 0;
        int count = 0;
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String original = content;
            content = content.replace("max_distance: 100000", "max_distance: 25000");
            if (!content.equals(original)) {
                Files.write(file, content.getBytes(StandardCharsets.UTF_8));
                log("FTB Essentials", "Reduced /rtp max_distance 100000->25000 (limits world growth)");
                count++;
            }
        } catch (IOException e) {
            ThaumicWards.LOGGER.warn("Failed to optimize FTB Essentials: {}", e.getMessage());
        }
        return count;
    }

    private static int optimizePipez(Path serverConfigDir) {
        Path file = serverConfigDir.resolve("pipez-server.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        // Ultimate pipe speed 1 -> 5 (every tick -> every 5 ticks)
        // These keys depend on exact TOML format
        return applyTomlChanges(file, changes, "Pipez");
    }

    private static int optimizeApotheosis(Path configDir) {
        // Apotheosis uses .cfg files, not TOML
        Path deadlyFile = configDir.resolve("apotheosis").resolve("deadly.cfg");
        if (!Files.exists(deadlyFile)) return 0;
        int count = 0;
        try {
            String content = new String(Files.readAllBytes(deadlyFile), StandardCharsets.UTF_8);
            String original = content;
            content = content.replace("I:\"Surface Boss Chance\"=85", "I:\"Surface Boss Chance\"=250");
            content = content.replace("I:\"Random Affix Chance\"=125", "I:\"Random Affix Chance\"=350");
            if (!content.equals(original)) {
                Files.write(deadlyFile, content.getBytes(StandardCharsets.UTF_8));
                log("Apotheosis", "Reduced boss spawn frequency (85->250) and affix chance (125->350)");
                count++;
            }
        } catch (IOException e) {
            ThaumicWards.LOGGER.warn("Failed to optimize Apotheosis: {}", e.getMessage());
        }
        return count;
    }

    private static int optimizeExtremeReactors(Path configDir) {
        Path file = configDir.resolve("extremereactors").resolve("common.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("maxReactorSize = 32", "maxReactorSize = 16");
        changes.put("maxTurbineSize = 32", "maxTurbineSize = 16");
        changes.put("maxReactorHeight = 48", "maxReactorHeight = 32");
        changes.put("ticksPerRedstoneUpdate = 20", "ticksPerRedstoneUpdate = 40");
        return applyTomlChanges(file, changes, "Extreme Reactors");
    }

    private static int optimizeRFToolsBuilder(Path serverConfigDir) {
        Path file = serverConfigDir.resolve("rftoolsbuilder-server.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("quarryBaseSpeed = 8", "quarryBaseSpeed = 2");
        changes.put("quarryChunkloads = true", "quarryChunkloads = false");
        changes.put("clearingQuarryAllowed = true", "clearingQuarryAllowed = false");
        changes.put("maxBuilderDimension = 512", "maxBuilderDimension = 128");
        changes.put("quarryInfusionSpeedFactor = 20", "quarryInfusionSpeedFactor = 4");
        changes.put("surfaceAreaPerTick = 262144", "surfaceAreaPerTick = 65536");
        changes.put("maxShieldSize = 256", "maxShieldSize = 128");
        return applyTomlChanges(file, changes, "RFTools Builder");
    }

    private static int optimizeRFToolsUtility(Path serverConfigDir) {
        Path file = serverConfigDir.resolve("rftoolsutility-server.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("screenRefreshTiming = 500", "screenRefreshTiming = 1000");
        changes.put("ticksPerLocatorScan = 40", "ticksPerLocatorScan = 80");
        changes.put("locatorMaxEnergyChunks = 25", "locatorMaxEnergyChunks = 9");
        return applyTomlChanges(file, changes, "RFTools Utility");
    }

    private static int optimizeInControl(Path configDir) {
        Path spawnFile = configDir.resolve("incontrol").resolve("spawn.json");
        if (!Files.exists(spawnFile)) return 0;
        try {
            String content = new String(Files.readAllBytes(spawnFile), StandardCharsets.UTF_8).trim();
            // Only write rules if the file is empty (just "[]")
            if (!"[]".equals(content)) return 0;

            String rules = "[\n" +
                "  {\"_comment\": \"Cap Alex's Mobs at 40\", \"mod\": \"alexsmobs\", " +
                "\"maxcount\": {\"amount\": 40, \"mod\": \"alexsmobs\"}, \"result\": \"deny\"},\n" +
                "  {\"_comment\": \"Cap Ice and Fire at 30\", \"mod\": \"iceandfire\", " +
                "\"maxcount\": {\"amount\": 30, \"mod\": \"iceandfire\"}, \"result\": \"deny\"},\n" +
                "  {\"_comment\": \"Cap Mowzie's at 20\", \"mod\": \"mowziesmobs\", " +
                "\"maxcount\": {\"amount\": 20, \"mod\": \"mowziesmobs\"}, \"result\": \"deny\"},\n" +
                "  {\"_comment\": \"Cap Quark mobs at 25\", \"mod\": \"quark\", " +
                "\"maxcount\": {\"amount\": 25, \"mod\": \"quark\"}, \"result\": \"deny\"},\n" +
                "  {\"_comment\": \"Cap Ars Nouveau at 20\", \"mod\": \"ars_nouveau\", " +
                "\"maxcount\": {\"amount\": 20, \"mod\": \"ars_nouveau\"}, \"result\": \"deny\"},\n" +
                "  {\"_comment\": \"Limit dragons to 3\", \"mob\": [\"iceandfire:fire_dragon\", " +
                "\"iceandfire:ice_dragon\", \"iceandfire:lightning_dragon\"], " +
                "\"maxcount\": {\"amount\": 3}, \"result\": \"deny\"}\n" +
                "]";

            Files.write(spawnFile, rules.getBytes(StandardCharsets.UTF_8));
            log("InControl", "Wrote entity spawn caps: Alex's 40, I&F 30, Mowzie's 20, Quark 25, Ars 20, Dragons 3");
            return 1;
        } catch (IOException e) {
            ThaumicWards.LOGGER.warn("Failed to write InControl spawn rules: {}", e.getMessage());
        }
        return 0;
    }

    private static int optimizeForestry(Path configDir) {
        int count = 0;
        // Butterfly entities - cap at 150 from 1000
        Path lepid = configDir.resolve("forestry").resolve("lepidopterology.cfg");
        if (Files.exists(lepid)) {
            try {
                String content = new String(Files.readAllBytes(lepid), StandardCharsets.UTF_8);
                String original = content;
                content = content.replace("I:maximum=1000", "I:maximum=150");
                content = content.replace("I:spawn.limit=100", "I:spawn.limit=20");
                content = content.replace("I:maxDistance=64", "I:maxDistance=32");
                if (!content.equals(original)) {
                    Files.write(lepid, content.getBytes(StandardCharsets.UTF_8));
                    log("Forestry", "Butterfly cap 1000->150, spawn limit 100->20, distance 64->32");
                    count++;
                }
            } catch (IOException e) {
                ThaumicWards.LOGGER.warn("Failed to optimize Forestry lepidopterology: {}", e.getMessage());
            }
        }
        // Bee work rate
        Path apic = configDir.resolve("forestry").resolve("apiculture.cfg");
        if (Files.exists(apic)) {
            try {
                String content = new String(Files.readAllBytes(apic), StandardCharsets.UTF_8);
                String original = content;
                content = content.replace("I:ticks.work=550", "I:ticks.work=750");
                if (!content.equals(original)) {
                    Files.write(apic, content.getBytes(StandardCharsets.UTF_8));
                    log("Forestry", "Bee work interval 550->750 ticks");
                    count++;
                }
            } catch (IOException e) {
                ThaumicWards.LOGGER.warn("Failed to optimize Forestry apiculture: {}", e.getMessage());
            }
        }
        return count;
    }

    private static int optimizeProjectE(Path configDir) {
        int count = 0;
        // EMC pregeneration
        Path mapping = configDir.resolve("ProjectE").resolve("mapping.toml");
        Map<String, String> mapChanges = new LinkedHashMap<>();
        mapChanges.put("pregenerate = false", "pregenerate = true");
        count += applyTomlChanges(mapping, mapChanges, "ProjectE Mapping");

        // Server config - timePedBonus, radius mining
        Path server = configDir.resolve("ProjectE").resolve("server.toml");
        Map<String, String> srvChanges = new LinkedHashMap<>();
        srvChanges.put("timePedBonus = 18", "timePedBonus = 4");
        srvChanges.put("disableAllRadiusMining = false", "disableAllRadiusMining = true");
        count += applyTomlChanges(server, srvChanges, "ProjectE Server");

        return count;
    }

    private static int optimizeTinkersConstruct(Path configDir) {
        Path file = configDir.resolve("tconstruct-common.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("separation = 13", "separation = 30"); // blood islands
        changes.put("showAllAnvilVariants = true", "showAllAnvilVariants = false");
        changes.put("showAllTableVariants = true", "showAllTableVariants = false");
        return applyTomlChanges(file, changes, "Tinkers' Construct");
    }

    private static int optimizeServerProperties() {
        Path file = FMLPaths.GAMEDIR.get().resolve("server.properties");
        if (!Files.exists(file)) return 0;
        int count = 0;
        try {
            Properties props = new Properties();
            props.load(Files.newBufferedReader(file));

            boolean changed = false;

            if ("10".equals(props.getProperty("view-distance"))) {
                props.setProperty("view-distance", "6");
                log("server.properties", "view-distance 10->6 (62% fewer chunks per player)");
                changed = true; count++;
            }
            if ("true".equals(props.getProperty("sync-chunk-writes"))) {
                props.setProperty("sync-chunk-writes", "false");
                log("server.properties", "sync-chunk-writes true->false (async chunk saves)");
                changed = true; count++;
            }
            if ("60000".equals(props.getProperty("max-tick-time"))) {
                props.setProperty("max-tick-time", "-1");
                log("server.properties", "max-tick-time 60000->-1 (disable watchdog for modded)");
                changed = true; count++;
            }
            if ("false".equals(props.getProperty("allow-flight"))) {
                props.setProperty("allow-flight", "true");
                log("server.properties", "allow-flight false->true (prevents kicks from mod flight)");
                changed = true; count++;
            }

            if (changed) {
                props.store(Files.newBufferedWriter(file), "Optimized by Thaumic Wards AutoOptimizer");
            }
        } catch (IOException e) {
            ThaumicWards.LOGGER.warn("Failed to optimize server.properties: {}", e.getMessage());
        }
        return count;
    }

    // --- Utility Methods ---

    private static int applyTomlChanges(Path file, Map<String, String> changes, String modName) {
        if (!Files.exists(file) || changes.isEmpty()) return 0;
        int count = 0;
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String original = content;

            for (Map.Entry<String, String> entry : changes.entrySet()) {
                if (content.contains(entry.getKey())) {
                    content = content.replace(entry.getKey(), entry.getValue());
                    log(modName, entry.getKey().trim() + " -> " + entry.getValue().trim());
                    count++;
                }
            }

            if (!content.equals(original)) {
                Files.write(file, content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            ThaumicWards.LOGGER.warn("Failed to optimize {} config at {}: {}", modName, file, e.getMessage());
        }
        return count;
    }

    private static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    private static void log(String mod, String change) {
        String entry = "[" + mod + "] " + change;
        changelog.add(entry);
        ThaumicWards.LOGGER.info("  [AutoOpt] {}", entry);
    }

    public static List<String> getChangelog() {
        return Collections.unmodifiableList(changelog);
    }

    public static void reset() {
        hasRun = false;
        changelog.clear();
    }
}
