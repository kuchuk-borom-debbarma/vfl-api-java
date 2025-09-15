package dev.kuku.vfl.internal.models;


import io.github.robsonkades.uuidv7.UUIDv7;

import java.time.Instant;

public class Block {
    private final String id;
    private final String parentBlockId;
    private final String name;
    private final long createdTime;

    public Block(String name, String parentBlockId) {
        this.id = UUIDv7.randomUUID().toString();
        this.parentBlockId = parentBlockId;
        this.name = name;
        this.createdTime = Instant.now().toEpochMilli();
    }

    public String getId() {
        return id;
    }

    public String getParentBlockId() {
        return parentBlockId;
    }

    public String getName() {
        return name;
    }

    public long getCreatedTime() {
        return createdTime;
    }
}
