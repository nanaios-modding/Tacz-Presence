package com.tacz.presence.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.presence.PresenceConfig;
import com.tacz.presence.TaczPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = TaczPresence.MODID)
public class PresenceHandler {
    private static float vignetteAlpha = 0.0f;
    private static float pulseAlpha = 0.0f;
    private static float shakeIntensity = 0.0f;
    private static float lastHealth = 20.0f;
    private static int lastHurtTime = 0;

    private static boolean wasDead = false;

    private static final ResourceLocation TARGET_HIT_ID = ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID,
            "target_player_hit");
    private static final ResourceLocation TARGET_SUPERHIT_ID = ResourceLocation.fromNamespaceAndPath(TaczPresence.MODID,
            "target_player_superhit");

    public static float getVignetteAlpha() {
        return vignetteAlpha + pulseAlpha;
    }

    private static void resetEffects() {
        vignetteAlpha = 0.0f;
        pulseAlpha = 0.0f;
        shakeIntensity = 0.0f;

    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity().level().isClientSide) {
            resetEffects();
        }
    }

    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        // Attempt to cancel original TACZ overlays to avoid duplication
        if (event.getOverlay().id().getNamespace().equals("tacz")) {
            String path = event.getOverlay().id().getPath();
            if (path.contains("damage") || path.contains("blood") || path.contains("indicator")) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        DamageIndicatorOverlay.tick();

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;

        // Respawn Detection
        boolean isDead = player.isDeadOrDying();
        if (wasDead && !isDead) {
            resetEffects();
        }
        wasDead = isDead;

        // Decay effects
        vignetteAlpha = Math.max(0, vignetteAlpha - PresenceConfig.VIGNETTE_DECAY_RATE.get().floatValue());
        shakeIntensity = Math.max(0, shakeIntensity - 0.6f);

        // Hit Detection
        boolean isHurt = player.hurtTime > lastHurtTime && player.hurtTime > 0;
        lastHurtTime = player.hurtTime;

        if (isHurt) {
            // Activar el Hit Overlay cuando se recibe un impacto
            HitOverlay.trigger();

            float currentHealth = player.getHealth();
            float damage = Math.max(0, lastHealth - currentHealth);

            DamageSource source = player.getLastDamageSource();

            Vec3 indicatorPos = null;

            // Priority 1: Entity
            if (source != null && source.getEntity() != null && source.getEntity() != player) {
                indicatorPos = source.getEntity().position();
            }
            // Priority 2: Source Position
            else if (source != null && source.getSourcePosition() != null) {
                indicatorPos = source.getSourcePosition();
            }
            // Priority 3: Last Attacker
            else if (player.getLastAttacker() != null) {
                indicatorPos = player.getLastAttacker().position();
            }

            if (indicatorPos != null) {
                float damageAmount = Math.max(1.0f, damage); // Ensure at least 1.0f
                DamageIndicatorOverlay.addIndicator(indicatorPos, damageAmount);
            }

            boolean isTaczDamage = source != null && source.getMsgId().equals("tacz.bullet");
            float intendedDamage = 0.0f;

            if (isTaczDamage) {
                if (source.getDirectEntity() instanceof EntityKineticBullet bullet) {
                    ResourceLocation gunId = bullet.getGunId();
                    Optional<CommonGunIndex> gunIndex = TimelessAPI.getCommonGunIndex(gunId);
                    if (gunIndex.isPresent()) {
                        intendedDamage = gunIndex.get().getGunData().getBulletData().getDamageAmount();
                    }
                }

                if (intendedDamage <= 0)
                    intendedDamage = damage > 0 ? damage : 5.0f;

                float actualDamage = damage > 0 ? damage : intendedDamage;

                if (actualDamage >= 20.0f) {
                    if (PresenceConfig.ENABLE_SUPER_HIT_SOUND.get()) {
                        player.playSound(SoundEvent.createVariableRangeEvent(TARGET_SUPERHIT_ID), 1.0f,
                                0.9f + (float) Math.random() * 0.1f);
                    }
                } else {
                    if (PresenceConfig.ENABLE_HIT_SOUND.get()) {
                        player.playSound(SoundEvent.createVariableRangeEvent(TARGET_HIT_ID), 1.0f,
                                0.9f + (float) Math.random() * 0.2f);
                    }
                }

                shakeIntensity = Math.min(PresenceConfig.MAX_SHAKE.get().floatValue(),
                        shakeIntensity + (intendedDamage * PresenceConfig.SHAKE_MULTIPLIER.get().floatValue()));
            }

            PresenceConfig.Mode mode = PresenceConfig.DAMAGE_OVERLAY_MODE.get();
            boolean showOverlay = false;

            if (mode == PresenceConfig.Mode.ALL)
                showOverlay = true;
            else if (mode == PresenceConfig.Mode.GUNS_ONLY && isTaczDamage)
                showOverlay = true;

            if (showOverlay) {
                float visualBaseDamage = intendedDamage > 0 ? intendedDamage : (damage > 0 ? damage : 6.0f);
                float intensity = 0.25f + (visualBaseDamage / 15.0f);

                if (damage <= 0) {
                    intensity *= 0.3f;
                }

                vignetteAlpha = Math.min(PresenceConfig.VIGNETTE_MAX_ALPHA.get().floatValue(),
                        vignetteAlpha + intensity);

                float healthFactor = 1.0f - (player.getHealth() / player.getMaxHealth());
                healthFactor = (float) Math.pow(healthFactor, 2.0);

                if (vignetteAlpha < healthFactor * 0.8f) {
                    vignetteAlpha = healthFactor * 0.8f;
                }
            }
        }

        if (player.isDeadOrDying()) {
            vignetteAlpha = Math.max(0, vignetteAlpha - 0.05f);
            pulseAlpha = Math.max(0, pulseAlpha - 0.05f);
            shakeIntensity = 0;
            return;
        }

        lastHealth = player.getHealth();

        // Removed Heartbeat/Low Health Sound logic as per user request.
        // We only decay pulseAlpha here.
        pulseAlpha = Math.max(0.0f, pulseAlpha - 0.01f);
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (shakeIntensity > 0) {
            float yawShake = (float) ((Math.random() - 0.5) * shakeIntensity * 0.5);
            float pitchShake = (float) ((Math.random() - 0.5) * shakeIntensity * 0.5);
            float rollShake = (float) ((Math.random() - 0.5) * shakeIntensity * 0.5);

            event.setYaw(event.getYaw() + yawShake);
            event.setPitch(event.getPitch() + pitchShake);
            event.setRoll(event.getRoll() + rollShake);
        }
    }

    public static void addShake(float amount) {
        shakeIntensity = Math.min(PresenceConfig.MAX_SHAKE.get().floatValue(), shakeIntensity + amount);
    }
}
