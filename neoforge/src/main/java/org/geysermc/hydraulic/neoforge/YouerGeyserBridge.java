package org.geysermc.hydraulic.neoforge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.geysermc.hydraulic.HydraulicImpl;
import org.geysermc.hydraulic.pack.PackListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bridges Hydraulic (NeoForge mod) to the plugin-based Geyser (Geyser-Spigot)
 * running on hybrid servers like Youer, where the Geyser API classes are not
 * visible from the mod classloader.
 *
 * 1. Converts all mod packs directly into Geyser's pack directory.
 * 2. Registers custom (non-vanilla) blocks via the plugin-side
 *    GeyserDefineCustomBlocksEvent through reflection.
 */
public class YouerGeyserBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("Hydraulic/YouerBridge");

    private static volatile boolean customBlocksRegistered = false;

    /**
     * Attempts to locate the plugin-based Geyser on the server (Youer/Paper-like
     * environments). Runs the conversion after a short delay so the Geyser plugin
     * has finished its own startup, then reloads Geyser.
     */
    public static void tryBridge(HydraulicImpl hydraulic) {
        Thread bridgeThread = new Thread(() -> {
            try {
                // Poll until the Geyser-Spigot plugin appears (it must be bridged BEFORE
                // Geyser fires GeyserDefineCustomBlocksEvent during its own startup)
                boolean done = false;
                for (int i = 0; i < 1200 && !done; i++) {
                    Thread.sleep(100);
                    done = bridge(hydraulic);
                }
                if (!done) {
                    LOGGER.info("Geyser-Spigot plugin never appeared - Youer bridge not active");
                }
            } catch (Throwable t) {
                LOGGER.warn("Youer Geyser bridge failed", t);
            }
        }, "Hydraulic Youer Bridge");
        bridgeThread.setDaemon(true);
        bridgeThread.start();
    }

    private static boolean bridge(HydraulicImpl hydraulic) {
        try {
            Class<?> bukkitClass;
            try {
                bukkitClass = Class.forName("org.bukkit.Bukkit");
            } catch (ClassNotFoundException e) {
                return false; // not a hybrid server
            }
            Object pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
            Object plugin = pluginManager.getClass().getMethod("getPlugin", String.class).invoke(pluginManager, "Geyser-Spigot");
            if (plugin == null) {
                return false; // not loaded yet
            }

            ClassLoader pluginLoader = plugin.getClass().getClassLoader();

            // Register custom blocks on the plugin-side event bus
            registerCustomBlocks(pluginLoader);

            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi", true, pluginLoader);
            Object api = apiClass.getMethod("api").invoke(null);
            Object packDirectory = apiClass.getMethod("packDirectory").invoke(api);
            Path packsPath = (Path) packDirectory;

            LOGGER.info("Youer bridge: Geyser pack directory = {}", packsPath);
            LOGGER.info("Youer bridge: mods known to Hydraulic: {}", hydraulic.mods().stream().map(m -> m.id()).toList());

            // Convert all mod packs directly (bypasses the unreachable event bus)
            PackListener listener = hydraulic.getPackManager().packListener();
            if (listener == null) {
                return false; // pack manager not initialized yet (before ServerStarting) - retry
            }
            listener.convertAll(packsPath);

            if (hydraulic.server() == null) {
                return false; // server not ready yet - retry (reload needs the main thread)
            }
            LOGGER.info("Youer bridge: reloading Geyser to pick up packs...");
            // dispatch must run on the server main thread
            hydraulic.server().execute(() -> {
                try {
                    Object server = bukkitClass.getMethod("getServer").invoke(null);
                    Object console = bukkitClass.getMethod("getConsoleSender").invoke(null);
                    Class<?> commandSenderClass = Class.forName("org.bukkit.command.CommandSender", true, pluginLoader);
                    server.getClass().getMethod("dispatchCommand", commandSenderClass, String.class)
                        .invoke(server, console, "geyser reload");
                    LOGGER.info("Youer bridge: Geyser reload dispatched");
                } catch (Exception e) {
                    LOGGER.error("Failed to dispatch geyser reload", e);
                }
            });
            return true;
        } catch (Throwable t) {
            // Bukkit not fully initialized yet or transient failure - retry on next poll
            return false;
        }
    }

    /**
     * Registers all non-vanilla blocks (e.g. twilightforest:*) with the plugin-based
     * Geyser via its GeyserDefineCustomBlocksEvent, using reflection because the
     * plugin's API classes are not visible from the mod classloader.
     */
    private static void registerCustomBlocks(ClassLoader pluginLoader) {
        if (customBlocksRegistered) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi", true, pluginLoader);
            Object api = apiClass.getMethod("api").invoke(null);
            Object eventBus = apiClass.getMethod("eventBus").invoke(api);

            Class<?> eventClass = Class.forName("org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent", true, pluginLoader);
            Class<?> builderClass = Class.forName("org.geysermc.geyser.api.block.custom.NonVanillaCustomBlockData$Builder", true, pluginLoader);
            Class<?> dataClass = Class.forName("org.geysermc.geyser.api.block.custom.NonVanillaCustomBlockData", true, pluginLoader);
            Class<?> parentBuilderClass = Class.forName("org.geysermc.geyser.api.block.custom.CustomBlockData$Builder", true, pluginLoader);
            Class<?> registrarClass = Class.forName("org.geysermc.geyser.api.event.EventRegistrar", true, pluginLoader);

            Method subscribe = findSubscribeMethod(eventBus.getClass());
            Method register = eventClass.getMethod("register", Class.forName("org.geysermc.geyser.api.block.custom.CustomBlockData", true, pluginLoader));
            Method builderStatic = dataClass.getMethod("builder");
            Method name = parentBuilderClass.getMethod("name", String.class);
            Method namespace = builderClass.getMethod("namespace", String.class);
            Method includedInCreativeInventory = parentBuilderClass.getMethod("includedInCreativeInventory", boolean.class);
            Method build = parentBuilderClass.getMethod("build");

            // Owner for the owned event bus: EventRegistrar.of(anyObject)
            Object owner = registrarClass.getMethod("of", Object.class).invoke(null, YouerGeyserBridge.class);

            // Collect non-vanilla blocks from the NeoForge registry (visible to mods)
            List<ResourceLocation> nonVanilla = new ArrayList<>();
            for (ResourceLocation key : BuiltInRegistries.BLOCK.keySet()) {
                if (!"minecraft".equals(key.getNamespace())) {
                    nonVanilla.add(key);
                }
            }
            LOGGER.info("Youer bridge: found {} non-vanilla blocks to register", nonVanilla.size());

            Consumer<Object> consumer = event -> {
                try {
                    int registered = 0;
                    for (ResourceLocation key : nonVanilla) {
                        try {
                            Object builder = builderStatic.invoke(null);
                            name.invoke(builder, key.getPath());
                            namespace.invoke(builder, key.getNamespace());
                            includedInCreativeInventory.invoke(builder, true);
                            Object data = build.invoke(builder);
                            register.invoke(event, data);
                            registered++;
                        } catch (InvocationTargetException e) {
                            // skip blocks that Geyser rejects (e.g. duplicate name)
                        }
                    }
                    LOGGER.info("Youer bridge: registered {} custom blocks with Geyser", registered);
                } catch (Throwable t) {
                    LOGGER.warn("Youer bridge: custom block registration failed", t);
                }
            };

            subscribe.invoke(eventBus, owner, eventClass, consumer);
            customBlocksRegistered = true;
            LOGGER.info("Youer bridge: subscribed to GeyserDefineCustomBlocksEvent");
        } catch (Throwable t) {
            LOGGER.warn("Youer bridge: failed to hook custom block registration", t);
        }
    }

    private static Method findSubscribeMethod(Class<?> eventBusClass) {
        for (Method m : eventBusClass.getMethods()) {
            // OwnedEventBus.subscribe(O owner, Class<T>, Consumer<T>)
            if (m.getName().equals("subscribe") && m.getParameterCount() == 3
                && m.getParameterTypes()[1] == Class.class) {
                return m;
            }
        }
        throw new IllegalStateException("subscribe method not found on " + eventBusClass);
    }
}
