package io.github.resilience4j.common.timelimiter.configuration;

import io.github.resilience4j.common.CompositeCustomizer;
import io.github.resilience4j.common.retry.configuration.RetryConfigurationProperties;
import io.github.resilience4j.core.ConfigurationNotFoundException;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TimeLimiterConfigurationPropertiesTest {

    @Test
    public void testTimeLimiterProperties() {
        // Given
        TimeLimiterConfigurationProperties.InstanceProperties instanceProperties1 = new TimeLimiterConfigurationProperties.InstanceProperties();
        instanceProperties1.setTimeoutDuration(Duration.ofSeconds(3));
        instanceProperties1.setCancelRunningFuture(true);
        instanceProperties1.setEventConsumerBufferSize(200);

        TimeLimiterConfigurationProperties.InstanceProperties instanceProperties2 = new TimeLimiterConfigurationProperties.InstanceProperties();
        instanceProperties2.setTimeoutDuration(Duration.ofSeconds(5));
        instanceProperties2.setCancelRunningFuture(false);
        instanceProperties2.setEventConsumerBufferSize(500);

        TimeLimiterConfigurationProperties timeLimiterConfigurationProperties = new TimeLimiterConfigurationProperties();
        timeLimiterConfigurationProperties.getInstances().put("backend1", instanceProperties1);
        timeLimiterConfigurationProperties.getInstances().put("backend2", instanceProperties2);
        Map<String,String> tags = new HashMap<>();
        tags.put("testKey1","testKet2");
        timeLimiterConfigurationProperties.setTags(tags);

        // Then
        assertThat(timeLimiterConfigurationProperties.getTags()).isNotEmpty();
        assertThat(timeLimiterConfigurationProperties.getInstances().size()).isEqualTo(2);
        final TimeLimiterConfig timeLimiter1 = timeLimiterConfigurationProperties.createTimeLimiterConfig("backend1");
        final TimeLimiterConfig timeLimiter2 = timeLimiterConfigurationProperties.createTimeLimiterConfig("backend2");

        TimeLimiterConfigurationProperties.InstanceProperties instancePropertiesForTimeLimiter1 = timeLimiterConfigurationProperties.getInstances().get("backend1");

        assertThat(instancePropertiesForTimeLimiter1.getTimeoutDuration().toMillis()).isEqualTo(3000);
        assertThat(timeLimiter1).isNotNull();
        assertThat(timeLimiter1.shouldCancelRunningFuture()).isEqualTo(true);

        assertThat(timeLimiter2).isNotNull();
        assertThat(timeLimiter2.getTimeoutDuration().toMillis()).isEqualTo(5000);
    }

    @Test
    public void testCreateTimeLimiterPropertiesWithSharedConfigs() {
        // Given
        TimeLimiterConfigurationProperties.InstanceProperties defaultProperties = new TimeLimiterConfigurationProperties.InstanceProperties();
        defaultProperties.setTimeoutDuration(Duration.ofSeconds(3));
        defaultProperties.setCancelRunningFuture(true);
        defaultProperties.setEventConsumerBufferSize(200);

        TimeLimiterConfigurationProperties.InstanceProperties sharedProperties = new TimeLimiterConfigurationProperties.InstanceProperties();
        sharedProperties.setTimeoutDuration(Duration.ofSeconds(5));
        sharedProperties.setCancelRunningFuture(false);
        sharedProperties.setEventConsumerBufferSize(500);

        TimeLimiterConfigurationProperties.InstanceProperties backendWithDefaultConfig = new TimeLimiterConfigurationProperties.InstanceProperties();
        backendWithDefaultConfig.setBaseConfig("defaultConfig");
        backendWithDefaultConfig.setTimeoutDuration(Duration.ofMillis(200L));

        TimeLimiterConfigurationProperties.InstanceProperties backendWithSharedConfig = new TimeLimiterConfigurationProperties.InstanceProperties();
        backendWithSharedConfig.setBaseConfig("sharedConfig");
        backendWithSharedConfig.setTimeoutDuration(Duration.ofMillis(300L));

        TimeLimiterConfigurationProperties timeLimiterConfigurationProperties = new TimeLimiterConfigurationProperties();
        timeLimiterConfigurationProperties.getConfigs().put("defaultConfig", defaultProperties);
        timeLimiterConfigurationProperties.getConfigs().put("sharedConfig", sharedProperties);

        timeLimiterConfigurationProperties.getInstances().put("backendWithDefaultConfig", backendWithDefaultConfig);
        timeLimiterConfigurationProperties.getInstances().put("backendWithSharedConfig", backendWithSharedConfig);

        Map<String,String> globalTags = new HashMap<>();
        globalTags.put("testKey1","testKet2");
        timeLimiterConfigurationProperties.setTags(globalTags);

        //Then
        assertThat(timeLimiterConfigurationProperties.getTags()).isNotEmpty();
        // Should get default config and overwrite max attempt and wait time
        TimeLimiterConfig timeLimiter1 = timeLimiterConfigurationProperties.createTimeLimiterConfig("backendWithDefaultConfig");
        assertThat(timeLimiter1).isNotNull();
        assertThat(timeLimiter1.shouldCancelRunningFuture()).isEqualTo(true);
        assertThat(timeLimiter1.getTimeoutDuration().toMillis()).isEqualTo(200);

        // Should get shared config and overwrite wait time
        TimeLimiterConfig timeLimiter2 = timeLimiterConfigurationProperties.createTimeLimiterConfig("backendWithSharedConfig");
        assertThat(timeLimiter2).isNotNull();
        assertThat(timeLimiter2.shouldCancelRunningFuture()).isEqualTo(false);
        assertThat(timeLimiter2.getTimeoutDuration().toMillis()).isEqualTo(300);

        // Unknown backend should get default config of Registry
        TimeLimiterConfig timeLimiter3 = timeLimiterConfigurationProperties.createTimeLimiterConfig("unknownBackend");
        assertThat(timeLimiter3).isNotNull();
        assertThat(timeLimiter3.getTimeoutDuration().toMillis()).isEqualTo(1000);

    }

    @Test
    public void testCreatePropertiesWithUnknownConfig() {
        TimeLimiterConfigurationProperties timeLimiterConfigurationProperties = new TimeLimiterConfigurationProperties();

        TimeLimiterConfigurationProperties.InstanceProperties instanceProperties = new TimeLimiterConfigurationProperties.InstanceProperties();
        instanceProperties.setBaseConfig("unknownConfig");
        timeLimiterConfigurationProperties.getInstances().put("backend", instanceProperties);

        //then
        assertThatThrownBy(() -> timeLimiterConfigurationProperties.createTimeLimiterConfig("backend"))
                .isInstanceOf(ConfigurationNotFoundException.class)
                .hasMessage("Configuration with name 'unknownConfig' does not exist");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentOnEventConsumerBufferSize() {
        TimeLimiterConfigurationProperties.InstanceProperties defaultProperties = new TimeLimiterConfigurationProperties.InstanceProperties();
        defaultProperties.setEventConsumerBufferSize(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentOnTimeoutDuration() {
        TimeLimiterConfigurationProperties.InstanceProperties defaultProperties = new TimeLimiterConfigurationProperties.InstanceProperties();
        defaultProperties.setTimeoutDuration(Duration.ofMillis(-1000));
    }

    @Test
    public void testCustomizeTimeLimiterConfig() {
        TimeLimiterConfigurationProperties timeLimiterConfigurationProperties = new TimeLimiterConfigurationProperties();
        TimeLimiterConfigCustomizer customizer = TimeLimiterConfigCustomizer.of("backend",
            builder -> builder.timeoutDuration(Duration.ofSeconds(10)));
        TimeLimiterConfigurationProperties.InstanceProperties instanceProperties = new TimeLimiterConfigurationProperties.InstanceProperties();
        instanceProperties.setTimeoutDuration(Duration.ofSeconds(3));
        TimeLimiterConfig config = timeLimiterConfigurationProperties.createTimeLimiterConfig("backend", instanceProperties,
            new CompositeCustomizer<>(Collections.singletonList(customizer)));

        assertThat(config).isNotNull();
        assertThat(config.getTimeoutDuration()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    public void testTimeLimiterConfigWithBaseConfig() {
        TimeLimiterConfigurationProperties.InstanceProperties defaultConfig = new TimeLimiterConfigurationProperties.InstanceProperties();
        defaultConfig.setTimeoutDuration(Duration.ofMillis(4000L));
        defaultConfig.setCancelRunningFuture(false);

        TimeLimiterConfigurationProperties.InstanceProperties sharedConfigWithDefaultConfig = new TimeLimiterConfigurationProperties.InstanceProperties();
        sharedConfigWithDefaultConfig.setCancelRunningFuture(true);
        sharedConfigWithDefaultConfig.setBaseConfig("defaultConfig");

        TimeLimiterConfigurationProperties.InstanceProperties instanceWithSharedConfig = new TimeLimiterConfigurationProperties.InstanceProperties();
        instanceWithSharedConfig.setBaseConfig("sharedConfig");


        TimeLimiterConfigurationProperties timeLimiterConfigurationProperties = new TimeLimiterConfigurationProperties();
        timeLimiterConfigurationProperties.getConfigs().put("defaultConfig", defaultConfig);
        timeLimiterConfigurationProperties.getConfigs().put("sharedConfig", sharedConfigWithDefaultConfig);
        timeLimiterConfigurationProperties.getInstances().put("instanceWithSharedConfig", instanceWithSharedConfig);


        TimeLimiterConfig instance = timeLimiterConfigurationProperties
            .createTimeLimiterConfig("instanceWithSharedConfig", instanceWithSharedConfig, compositeTimeLimiterCustomizer());
        assertThat(instance).isNotNull();
        assertThat(instance.getTimeoutDuration()).isEqualTo(Duration.ofMillis(4000L));
        assertThat(instance.shouldCancelRunningFuture()).isEqualTo(true);
    }

    @Test
    public void testTimeLimiterConfigWithDefaultConfig() {
        TimeLimiterConfigurationProperties.InstanceProperties defaultConfig = new TimeLimiterConfigurationProperties.InstanceProperties();
        defaultConfig.setTimeoutDuration(Duration.ofMillis(4000L));
        defaultConfig.setCancelRunningFuture(false);

        TimeLimiterConfigurationProperties.InstanceProperties sharedConfigWithDefaultConfig = new TimeLimiterConfigurationProperties.InstanceProperties();
        sharedConfigWithDefaultConfig.setTimeoutDuration(Duration.ofMillis(3000L));
        sharedConfigWithDefaultConfig.setCancelRunningFuture(true);

        TimeLimiterConfigurationProperties.InstanceProperties instanceWithSharedConfig = new TimeLimiterConfigurationProperties.InstanceProperties();
        instanceWithSharedConfig.setBaseConfig("sharedConfig");

        TimeLimiterConfigurationProperties.InstanceProperties instanceWithDefaultConfig = new TimeLimiterConfigurationProperties.InstanceProperties();

        TimeLimiterConfigurationProperties timeLimiterConfigurationProperties = new TimeLimiterConfigurationProperties();
        timeLimiterConfigurationProperties.getConfigs().put("default", defaultConfig);
        timeLimiterConfigurationProperties.getConfigs().put("sharedConfig", sharedConfigWithDefaultConfig);
        timeLimiterConfigurationProperties.getInstances().put("instanceWithSharedConfig", instanceWithSharedConfig);

        TimeLimiterConfig instance1 = timeLimiterConfigurationProperties
            .createTimeLimiterConfig("instanceWithSharedConfig", instanceWithSharedConfig, compositeTimeLimiterCustomizer());
        assertThat(instance1).isNotNull();
        assertThat(instance1.getTimeoutDuration()).isEqualTo(Duration.ofMillis(3000L));
        assertThat(instance1.shouldCancelRunningFuture()).isEqualTo(true);

        TimeLimiterConfig instance2 = timeLimiterConfigurationProperties
            .createTimeLimiterConfig("unknown", instanceWithDefaultConfig, compositeTimeLimiterCustomizer());
        assertThat(instance2).isNotNull();
        assertThat(instance2.getTimeoutDuration()).isEqualTo(Duration.ofMillis(4000L));
        assertThat(instance2.shouldCancelRunningFuture()).isEqualTo(false);
    }

    @Test
    public void testGetInstancePropertiesPropertiesWithoutDefaultConfig() {
        //Given
        TimeLimiterConfigurationProperties.InstanceProperties backendWithoutBaseConfig = new TimeLimiterConfigurationProperties.InstanceProperties();

        TimeLimiterConfigurationProperties timeLimiterConfigurationProperties = new TimeLimiterConfigurationProperties();
        timeLimiterConfigurationProperties.getInstances().put("backendWithoutBaseConfig", backendWithoutBaseConfig);

        //Then
        assertThat(timeLimiterConfigurationProperties.getInstances().size()).isEqualTo(1);

        // Should get defaults
        TimeLimiterConfigurationProperties.InstanceProperties timeLimiterProperties =
            timeLimiterConfigurationProperties.getInstanceProperties("backendWithoutBaseConfig");
        assertThat(timeLimiterProperties).isNotNull();
        assertThat(timeLimiterProperties.getEventConsumerBufferSize()).isNull();
    }

    @Test
    public void testGetIstancePropertiesPropertiesWithDefaultConfig() {
        //Given
        TimeLimiterConfigurationProperties.InstanceProperties defaultProperties = new TimeLimiterConfigurationProperties.InstanceProperties();
        defaultProperties.setEventConsumerBufferSize(99);

        TimeLimiterConfigurationProperties.InstanceProperties backendWithoutBaseConfig = new TimeLimiterConfigurationProperties.InstanceProperties();

        TimeLimiterConfigurationProperties timeLimiterConfigurationProperties = new TimeLimiterConfigurationProperties();
        timeLimiterConfigurationProperties.getConfigs().put("default", defaultProperties);
        timeLimiterConfigurationProperties.getInstances().put("backendWithoutBaseConfig", backendWithoutBaseConfig);

        //Then
        assertThat(timeLimiterConfigurationProperties.getInstances().size()).isEqualTo(1);

        // Should get default config and overwrite enableExponentialBackoff but not enableRandomizedWait
        TimeLimiterConfigurationProperties.InstanceProperties timeLimiterProperties =
            timeLimiterConfigurationProperties.getInstanceProperties("backendWithoutBaseConfig");
        assertThat(timeLimiterProperties).isNotNull();
        assertThat(timeLimiterProperties.getEventConsumerBufferSize()).isEqualTo(99);
    }

    private CompositeCustomizer<TimeLimiterConfigCustomizer> compositeTimeLimiterCustomizer() {
        return new CompositeCustomizer<>(Collections.emptyList());
    }

}
