package com.sadna.group13a.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dedicated ObjectMapper for serializing domain aggregates to/from the JSON
 * blob column used by JPA repository implementations. Kept separate from
 * Spring's default ObjectMapper (used for DTO/API serialization) because it
 * reads/writes every field directly — domain aggregates have private final
 * fields and no no-arg constructors, so getter/setter-based serialization
 * doesn't round-trip them.
 */
@Configuration
public class PersistenceConfig {

    @Bean("domainObjectMapper")
    public ObjectMapper domainObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        // ALL=NONE above also disables CREATOR auto-detection, which breaks records
        // (e.g. PendingNotification) and any class relying on Jackson's automatic
        // canonical/implicit-constructor detection rather than an explicit @JsonCreator.
        mapper.setVisibility(PropertyAccessor.CREATOR, Visibility.ANY);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
