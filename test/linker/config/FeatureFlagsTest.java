package linker.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void testIsNewUiEnabledExceptionHandling() {
        LDClient client = mock(LDClient.class);

        when(client.boolVariation(anyString(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("LaunchDarkly failure"));

        FeatureFlags ff = new FeatureFlags(client);

        assertFalse(ff.isNewUiEnabled());
    }

    
}
