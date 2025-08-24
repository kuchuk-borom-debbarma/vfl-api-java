package dev.kuku.vfl.internal.models;

import dev.kuku.vfl.internal.models.logType.LogTypeBase;
import dev.kuku.vfl.internal.models.logType.LogTypeReferencedBlock;
import io.github.robsonkades.uuidv7.UUIDv7;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Instant;

public class BlockLog {
    public String getId() {
        return id;
    }

    public @Nullable String getMessage() {
        return message;
    }

    public @Nullable String getReferencedBlockId() {
        return referencedBlockId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }

    private final String id;

    private final @Nullable String message;
    private final @Nullable String parentLogId;
    private final @Nullable String referencedBlockId;
    private final long timestamp;
    private final String type;

    public BlockLog(@Nullable String message, @Nullable String parentLogId, LogTypeBase type) {
        String id = UUIDv7.randomUUID().toString();
        this.id = id;
        this.message = message;
        this.parentLogId = parentLogId;
        this.referencedBlockId = null;
        this.timestamp = Instant.now().toEpochMilli();
        this.type = type.name();
    }

    public BlockLog(@Nullable String message, @Nullable String parentLogId, String referencedBlockId, LogTypeReferencedBlock type) {
        String id = UUIDv7.randomUUID().toString();
        this.id = id;
        this.parentLogId = parentLogId;
        this.message = message;
        this.referencedBlockId = referencedBlockId;
        this.timestamp = Instant.now().toEpochMilli();
        this.type = type.name();
    }
}
