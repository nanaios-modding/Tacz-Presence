package com.tacz.presence;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class PresenceConfig {
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        final Pair<Client, ForgeConfigSpec> clientSpecPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT_SPEC = clientSpecPair.getRight();
        CLIENT = clientSpecPair.getLeft();

        final Pair<Server, ForgeConfigSpec> serverSpecPair = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER_SPEC = serverSpecPair.getRight();
        SERVER = serverSpecPair.getLeft();
    }

    public enum Mode {
        ALL, GUNS_ONLY, OFF
    }

    public enum HudType {
        TACZ_HUD, // Original TACZ HUD
        TACZ_P_HUD // Custom immersive HUD from this mod
    }

    // Static accessors for convenience (matching reference code style)
    public static ForgeConfigSpec.DoubleValue VIGNETTE_DECAY_RATE;
    public static ForgeConfigSpec.BooleanValue ENABLE_SUPER_HIT_SOUND;
    public static ForgeConfigSpec.BooleanValue ENABLE_HIT_SOUND;
    public static ForgeConfigSpec.DoubleValue MAX_SHAKE;
    public static ForgeConfigSpec.DoubleValue SHAKE_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue PULSE_SHAKE_MULTIPLIER;
    public static ForgeConfigSpec.EnumValue<Mode> DAMAGE_OVERLAY_MODE;
    public static ForgeConfigSpec.DoubleValue VIGNETTE_MAX_ALPHA;

    // Gun HUD Config
    public static ForgeConfigSpec.EnumValue<HudType> GUN_HUD_TYPE;

    public static ForgeConfigSpec.BooleanValue ENABLE_DAMAGE_INDICATOR;
    public static ForgeConfigSpec.DoubleValue DAMAGE_INDICATOR_RADIUS;
    public static ForgeConfigSpec.DoubleValue DAMAGE_INDICATOR_OPACITY;
    public static ForgeConfigSpec.BooleanValue HEARTBEAT_AUDIO_ENABLED;

    public static ForgeConfigSpec.BooleanValue ENABLE_LOW_HEALTH_ENTRY_SOUND;
    public static ForgeConfigSpec.BooleanValue ENABLE_LOW_HEALTH_EXIT_SOUND;

    // HUD Alert Configs
    public static ForgeConfigSpec.IntValue HUD_ALERT_Y_OFFSET;
    public static ForgeConfigSpec.DoubleValue HUD_ALERT_SCALE;

    public static ForgeConfigSpec.BooleanValue HUD_NO_AMMO_ENABLED;
    public static ForgeConfigSpec.ConfigValue<String> HUD_NO_AMMO_COLOR;

    public static ForgeConfigSpec.BooleanValue HUD_RELOAD_ENABLED;
    public static ForgeConfigSpec.ConfigValue<String> HUD_RELOAD_COLOR;

    public static ForgeConfigSpec.BooleanValue HUD_LOW_AMMO_ENABLED;
    public static ForgeConfigSpec.ConfigValue<String> HUD_LOW_AMMO_COLOR;

    // Sniper Glare Configs
    public static ForgeConfigSpec.BooleanValue GLARE_ENABLED;
    public static ForgeConfigSpec.DoubleValue GLARE_MIN_ZOOM;
    public static ForgeConfigSpec.DoubleValue GLARE_MIN_DISTANCE;
    public static ForgeConfigSpec.DoubleValue GLARE_BASE_SIZE;
    public static ForgeConfigSpec.DoubleValue GLARE_SCALE_CLOSE;
    public static ForgeConfigSpec.DoubleValue GLARE_SCALE_MID;
    public static ForgeConfigSpec.DoubleValue GLARE_SCALE_FAR;
    public static ForgeConfigSpec.DoubleValue GLARE_VIEW_ANGLE;
    // Weather-based opacity configs
    public static ForgeConfigSpec.DoubleValue GLARE_OPACITY_DAY;
    public static ForgeConfigSpec.DoubleValue GLARE_OPACITY_NIGHT;
    public static ForgeConfigSpec.DoubleValue GLARE_OPACITY_RAIN;
    public static ForgeConfigSpec.DoubleValue GLARE_OPACITY_NIGHT_RAIN;

    public static class Client {
        public Client(ForgeConfigSpec.Builder builder) {
            builder.push("screen_shake");
            builder.translation("config.tacz_presence.screen_shake");

            builder.comment("Camera shake intensity multiplier (0.0-5.0)");
            builder.translation("config.tacz_presence.shake_multiplier");
            SHAKE_MULTIPLIER = builder.defineInRange("ShakeMultiplier", 0.2D, 0.0D, 5.0D);

            builder.comment("Max camera shake intensity");
            builder.translation("config.tacz_presence.max_shake");
            MAX_SHAKE = builder.defineInRange("MaxShake", 15.0D, 0.0D, 100.0D);

            builder.comment("Low health pulse shake multiplier");
            builder.translation("config.tacz_presence.pulse_shake_multiplier");
            PULSE_SHAKE_MULTIPLIER = builder.defineInRange("PulseShakeMultiplier", 25.0D, 0.0D, 100.0D);
            builder.pop();

            builder.push("visual_effects");
            builder.translation("config.tacz_presence.visual_effects");

            builder.comment("Damage overlay mode: ALL, GUNS_ONLY, OFF");
            builder.translation("config.tacz_presence.damage_overlay_mode");
            DAMAGE_OVERLAY_MODE = builder.defineEnum("DamageOverlayMode", Mode.ALL);

            builder.comment("Max vignette opacity (0.0-1.0)");
            builder.translation("config.tacz_presence.vignette_max_alpha");
            VIGNETTE_MAX_ALPHA = builder.defineInRange("VignetteMaxAlpha", 1.0D, 0.0D, 1.0D);

            builder.comment("Vignette fade rate per tick");
            builder.translation("config.tacz_presence.vignette_decay_rate");
            VIGNETTE_DECAY_RATE = builder.defineInRange("VignetteDecayRate", 0.005D, 0.001D, 0.1D);

            builder.pop();

            builder.push("hud");
            builder.translation("config.tacz_presence.hud");

            builder.comment("Gun HUD style: TACZ_HUD (original), TACZ_P_HUD (custom immersive)");
            builder.translation("config.tacz_presence.gun_hud_type");
            GUN_HUD_TYPE = builder.defineEnum("GunHudType", HudType.TACZ_P_HUD);

            builder.comment("Enable damage direction indicator");
            builder.translation("config.tacz_presence.enable_damage_indicator");
            ENABLE_DAMAGE_INDICATOR = builder.define("EnableDamageIndicator", true);

            builder.comment("Indicator distance from crosshair (0.0-1.0)");
            builder.translation("config.tacz_presence.damage_indicator_radius");
            DAMAGE_INDICATOR_RADIUS = builder.defineInRange("DamageIndicatorRadius", 0.25, 0.05, 0.5);

            builder.comment("Indicator base opacity (0.0-1.0)");
            builder.translation("config.tacz_presence.damage_indicator_opacity");
            DAMAGE_INDICATOR_OPACITY = builder.defineInRange("DamageIndicatorOpacity", 1.0, 0.0, 1.0);
            builder.pop();

            builder.push("hud_alerts");
            builder.translation("config.tacz_presence.hud_alerts");

            builder.comment("Vertical offset for HUD alerts from center");
            builder.translation("config.tacz_presence.hud_alert_y_offset");
            HUD_ALERT_Y_OFFSET = builder.defineInRange("AlertYOffset", 60, -500, 500);

            builder.comment("Scale of HUD alert text");
            builder.translation("config.tacz_presence.hud_alert_scale");
            HUD_ALERT_SCALE = builder.defineInRange("AlertScale", 1.2, 0.1, 5.0);

            builder.comment("Show 'NO AMMO' alert");
            builder.translation("config.tacz_presence.hud_no_ammo_enabled");
            HUD_NO_AMMO_ENABLED = builder.define("NoAmmoEnabled", true);
            builder.comment("Color for 'NO AMMO' (Hex format: #RRGGBB)");
            builder.translation("config.tacz_presence.hud_no_ammo_color");
            HUD_NO_AMMO_COLOR = builder.define("NoAmmoColor", "#FF5555");

            builder.comment("Show 'RELOAD' alert");
            builder.translation("config.tacz_presence.hud_reload_enabled");
            HUD_RELOAD_ENABLED = builder.define("ReloadEnabled", true);
            builder.comment("Color for 'RELOAD' (Hex format: #RRGGBB)");
            builder.translation("config.tacz_presence.hud_reload_color");
            HUD_RELOAD_COLOR = builder.define("ReloadColor", "#FF5555");

            builder.comment("Show 'LOW AMMO' alert");
            builder.translation("config.tacz_presence.hud_low_ammo_enabled");
            HUD_LOW_AMMO_ENABLED = builder.define("LowAmmoEnabled", true);
            builder.comment("Color for 'LOW AMMO' (Hex format: #RRGGBB)");
            builder.translation("config.tacz_presence.hud_low_ammo_color");
            HUD_LOW_AMMO_COLOR = builder.define("LowAmmoColor", "#FFAA00");
            builder.pop();

            builder.push("sounds");
            builder.translation("config.tacz_presence.sounds");

            builder.comment("Enable standard hit sound");
            builder.translation("config.tacz_presence.enable_hit_sound");
            ENABLE_HIT_SOUND = builder.define("EnableHitSound", true);

            builder.comment("Enable super hit sound (high damage)");
            builder.translation("config.tacz_presence.enable_super_hit_sound");
            ENABLE_SUPER_HIT_SOUND = builder.define("EnableSuperHitSound", true);

            builder.comment("Enable low health heartbeat sound");
            builder.translation("config.tacz_presence.heartbeat_audio_enabled");
            HEARTBEAT_AUDIO_ENABLED = builder.define("EnableLowHealthHeartbeat", true);

            builder.comment("Enable low health entry sound");
            builder.translation("config.tacz_presence.enable_low_health_entry_sound");
            ENABLE_LOW_HEALTH_ENTRY_SOUND = builder.define("EnableLowHealthEntrySound", true);

            builder.comment("Enable low health exit sound");
            builder.translation("config.tacz_presence.enable_low_health_exit_sound");
            ENABLE_LOW_HEALTH_EXIT_SOUND = builder.define("EnableLowHealthExitSound", true);
            builder.pop();
        }
    }

    public static class Server {
        public Server(ForgeConfigSpec.Builder builder) {
            // Sniper Glare Configuration
            builder.push("sniper_glare");
            builder.translation("config.tacz_presence.sniper_glare");

            builder.comment("Enable sniper glare effect visible to other players");
            builder.translation("config.tacz_presence.glare_enabled");
            GLARE_ENABLED = builder.define("EnableSniperGlare", true);

            builder.comment("Minimum scope zoom level to be considered a sniper scope (e.g., 4.0 for 4x zoom)");
            builder.translation("config.tacz_presence.glare_min_zoom");
            GLARE_MIN_ZOOM = builder.defineInRange("MinZoomLevel", 4.0D, 1.0D, 20.0D);

            builder.comment("Minimum distance (blocks) to see the glare");
            builder.translation("config.tacz_presence.glare_min_distance");
            GLARE_MIN_DISTANCE = builder.defineInRange("MinDistance", 5.0D, 1.0D, 100.0D);

            builder.comment("Base glare size in blocks");
            builder.translation("config.tacz_presence.glare_base_size");
            GLARE_BASE_SIZE = builder.defineInRange("BaseSize", 3.0D, 0.5D, 20.0D);

            builder.comment("Size scale when close (at minimum distance)");
            builder.translation("config.tacz_presence.glare_scale_close");
            GLARE_SCALE_CLOSE = builder.defineInRange("ScaleClose", 0.5D, 0.1D, 5.0D);

            builder.comment("Size scale at medium distance (50 blocks)");
            builder.translation("config.tacz_presence.glare_scale_mid");
            GLARE_SCALE_MID = builder.defineInRange("ScaleMid", 1.0D, 0.1D, 5.0D);

            builder.comment("Size scale when far (100+ blocks)");
            builder.translation("config.tacz_presence.glare_scale_far");
            GLARE_SCALE_FAR = builder.defineInRange("ScaleFar", 1.6D, 0.1D, 10.0D);

            builder.comment("Viewing angle (degrees) from sniper's aim direction to see glare");
            builder.translation("config.tacz_presence.glare_view_angle");
            GLARE_VIEW_ANGLE = builder.defineInRange("ViewAngle", 45.0D, 10.0D, 180.0D);

            builder.comment("Glare opacity during clear day (0.0-1.0)");
            GLARE_OPACITY_DAY = builder.defineInRange("OpacityDay", 1.0D, 0.0D, 1.0D);

            builder.comment("Glare opacity during clear night (0.0-1.0)");
            GLARE_OPACITY_NIGHT = builder.defineInRange("OpacityNight", 0.8D, 0.0D, 1.0D);

            builder.comment("Glare opacity during rain (0.0-1.0)");
            GLARE_OPACITY_RAIN = builder.defineInRange("OpacityRain", 0.65D, 0.0D, 1.0D);

            builder.comment("Glare opacity during night + rain (0.0-1.0)");
            GLARE_OPACITY_NIGHT_RAIN = builder.defineInRange("OpacityNightRain", 0.5D, 0.0D, 1.0D);

            builder.pop();
        }
    }
}
