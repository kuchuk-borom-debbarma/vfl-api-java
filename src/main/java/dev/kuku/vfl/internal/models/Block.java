package dev.kuku.vfl.internal.models;

import java.time.Instant;

public class Block {
    private final String name;
    private final String id;
    private final long blockCreatedAt;
    /// Should be set when the block has started to be used. For annotated sub block it should be instant as we can't predict when it starts.
    private Long blockStartedAt = null;

    public Block(String name, String id) {
        this.name = name;
        this.id = id;
        this.blockCreatedAt = Instant.now().toEpochMilli();
    }
}
