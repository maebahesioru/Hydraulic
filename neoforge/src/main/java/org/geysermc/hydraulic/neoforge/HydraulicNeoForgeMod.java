package org.geysermc.hydraulic.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.geysermc.hydraulic.Constants;
import org.geysermc.hydraulic.HydraulicImpl;
import org.geysermc.hydraulic.neoforge.platform.HydraulicNeoForgeBootstrap;
import org.geysermc.hydraulic.platform.HydraulicPlatform;

@Mod(Constants.MOD_ID)
public class HydraulicNeoForgeMod {
    private final HydraulicImpl hydraulic;

    public HydraulicNeoForgeMod() {
        System.out.println("[HydraulicDebug] HydraulicNeoForgeMod constructor start");
        this.hydraulic = HydraulicImpl.load(HydraulicPlatform.NEOFORGE, new HydraulicNeoForgeBootstrap());
        System.out.println("[HydraulicDebug] HydraulicImpl loaded, registering ServerStartingEvent listener");

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        System.out.println("[HydraulicDebug] listener registered");

        // Start the Youer bridge as early as possible (plugin loads before server events on hybrid servers)
        YouerGeyserBridge.tryBridge(this.hydraulic);
    }

    private void onServerStarting(ServerStartingEvent event) {
        System.out.println("[HydraulicDebug] onServerStarting fired, calling hydraulic.onServerStarting");
        this.hydraulic.onServerStarting(event.getServer());
        System.out.println("[HydraulicDebug] onServerStarting done");
    }
}
