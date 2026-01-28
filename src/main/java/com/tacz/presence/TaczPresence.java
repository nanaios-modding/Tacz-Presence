package com.tacz.presence;

import com.mojang.logging.LogUtils;
import com.tacz.presence.network.PresenceNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.tacz.presence.client.DamageIndicatorOverlay;
import com.tacz.presence.client.DamageOverlay;
import com.tacz.presence.client.HitOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(TaczPresence.MODID)
public class TaczPresence {
    public static final String MODID = "tacz_presence";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TaczPresence(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.CLIENT, PresenceConfig.CLIENT_SPEC);
        context.registerConfig(ModConfig.Type.SERVER, PresenceConfig.SERVER_SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("TACZ: Combat Presence common setup");

        // Register network packets for sniper glare sync
        event.enqueueWork(PresenceNetwork::register);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("TACZ: Combat Presence client setup");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("damage_overlay", DamageOverlay.INSTANCE);
            event.registerAboveAll("hit_overlay", HitOverlay.INSTANCE);
            event.registerAboveAll("damage_indicator", DamageIndicatorOverlay.INSTANCE);
        }
    }
}
