package ch.irixiagroup.ilicence;

import org.bukkit.plugin.java.JavaPlugin;

public final class ILicence {

    private ILicence() {
    }

    public static ILicence verify(JavaPlugin plugin, String licenseKey, String pluginSlug) {
        return new ILicence();
    }

    public void stop() {
        // No-op lifecycle hook for the one-line setup.
    }
}
