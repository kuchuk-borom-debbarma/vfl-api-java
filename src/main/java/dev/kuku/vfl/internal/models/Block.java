package dev.kuku.vfl.internal.models;


import io.github.robsonkades.uuidv7.UUIDv7;

import java.time.Instant;

public class Block {
    private final String id;
    private final String name;
    private final long createdTime;
//    private final Long returnedTime;
//    private final Long enteredTime;
//    private final Long exitedTime;

    public Block(String name) {
        this.id = UUIDv7.randomUUID().toString();
        this.name = name;
        this.createdTime = Instant.now().toEpochMilli();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getCreatedTime() {
        return createdTime;
    }
}
