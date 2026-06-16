package com.sadna.group13a.infrastructure.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sadna.group13a.domain.shared.PurchasePolicy;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter that serializes a PurchasePolicy composite tree to/from a
 * JSON string stored in a single TEXT column.
 *
 * This converter is only invoked when JPA actually persists or loads a row — it is
 * never called during in-memory unit tests, so in-memory lambda policies remain valid.
 *
 * Jackson resolves the concrete implementation via the @JsonTypeInfo / @JsonSubTypes
 * annotations declared on the PurchasePolicy interface.
 */
@Converter
public class PurchasePolicyConverter implements AttributeConverter<PurchasePolicy, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public String convertToDatabaseColumn(PurchasePolicy attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize PurchasePolicy to JSON", e);
        }
    }

    @Override
    public PurchasePolicy convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return MAPPER.readValue(dbData, PurchasePolicy.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize PurchasePolicy from JSON: " + dbData, e);
        }
    }
}
