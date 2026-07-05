package linker.config;

import com.launchdarkly.sdk.server.LDClient;

public class FeatureFlags {
    private final LDClient ldClient;

    public FeatureFlags() {
        this.ldClient = null;
    }

    public FeatureFlags(LDClient ldClient) {
        this.ldClient = ldClient;
    }

    public boolean isNewUiEnabled() {
        return false;
    }
}