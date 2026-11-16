// Greenlight 0.1.0 (LGPL-3.0), upstream 1d744b37d639433890521bde3a787bd9ddb2ae90.
package me.flashyreese.mods.greenlight.feature;

import me.flashyreese.mods.greenlight.feature.spi.GreenlightProvider;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class GreenlightRuntimeProvider implements GreenlightProvider {
    public static void resourcesReloaded() {
        ResourcePackPolicySource.resourcesReloaded();
    }

    public GreenlightRuntimeProvider() {}

    @Override
    public void registerFeature(ClientFeature<?> feature) {
        ClientFeaturePolicyManager.registerFeature(feature);
    }

    @Override
    public void registerSource(FeaturePolicySource source) {
        ClientFeaturePolicyManager.registerSource(source);
    }

    @Override
    public PolicyQuery query(ResourceLocation featureId) {
        return ClientFeaturePolicyManager.query(featureId);
    }

    @Override
    public Set<ResourceLocation> getGrantedFeatures() {
        return ClientFeaturePolicyManager.getGrantedFeatures();
    }
}
