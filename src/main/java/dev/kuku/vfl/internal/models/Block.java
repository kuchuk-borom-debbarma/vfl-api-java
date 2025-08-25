package dev.kuku.vfl.internal.models;


import io.github.robsonkades.uuidv7.UUIDv7;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Instant;

public class Block {
    private final String id;
    private final @Nullable String parentBlockId;
    private final String name;
    private final long createdTime;

    public Block(String name, @Nullable String parentBlockId) {
        this.id = UUIDv7.randomUUID().toString();
        this.parentBlockId = parentBlockId;
        this.name = name;
        this.createdTime = Instant.now().toEpochMilli();
    }

    public String getId() {
        return id;
    }

    public @Nullable String getParentBlockId() {
        return parentBlockId;
    }

    public String getName() {
        return name;
    }

    public long getCreatedTime() {
        return createdTime;
    }
}
