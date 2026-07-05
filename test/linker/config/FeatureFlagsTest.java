package linker.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;

class FeatureFlagsTest {

    @Test
    void testDefaultConstructor() {
        FeatureFlags ff = new FeatureFlags();
        assertFalse(ff.isNewUiEnabled());
    }

    @Test
    void testClientConstructorWithNull() {
        FeatureFlags ff = new FeatureFlags(null);
        assertFalse(ff.isNewUiEnabled());
    }

    @Test
    void testIsNewUiEnabledReturnsFalseByDefault() throws Exception {
        LDConfig config = new LDConfig.Builder().offline(true).build();
        try (LDClient client = new LDClient("sdk-dummy", config)) {
            FeatureFlags ff = new FeatureFlags(client);
            assertFalse(ff.isNewUiEnabled());
        }
    }

    @Test
    void testIsNewUiEnabledExceptionHandling() throws Exception {
        LDConfig config = new LDConfig.Builder().offline(true).build();
        LDClient client = new LDClient("sdk-dummy", config);
        FeatureFlags ff = new FeatureFlags(client);
        
        // Close the client so calls to it throw a LogicallyClosedException
        client.close();
        
        assertFalse(ff.isNewUiEnabled());
    }
}
