package com.thaumicwards.performance;

import com.thaumicwards.config.ServerConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.AmbientEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * EntityTickHandler - reduces CPU usage by skipping entity AI/goal ticks.
 *
 * Tick skipping categories (priority order, first match wins):
 *   BLACKLIST  — Bosses and special mobs: NEVER skip (always tick at full rate)
 *   PLAYER     — Never skip players
 *   DISTANT    — Chunk is far from all players: adaptive tick rate
 *   MODDED_COMPLEX_NEAR — Modded mobs with expensive AI near players: moderate reduction
 *   PASSIVE_NEAR_32     — Passive mobs > 32 blocks from nearest player: skip aggressively
 *   PASSIVE_NEAR        — Passive mobs within 32 blocks: light skip (5 TPS equivalent)
 *   HOSTILE_NEAR        — Hostile mobs near players: skip only under SEVERE throttle
 *
 * Modded mob categories handled explicitly:
 *   - Ice and Fire: dragons, hippogryphs, myrmex (complex multi-phase pathfinding)
 *   - Alex's Mobs: various large fauna with complex goal trees
 *   - Mowzie's Mobs: bosses (blacklisted), non-boss followers/summoned (modded complex)
 *   - Quark: crabs, foxhounds, wraiths
 *   - Twilight Forest: bosses (blacklisted), general TF mobs (modded complex)
 *
 * Distance measurement:
 *   Block-level distance is used for passive-near threshold (32 blocks).
 *   Chunk-level distance continues to be used for the distant/near-player chunk split
 *   (cheap, cached every 100 ticks via TickRateManager).
 */
public class EntityTickHandler {

    // Squared block distance threshold for "close enough to a player to tick normally"
    // for passive mobs.  32 blocks → 32² = 1024.
    private static final double PASSIVE_NEAR_DIST_SQ = 32.0 * 32.0;

    // Squared distance for modded complex AI mobs — we still reduce their tick rate
    // even when they are within the chunk-near radius but far from actual players.
    private static final double MODDED_COMPLEX_NEAR_DIST_SQ = 48.0 * 48.0;

    // -----------------------------------------------------------------------
    // Modded mob registry name prefixes — used for string-based classification
    // when Forge class hierarchy is insufficient (mods don't extend vanilla classes).
    // We compare against the ResourceLocation's namespace (mod ID) and path prefix.
    // -----------------------------------------------------------------------

    // Ice and Fire mod (iceandfire) — dragons, hippogryphs, myrmex, stymphalian birds
    private static final String MOD_ICE_AND_FIRE = "iceandfire";

    // Alex's Mobs (alexsmobs) — large fauna such as bears, anacondas, sunbirds, etc.
    private static final String MOD_ALEXS_MOBS = "alexsmobs";

    // Mowzie's Mobs (mowziesmobs) — complex AI boss-like creatures
    private static final String MOD_MOWZIES = "mowziesmobs";

    // Quark (quark) — crabs, foxhounds, wraiths
    private static final String MOD_QUARK = "quark";

    // Twilight Forest (twilightforest) — large variety of AI-heavy mobs
    private static final String MOD_TWILIGHT_FOREST = "twilightforest";

    // Familiar Fauna (familiarfauna) — adds many ambient critters
    private static final String MOD_FAMILIAR_FAUNA = "familiarfauna";

    // Untamed Wilds (untamedwilds)
    private static final String MOD_UNTAMED_WILDS = "untamedwilds";

    // Environmental (environmental) — adds deer, ducks, rabbits, etc.
    private static final String MOD_ENVIRONMENTAL = "environmental";

    // Upgrade Aquatic (upgrade_aquatic) — adds many aquatic mobs
    private static final String MOD_UPGRADE_AQUATIC = "upgrade_aquatic";

    // -----------------------------------------------------------------------
    // Boss / blacklist path fragments — entities whose registry name path
    // contains any of these strings are NEVER tick-skipped.
    // -----------------------------------------------------------------------
    private static final String[] BOSS_PATH_FRAGMENTS = {
        // Ice and Fire bosses
        "dragon",
        "sea_serpent",
        "hydra",
        // Mowzie's bosses
        "ferrous_wroughtnaut",
        "umvuthi",
        "barako",
        "naga",           // Twilight Forest
        "lich",           // Twilight Forest
        "hydra",
        "ur_ghast",
        "snow_queen",
        "phantom_knight",
        // Generic boss markers
        "boss",
        "king",
        "queen",
        "lord",
        "warlord",
        "overlord",
        "guardian_elder",
        "elder_guardian",
    };

    // Path fragments that identify modded mobs with complex AI (but not bosses)
    private static final String[] MODDED_COMPLEX_PATH_FRAGMENTS = {
        // Ice and Fire
        "hippogryph",
        "myrmex",
        "stymphalian_bird",
        "cockatrice",
        "cyclops",
        "pixie",
        // Mowzie's non-boss but still complex
        "grottol",
        "frostmaw",
        "sculptor",
        // Quark
        "crab",
        "foxhound",
        "wraith",
        // Twilight Forest non-boss
        "yeti",
        "minoshroom",
        "knight_phantom",
        "carminite",
        "minotaur",
        "deer",           // TF and Environmental
    };

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        // Guard: require at least the chunk optimisation flag to be on
        if (!ServerConfig.ENABLE_CHUNK_LOAD_OPTIMIZATION.get()) {
            return;
        }

        LivingEntity entity = event.getEntityLiving();

        // Never skip players
        if (entity instanceof PlayerEntity) {
            return;
        }

        // Server side only
        if (entity.level.isClientSide) {
            return;
        }

        // ---------------------------------------------------------------
        // Classify entity type for skip decisions
        // ---------------------------------------------------------------
        EntityCategory category = classifyEntity(entity);

        // BLACKLIST: bosses and special mobs always tick at full rate
        if (category == EntityCategory.BOSS) {
            return;
        }

        int chunkX = ((int) entity.getX()) >> 4;
        int chunkZ = ((int) entity.getZ()) >> 4;
        boolean distantChunk = TickRateManager.isDistantChunk(chunkX, chunkZ);

        boolean isPassive  = category == EntityCategory.PASSIVE;
        boolean isHostile  = category == EntityCategory.HOSTILE || category == EntityCategory.MODDED_COMPLEX;

        // ---------------------------------------------------------------
        // Determine skip interval
        // ---------------------------------------------------------------
        int interval = computeInterval(entity, category, distantChunk, isPassive, isHostile);

        if (interval > 1 && entity.tickCount % interval != 0) {
            event.setCanceled(true);
        }
    }

    // -----------------------------------------------------------------------
    // Interval computation
    // -----------------------------------------------------------------------

    private static int computeInterval(LivingEntity entity, EntityCategory category,
                                       boolean distantChunk, boolean isPassive, boolean isHostile) {

        int interval;
        AdaptiveThrottler.ThrottleLevel throttle = AdaptiveThrottler.getCurrentLevel();

        if (distantChunk) {
            // ---- DISTANT CHUNK ----
            int baseDistant = AdaptiveThrottler.getEffectiveDistantTickInterval();

            switch (category) {
                case PASSIVE:
                    // Passive mobs in distant chunks: very infrequent ticks
                    interval = baseDistant * getPassiveDistantMultiplier();
                    break;
                case MODDED_COMPLEX:
                    // Complex modded AI in distant chunks: slower than hostile but tracked
                    interval = baseDistant * 2;
                    break;
                case HOSTILE:
                    // Hostile mobs in distant chunks: base adaptive interval
                    interval = baseDistant;
                    break;
                default:
                    // Neutral/misc mobs in distant chunks
                    interval = baseDistant * 2;
                    break;
            }

            // Additional scaling under throttle for distant
            if (throttle == AdaptiveThrottler.ThrottleLevel.SEVERE) {
                if (isPassive) interval = Math.max(interval, 20);
                else interval = Math.max(interval, 8);
            } else if (throttle == AdaptiveThrottler.ThrottleLevel.MODERATE) {
                if (isPassive) interval = Math.max(interval, 12);
                else if (!isHostile) interval = Math.max(interval, 6);
            }

        } else {
            // ---- NEAR-PLAYER CHUNK ----
            if (!ServerConfig.ENTITY_TICK_SKIP_ENABLED.get()) {
                return 1; // Tick skipping disabled
            }

            switch (category) {
                case PASSIVE: {
                    // Measure actual block distance to nearest player for fine-grained control
                    double nearestDistSq = nearestPlayerDistanceSq(entity);

                    if (nearestDistSq > PASSIVE_NEAR_DIST_SQ) {
                        // Passive mob is in a near chunk but still > 32 blocks from any player
                        // Skip aggressively — 4x the normal near-player passive interval
                        interval = ServerConfig.ENTITY_TICK_SKIP_PASSIVE_NEAR_INTERVAL.get() * 4;
                    } else {
                        // Within 32 blocks: normal passive near-player interval
                        interval = ServerConfig.ENTITY_TICK_SKIP_PASSIVE_NEAR_INTERVAL.get();
                    }

                    // Emergency: if TPS is critically low, double the skip interval
                    if (TPSMonitor.getCurrentTPS() < ServerConfig.ENTITY_TICK_SKIP_TPS_THRESHOLD.get()) {
                        interval *= 2;
                    }
                    break;
                }

                case MODDED_COMPLEX: {
                    // Modded mobs with expensive AI: reduce tick rate based on distance,
                    // even when in a near-player chunk.
                    double nearestDistSq = nearestPlayerDistanceSq(entity);

                    if (nearestDistSq > MODDED_COMPLEX_NEAR_DIST_SQ) {
                        // Far-ish from all players inside the near chunk boundary
                        interval = 6; // ~3.3 TPS equivalent
                    } else if (nearestDistSq > PASSIVE_NEAR_DIST_SQ) {
                        // 32–48 blocks: moderate reduction
                        interval = 4; // ~5 TPS
                    } else {
                        // Within 32 blocks: light reduction — still responsive
                        interval = 2; // ~10 TPS
                    }

                    // Back off harder under throttle
                    if (throttle == AdaptiveThrottler.ThrottleLevel.SEVERE) {
                        interval = Math.max(interval, 8);
                    } else if (throttle == AdaptiveThrottler.ThrottleLevel.MODERATE) {
                        interval = Math.max(interval, 4);
                    }
                    break;
                }

                case HOSTILE:
                    // Hostile mobs near players: only skip under throttle
                    if (throttle == AdaptiveThrottler.ThrottleLevel.SEVERE) {
                        interval = 3;
                    } else if (throttle == AdaptiveThrottler.ThrottleLevel.MODERATE) {
                        interval = 2;
                    } else {
                        interval = 1; // Full rate
                    }
                    break;

                default:
                    // Neutral/misc near player: skip lightly
                    interval = (throttle == AdaptiveThrottler.ThrottleLevel.SEVERE) ? 4 : 1;
                    break;
            }
        }

        return Math.max(1, interval);
    }

    // -----------------------------------------------------------------------
    // Entity classification
    // -----------------------------------------------------------------------

    private enum EntityCategory {
        BOSS,           // Never skip
        HOSTILE,        // Standard hostile mob
        MODDED_COMPLEX, // Modded mob with complex/expensive AI
        PASSIVE,        // Passive/ambient vanilla or low-complexity modded mob
        NEUTRAL,        // Neutral or misc
    }

    // Cache EntityType -> EntityCategory to avoid per-tick registry lookups and String allocations.
    // EntityType objects are singletons (one per entity type), so this is safe and bounded.
    private static final java.util.concurrent.ConcurrentHashMap<net.minecraft.entity.EntityType<?>, EntityCategory>
            TYPE_CATEGORY_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Classifies an entity into one of our tick-skip categories.
     * Uses a per-EntityType cache to avoid repeated registry name lookups
     * and String allocations on every tick for every entity.
     */
    private static EntityCategory classifyEntity(LivingEntity entity) {
        // Fast path: check the type-level cache first
        EntityCategory cached = TYPE_CATEGORY_CACHE.get(entity.getType());
        if (cached != null) return cached;

        EntityCategory result = classifyEntityUncached(entity);
        TYPE_CATEGORY_CACHE.put(entity.getType(), result);
        return result;
    }

    /** Performs the actual classification — called once per EntityType, then cached. */
    private static EntityCategory classifyEntityUncached(LivingEntity entity) {
        String registryName = getRegistryName(entity);

        // Always check boss blacklist first regardless of mod
        if (isBoss(registryName)) {
            return EntityCategory.BOSS;
        }

        // Identify the mod namespace
        String namespace = getNamespace(registryName);
        String path = getPath(registryName);

        // Mods that contain mostly complex AI mobs
        if (MOD_ICE_AND_FIRE.equals(namespace)
                || MOD_MOWZIES.equals(namespace)
                || MOD_TWILIGHT_FOREST.equals(namespace)) {
            if (isModdedComplexPath(path)) {
                return EntityCategory.MODDED_COMPLEX;
            }
            return EntityCategory.MODDED_COMPLEX;
        }

        // Alex's Mobs — mostly large fauna; treat as modded complex
        if (MOD_ALEXS_MOBS.equals(namespace)) {
            return EntityCategory.MODDED_COMPLEX;
        }

        // Quark — specific complex mobs; rest are simpler
        if (MOD_QUARK.equals(namespace)) {
            if (isModdedComplexPath(path)) {
                return EntityCategory.MODDED_COMPLEX;
            }
            return EntityCategory.PASSIVE;
        }

        // Mods that add mostly ambient/passive fauna — treat as passive for aggressive skipping
        if (MOD_FAMILIAR_FAUNA.equals(namespace)
                || MOD_UNTAMED_WILDS.equals(namespace)
                || MOD_ENVIRONMENTAL.equals(namespace)
                || MOD_UPGRADE_AQUATIC.equals(namespace)) {
            if (entity instanceof IMob) return EntityCategory.HOSTILE;
            return EntityCategory.PASSIVE;
        }

        // Fall back to vanilla class hierarchy
        if (entity instanceof AnimalEntity || entity instanceof AmbientEntity) {
            return EntityCategory.PASSIVE;
        }
        if (entity instanceof IMob) {
            return EntityCategory.HOSTILE;
        }

        return EntityCategory.NEUTRAL;
    }

    private static boolean isBoss(String registryName) {
        if (registryName == null) return false;
        String lower = registryName.toLowerCase();
        for (String frag : BOSS_PATH_FRAGMENTS) {
            if (lower.contains(frag)) return true;
        }
        return false;
    }

    private static boolean isModdedComplexPath(String path) {
        if (path == null) return false;
        for (String frag : MODDED_COMPLEX_PATH_FRAGMENTS) {
            if (path.contains(frag)) return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Player proximity helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the squared block distance to the nearest player in the same world.
     * Returns Double.MAX_VALUE if there are no players (should not happen in practice
     * since entity is in a loaded chunk, but safe guard is warranted).
     */
    private static double nearestPlayerDistanceSq(LivingEntity entity) {
        if (!(entity.level instanceof ServerWorld)) return Double.MAX_VALUE;
        ServerWorld world = (ServerWorld) entity.level;
        List<ServerPlayerEntity> players = world.players();
        if (players.isEmpty()) return Double.MAX_VALUE;

        double minSq = Double.MAX_VALUE;
        double ex = entity.getX(), ey = entity.getY(), ez = entity.getZ();
        for (ServerPlayerEntity player : players) {
            double dx = player.getX() - ex;
            double dy = player.getY() - ey;
            double dz = player.getZ() - ez;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq < minSq) minSq = sq;
        }
        return minSq;
    }

    // -----------------------------------------------------------------------
    // Registry name utilities
    // -----------------------------------------------------------------------

    private static String getRegistryName(LivingEntity entity) {
        net.minecraft.util.ResourceLocation loc =
                net.minecraftforge.registries.ForgeRegistries.ENTITIES.getKey(entity.getType());
        return loc != null ? loc.toString() : "";
    }

    private static String getNamespace(String registryName) {
        if (registryName == null || registryName.isEmpty()) return "";
        int colon = registryName.indexOf(':');
        return colon >= 0 ? registryName.substring(0, colon) : registryName;
    }

    private static String getPath(String registryName) {
        if (registryName == null || registryName.isEmpty()) return "";
        int colon = registryName.indexOf(':');
        return colon >= 0 ? registryName.substring(colon + 1) : "";
    }

    // -----------------------------------------------------------------------
    // Passive distant multiplier (unchanged from original)
    // -----------------------------------------------------------------------

    /**
     * Returns the multiplier applied to passive mobs in distant chunks.
     * Under throttling the base AdaptiveThrottler interval is already high;
     * the multiplier is kept moderate to avoid animals freezing completely.
     */
    private static int getPassiveDistantMultiplier() {
        if (!ServerConfig.ENTITY_TICK_SKIP_ENABLED.get()) return 3; // legacy behaviour
        int base = ServerConfig.ENTITY_TICK_SKIP_PASSIVE_DISTANT_INTERVAL.get();
        int near = ServerConfig.ENTITY_TICK_SKIP_PASSIVE_NEAR_INTERVAL.get();
        // distant = near * ratio so both values are honoured; at least 2x the near interval
        return Math.max(2, base / Math.max(1, near));
    }
}
