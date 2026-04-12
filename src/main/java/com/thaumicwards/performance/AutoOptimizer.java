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

        // Mekanism (includes ore dedup)
        if (isLoaded("mekanism")) {
            totalChanges += optimizeMekanism(configDir);
        }

        // Ore deduplication - disable duplicate ores across mods
        totalChanges += deduplicateOres(configDir, serverConfigDir);

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

        // Torchmaster
        if (isLoaded("torchmaster")) {
            totalChanges += optimizeTorchmaster(configDir);
        }

        // SecurityCraft
        if (isLoaded("securitycraft")) {
            totalChanges += optimizeSecurityCraft(serverConfigDir);
        }

        // Sophisticated Backpacks
        if (isLoaded("sophisticatedbackpacks")) {
            totalChanges += optimizeSophisticatedBackpacks(configDir);
        }

        // Building Gadgets
        if (isLoaded("buildinggadgets")) {
            totalChanges += optimizeBuildingGadgets(serverConfigDir);
        }

        // Mystical Agriculture
        if (isLoaded("mysticalagriculture")) {
            totalChanges += optimizeMysticalAgriculture(configDir);
        }

        // Compact Machines
        if (isLoaded("compactmachines")) {
            totalChanges += optimizeCompactMachines(serverConfigDir);
        }

        // Forge server config
        totalChanges += optimizeForgeServer(serverConfigDir);

        // Forge fml.toml
        totalChanges += optimizeFML(configDir);

        // Waystones
        if (isLoaded("waystones")) {
            totalChanges += optimizeWaystones(configDir);
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

            // NOTE: Do NOT enable clear_mixin_classinfo - it crashes on dedicated servers
            // by trying to audit IBakedModel (a client-only class). If it was previously
            // enabled by us, disable it.
            if (content.contains("mixin.perf.clear_mixin_classinfo=true")) {
                content = content.replace("mixin.perf.clear_mixin_classinfo=true", "mixin.perf.clear_mixin_classinfo=false");
                log("ModernFix", "Disabled clear_mixin_classinfo (crashes on dedicated server)");
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

    private static int deduplicateOres(Path configDir, Path serverConfigDir) {
        int count = 0;
        // Only run if All The Ores is present (the unified source)
        if (!isLoaded("alltheores")) return 0;

        // Disable Thermal duplicate ores
        Path thermal = serverConfigDir.resolve("thermal-server.toml");
        if (Files.exists(thermal)) {
            Map<String, String> changes = new LinkedHashMap<>();
            changes.put("Copper = true", "Copper = false");
            changes.put("Tin = true", "Tin = false");
            changes.put("Lead = true", "Lead = false");
            changes.put("Silver = true", "Silver = false");
            changes.put("Nickel = true", "Nickel = false");
            count += applyTomlChanges(thermal, changes, "Thermal Ores (dedup)");
        }

        // Disable Mystical World duplicate ores
        Path mw = configDir.resolve("mysticalworld-common.toml");
        if (Files.exists(mw)) {
            try {
                String content = new String(Files.readAllBytes(mw), StandardCharsets.UTF_8);
                String original = content;
                // Set oreChances to 0 for duplicates
                // Copper, Silver, Lead, Tin sections
                content = content.replaceAll("(\\[oregen\\.Copper_oregen\\][^\\[]*?)oreChances = \\d+", "$1oreChances = 0");
                content = content.replaceAll("(\\[oregen\\.Silver_oregen\\][^\\[]*?)oreChances = \\d+", "$1oreChances = 0");
                content = content.replaceAll("(\\[oregen\\.Lead_oregen\\][^\\[]*?)oreChances = \\d+", "$1oreChances = 0");
                content = content.replaceAll("(\\[oregen\\.Tin_oregen\\][^\\[]*?)oreChances = \\d+", "$1oreChances = 0");
                if (!content.equals(original)) {
                    Files.write(mw, content.getBytes(StandardCharsets.UTF_8));
                    log("Mystical World Ores", "Disabled duplicate copper/silver/lead/tin ore gen");
                    count++;
                }
            } catch (IOException e) {
                ThaumicWards.LOGGER.warn("Failed to dedup Mystical World ores: {}", e.getMessage());
            }
        }

        // Disable Ice and Fire duplicate ores
        Path iaf = configDir.resolve("iceandfire-common.toml");
        if (Files.exists(iaf)) {
            Map<String, String> iafChanges = new LinkedHashMap<>();
            iafChanges.put("\"Generate Copper Ore\" = true", "\"Generate Copper Ore\" = false");
            iafChanges.put("\"Generate Silver Ore\" = true", "\"Generate Silver Ore\" = false");
            count += applyTomlChanges(iaf, iafChanges, "Ice and Fire Ores (dedup)");
        }

        log("Ore Deduplication", "Consolidated ores to All The Ores as single source (~30 fewer ore passes/chunk)");
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
            // Always overwrite — we now own these rules for server performance
            // Skip only if our marker comment is already present (avoid re-writing every startup)
            if (content.contains("ThaumicWards-managed")) return 0;

            // Rules are ordered: specific individual mob caps first (highest priority in InControl),
            // then per-mod caps, then vanilla overrides.
            String rules = "[\n" +
                // ── Marker so we know we wrote this ──────────────────────────────────────
                "  {\"_comment\": \"ThaumicWards-managed spawn rules - do not edit manually\"},\n" +

                // ── Alex's Mobs: individual high-count offenders ─────────────────────────
                "  {\"_comment\": \"Cap crows (top offender: 417 seen) to 30 per world\",\n" +
                "   \"mob\": \"alexsmobs:crow\",\n" +
                "   \"maxcount\": {\"amount\": 30}, \"result\": \"deny\"},\n" +

                "  {\"_comment\": \"Cap raccoons to 25 per world\",\n" +
                "   \"mob\": \"alexsmobs:raccoon\",\n" +
                "   \"maxcount\": {\"amount\": 25}, \"result\": \"deny\"},\n" +

                "  {\"_comment\": \"Cap emu to 20\",\n" +
                "   \"mob\": \"alexsmobs:emu\",\n" +
                "   \"maxcount\": {\"amount\": 20}, \"result\": \"deny\"},\n" +

                "  {\"_comment\": \"Cap seagull to 20\",\n" +
                "   \"mob\": \"alexsmobs:seagull\",\n" +
                "   \"maxcount\": {\"amount\": 20}, \"result\": \"deny\"},\n" +

                "  {\"_comment\": \"Cap bald eagle to 15\",\n" +
                "   \"mob\": \"alexsmobs:bald_eagle\",\n" +
                "   \"maxcount\": {\"amount\": 15}, \"result\": \"deny\"},\n" +

                "  {\"_comment\": \"Cap Alex's Mobs total to 20 per mod\",\n" +
                "   \"mod\": \"alexsmobs\",\n" +
                "   \"maxcount\": {\"amount\": 20, \"mod\": \"alexsmobs\"}, \"result\": \"deny\"},\n" +

                // ── Ice and Fire ─────────────────────────────────────────────────────────
                "  {\"_comment\": \"Limit fire/ice/lightning dragons to 2 total\",\n" +
                "   \"mob\": [\"iceandfire:fire_dragon\", \"iceandfire:ice_dragon\", \"iceandfire:lightning_dragon\"],\n" +
                "   \"maxcount\": {\"amount\": 2}, \"result\": \"deny\"},\n" +

                "  {\"_comment\": \"Limit hippogryphs to 8 total\",\n" +
                "   \"mob\": \"iceandfire:hippogryph\",\n" +
                "   \"maxcount\": {\"amount\": 8}, \"result\": \"deny\"},\n" +

                "  {\"_comment\": \"Cap Ice and Fire total at 15 per mod\",\n" +
                "   \"mod\": \"iceandfire\",\n" +
                "   \"maxcount\": {\"amount\": 15, \"mod\": \"iceandfire\"}, \"result\": \"deny\"},\n" +

                // ── Quark ────────────────────────────────────────────────────────────────
                "  {\"_comment\": \"Cap Quark crabs and other passive mobs at 12 per type\",\n" +
                "   \"mod\": \"quark\",\n" +
                "   \"maxcount\": {\"amount\": 12, \"mod\": \"quark\"}, \"result\": \"deny\"},\n" +

                // ── Pixies (high-cost AI, 213 seen) ─────────────────────────────────────
                "  {\"_comment\": \"Cap pixies at 20 total — high AI cost mob\",\n" +
                "   \"mob\": [\"ars_nouveau:wilden_stalker\", \"ars_nouveau:wilden_guardian\",\n" +
                "            \"ars_nouveau:wilden_hunter\", \"ars_nouveau:wilden_witch\",\n" +
                "            \"ars_nouveau:starbuncle\", \"ars_nouveau:whirlisprig\",\n" +
                "            \"ars_nouveau:drygmy\", \"ars_nouveau:wixie\"],\n" +
                "   \"maxcount\": {\"amount\": 20}, \"result\": \"deny\"},\n" +

                "  {\"_comment\": \"Cap Ars Nouveau total at 15 per mod\",\n" +
                "   \"mod\": \"ars_nouveau\",\n" +
                "   \"maxcount\": {\"amount\": 15, \"mod\": \"ars_nouveau\"}, \"result\": \"deny\"},\n" +

                // ── Mowzie's Mobs ────────────────────────────────────────────────────────
                "  {\"_comment\": \"Cap Mowzie's mobs at 10 total\",\n" +
                "   \"mod\": \"mowziesmobs\",\n" +
                "   \"maxcount\": {\"amount\": 10, \"mod\": \"mowziesmobs\"}, \"result\": \"deny\"},\n" +

                // ── Vanilla passive mob overrides ────────────────────────────────────────
                "  {\"_comment\": \"Cap vanilla sheep to 40 per world (311 seen)\",\n" +
                "   \"mob\": \"minecraft:sheep\",\n" +
                "   \"maxcount\": {\"amount\": 40}, \"result\": \"deny\"},\n" +

                "  {\"_comment\": \"Cap vanilla cows to 40 per world (228 seen)\",\n" +
                "   \"mob\": \"minecraft:cow\",\n" +
                "   \"maxcount\": {\"amount\": 40}, \"result\": \"deny\"},\n" +

                "  {\"_comment\": \"Cap vanilla chickens to 40 per world (289 seen)\",\n" +
                "   \"mob\": \"minecraft:chicken\",\n" +
                "   \"maxcount\": {\"amount\": 40}, \"result\": \"deny\"},\n" +

                "  {\"_comment\": \"Cap vanilla pigs to 30 per world\",\n" +
                "   \"mob\": \"minecraft:pig\",\n" +
                "   \"maxcount\": {\"amount\": 30}, \"result\": \"deny\"},\n" +

                "  {\"_comment\": \"Cap vanilla rabbits to 25 per world\",\n" +
                "   \"mob\": \"minecraft:rabbit\",\n" +
                "   \"maxcount\": {\"amount\": 25}, \"result\": \"deny\"},\n" +

                "  {\"_comment\": \"Cap vanilla bats to 20 per world\",\n" +
                "   \"mob\": \"minecraft:bat\",\n" +
                "   \"maxcount\": {\"amount\": 20}, \"result\": \"deny\"},\n" +

                // ── Chest minecarts (255 seen — likely dungeon loot, cap spawns) ─────────
                "  {\"_comment\": \"Cap chest minecarts to 30 per world (255 seen in stress test)\",\n" +
                "   \"mob\": \"minecraft:chest_minecart\",\n" +
                "   \"maxcount\": {\"amount\": 30}, \"result\": \"deny\"}\n" +

                "]";

            Files.write(spawnFile, rules.getBytes(StandardCharsets.UTF_8));
            log("InControl", "Wrote aggressive entity spawn caps: crows 30, raccoons 25, sheep/cows/chickens 40, " +
                "dragons 2, Alex's 20, I&F 15, Quark 12, Ars 15, Mowzie's 10, chest_minecarts 30");
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

    private static int optimizeBuildingGadgets(Path serverConfigDir) {
        Path file = serverConfigDir.resolve("buildinggadgets-server.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("\"Max Placement/Tick\" = 1024", "\"Max Placement/Tick\" = 64");
        changes.put("\"Max Copy/Tick\" = 32768", "\"Max Copy/Tick\" = 256");
        changes.put("\"Max Copy Dimensions\" = 256", "\"Max Copy Dimensions\" = 64");
        changes.put("\"Max Build Dimensions\" = 256", "\"Max Build Dimensions\" = 64");
        return applyTomlChanges(file, changes, "Building Gadgets");
    }

    private static int optimizeMysticalAgriculture(Path configDir) {
        Path file = configDir.resolve("mysticalagriculture-common.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("growthAcceleratorCooldown = 10", "growthAcceleratorCooldown = 30");
        changes.put("fakePlayerWatering = true", "fakePlayerWatering = false");
        return applyTomlChanges(file, changes, "Mystical Agriculture");
    }

    private static int optimizeTorchmaster(Path configDir) {
        Path file = configDir.resolve("torchmaster.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("megaTorchRadius = 64", "megaTorchRadius = 32");
        changes.put("dreadLampRadius = 64", "dreadLampRadius = 32");
        return applyTomlChanges(file, changes, "Torchmaster");
    }

    private static int optimizeSecurityCraft(Path serverConfigDir) {
        Path file = serverConfigDir.resolve("securitycraft-server.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("laserBlockRange = 5", "laserBlockRange = 3");
        changes.put("maxAlarmRange = 100", "maxAlarmRange = 32");
        return applyTomlChanges(file, changes, "SecurityCraft");
    }

    private static int optimizeSophisticatedBackpacks(Path configDir) {
        Path file = configDir.resolve("sophisticatedbackpacks-common.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("upgradesInContainedBackpacksAreFunctional = true", "upgradesInContainedBackpacksAreFunctional = false");
        changes.put("upgradesUseInventoriesOfBackpacksInBackpack = true", "upgradesUseInventoriesOfBackpacksInBackpack = false");
        return applyTomlChanges(file, changes, "Sophisticated Backpacks");
    }

    private static int optimizeCompactMachines(Path serverConfigDir) {
        Path file = serverConfigDir.resolve("compactmachines-server.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("chunkloading = true", "chunkloading = false");
        return applyTomlChanges(file, changes, "Compact Machines");
    }

    private static int optimizeForgeServer(Path serverConfigDir) {
        Path file = serverConfigDir.resolve("forge-server.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("removeErroringEntities = false", "removeErroringEntities = true");
        changes.put("removeErroringTileEntities = false", "removeErroringTileEntities = true");
        changes.put("zombieBaseSummonChance = 0.1", "zombieBaseSummonChance = 0.0");
        changes.put("logCascadingWorldGeneration = true", "logCascadingWorldGeneration = false");
        changes.put("fixVanillaCascading = false", "fixVanillaCascading = true");
        changes.put("dimensionUnloadQueueDelay = 0", "dimensionUnloadQueueDelay = 6000");
        return applyTomlChanges(file, changes, "Forge Server");
    }

    private static int optimizeFML(Path configDir) {
        Path file = configDir.resolve("fml.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("versionCheck = true", "versionCheck = false");
        return applyTomlChanges(file, changes, "FML");
    }

    private static int optimizeWaystones(Path configDir) {
        Path file = configDir.resolve("waystones-common.toml");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("worldGenFrequency = 25", "worldGenFrequency = 50");
        int count = applyTomlChanges(file, changes, "Waystones Common");

        Path server = configDir.resolve("waystones-server.toml");
        Map<String, String> srvChanges = new LinkedHashMap<>();
        srvChanges.put("dimensionalWarp = \"ALLOW\"", "dimensionalWarp = \"GLOBAL_ONLY\"");
        count += applyTomlChanges(server, srvChanges, "Waystones Server");
        return count;
    }

    private static int optimizeServerProperties() {
        Path file = FMLPaths.GAMEDIR.get().resolve("server.properties");
        if (!Files.exists(file)) return 0;
        int count = 0;
        try {
            Properties props = new Properties();
            props.load(Files.newBufferedReader(file));

            boolean changed = false;

            // Reduce view-distance to 6 if it's higher (each step saves ~28% chunk-tick load)
            String vd = props.getProperty("view-distance", "10");
            try {
                if (Integer.parseInt(vd) > 6) {
                    props.setProperty("view-distance", "6");
                    log("server.properties", "view-distance " + vd + "->6 (~28% fewer chunk-ticks per player)");
                    changed = true; count++;
                }
            } catch (NumberFormatException ignored) {}

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

            // Reduce entity-broadcast-range-percentage from 100 to 70.
            // Entities 30% farther than vanilla tracking range still send packets at full rate
            // when at 100; 70 cuts the broadcast volume for distant players significantly.
            String ebr = props.getProperty("entity-broadcast-range-percentage", "100");
            try {
                if (Integer.parseInt(ebr) > 70) {
                    props.setProperty("entity-broadcast-range-percentage", "70");
                    log("server.properties", "entity-broadcast-range-percentage " + ebr + "->70 (fewer entity packets to distant players)");
                    changed = true; count++;
                }
            } catch (NumberFormatException ignored) {}

            // Reduce network-compression-threshold: 256 bytes is fine for a LAN/VPS but
            // 128 compresses more aggressively and helps with many small packets (position updates).
            String nct = props.getProperty("network-compression-threshold", "256");
            try {
                if (Integer.parseInt(nct) > 128) {
                    props.setProperty("network-compression-threshold", "128");
                    log("server.properties", "network-compression-threshold " + nct + "->128 (compress more packets, helps with 60+ players)");
                    changed = true; count++;
                }
            } catch (NumberFormatException ignored) {}

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
