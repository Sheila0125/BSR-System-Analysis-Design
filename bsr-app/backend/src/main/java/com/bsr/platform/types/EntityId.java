package com.bsr.platform.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record EntityId(long value) {

    public EntityId {
        if (value <= 0) {
            throw new IllegalArgumentException("Entity ID must be positive");
        }
    }

    @JsonCreator
    public static EntityId parse(String value) {
        return new EntityId(Long.parseLong(value));
    }

    @JsonValue
    public String asString() {
        return Long.toString(value);
    }
}
