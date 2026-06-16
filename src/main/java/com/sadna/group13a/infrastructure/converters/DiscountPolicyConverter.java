package com.sadna.group13a.infrastructure.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sadna.group13a.domain.shared.DiscountPolicy;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter that serializes a DiscountPolicy composite tree to/from a
 * JSON string stored in a single TEXT column.
 *
 * Jackson resolves the concrete implementation via the @JsonTypeInfo / @JsonSubTypes
 * annotations declared on the DiscountPolicy interface.
 */
@Converter
public class DiscountPolicyConverter implements AttributeConverter<DiscountPolicy, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public String convertToDatabaseColumn(DiscountPolicy attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize DiscountPolicy to JSON", e);
        }
    }

    @Override
    public DiscountPolicy convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return MAPPER.readValue(dbData, DiscountPolicy.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize DiscountPolicy from JSON: " + dbData, e);
        }
    }
}
