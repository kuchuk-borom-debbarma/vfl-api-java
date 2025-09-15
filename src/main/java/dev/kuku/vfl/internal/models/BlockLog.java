package dev.kuku.vfl.internal.models;

import dev.kuku.vfl.internal.models.logType.LogTypeBase;
import dev.kuku.vfl.internal.models.logType.LogTypeTraceBlock;
import io.github.robsonkades.uuidv7.UUIDv7;

import java.time.Instant;

public class BlockLog {
    public String getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public String getReferencedBlockId() {
        return referencedBlockId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }

    private final String id;
    private final String blockId;
    private final String message;
    private final String parentLogId;
    private final String referencedBlockId;
    private final long timestamp;
    private final String type;

    public BlockLog(String message, String blockId, String parentLogId, LogTypeBase type) {
        String id = UUIDv7.randomUUID().toString();
        this.id = id;
        this.blockId = blockId;
        this.message = message;
        this.parentLogId = parentLogId;
        this.referencedBlockId = null;
        this.timestamp = Instant.now().toEpochMilli();
        this.type = type.name();
    }

    public BlockLog(String message, String blockId, String parentLogId,  String referencedBlockId, LogTypeTraceBlock type) {
        String id = UUIDv7.randomUUID().toString();
        this.blockId = blockId;
        this.id = id;
        this.parentLogId = parentLogId;
        this.message = message;
        this.referencedBlockId = referencedBlockId;
        this.timestamp = Instant.now().toEpochMilli();
        this.type = type.name();
    }
}
