package linker.config;

import com.launchdarkly.sdk.LDContext;
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
        if (ldClient == null) {
            return false;
        }
        try {
            LDContext context = LDContext.builder("anonymous-user")
                .anonymous(true)
                .build();
            return ldClient.boolVariation("new-ui", context, false);
        } catch (Exception e) {
            return false;
        }
    }
}