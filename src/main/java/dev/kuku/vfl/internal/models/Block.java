package dev.kuku.vfl.internal.models;


import io.github.robsonkades.uuidv7.UUIDv7;

import java.time.Instant;

public class Block {
    private String id;
    private String parentBlockId;
    private String name;
    private long createdAt;

    public Block(String name, String parentBlockId) {
        this.id = UUIDv7.randomUUID().toString();
        this.parentBlockId = parentBlockId;
        this.name = name;
        this.createdAt = Instant.now().toEpochMilli();
    }

    public Block() {
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

    public long getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "Block{" +
               "id='" + id + '\'' +
               ", parentBlockId='" + parentBlockId + '\'' +
               ", name='" + name + '\'' +
               ", createdTime=" + createdAt +
               '}';
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setParentBlockId(String parentBlockId) {
        this.parentBlockId = parentBlockId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
