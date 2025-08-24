package dev.kuku.vfl.internal.models;


import java.time.Instant;

public class Block {
    private final String id;
    private final String name;
    private final long createdTime;
//    private final Long returnedTime;
//    private final Long enteredTime;
//    private final Long exitedTime;

    public Block(String id, String name) {
        this.id = id;
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
