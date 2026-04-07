package dev.crown.economy.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class MultiverseWorldResolver implements WorldResolver {

    @Override
    public String getName() {
        return "Multiverse-Core";
    }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core");
    }

    @Override
    public boolean isKnownWorld(World world) {
        return world != null && isKnownWorld(world.getName());
    }

    @Override
    public boolean isKnownWorld(String worldName) {
        if (!isAvailable() || worldName == null || worldName.isBlank()) {
            return false;
        }

        return isKnownWithMv5(worldName) || isKnownWithLegacyApi(worldName);
    }

    private boolean isKnownWithMv5(String worldName) {
        try {
            Class<?> apiClass = Class.forName("org.mvplugins.multiverse.core.MultiverseCoreApi");
            Method getMethod = apiClass.getMethod("get");
            Object api = getMethod.invoke(null);
            if (api == null) {
                return false;
            }

            Method worldManagerMethod = apiClass.getMethod("getWorldManager");
            Object worldManager = worldManagerMethod.invoke(api);
            if (worldManager == null) {
                return false;
            }

            Method getWorldMethod = worldManager.getClass().getMethod("getWorld", String.class);
            Object option = getWorldMethod.invoke(worldManager, worldName);
            if (option == null) {
                return false;
            }

            Method getOrNullMethod = option.getClass().getMethod("getOrNull");
            return getOrNullMethod.invoke(option) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isKnownWithLegacyApi(String worldName) {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
            if (plugin == null) {
                return false;
            }

            Method managerMethod = plugin.getClass().getMethod("getMVWorldManager");
            Object manager = managerMethod.invoke(plugin);
            if (manager == null) {
                return false;
            }

            try {
                Method getWorldMethod = manager.getClass().getMethod("getMVWorld", String.class);
                return getWorldMethod.invoke(manager, worldName) != null;
            } catch (NoSuchMethodException ignored) {
                Method isWorldMethod = manager.getClass().getMethod("isMVWorld", String.class, boolean.class);
                Object result = isWorldMethod.invoke(manager, worldName, false);
                return result instanceof Boolean known && known;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }
}
