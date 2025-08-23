package dev.kuku.vfl.internal.util;

import dev.kuku.vfl.internal.models.BlockLog;
import dev.kuku.vfl.internal.models.logType.LogTypeBase;
import dev.kuku.vfl.internal.models.logType.LogTypeReferencedBlock;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FlowUtil {

    @NonNull
    public static BlockLog CreateLogAndPushToBuffer(@Nullable String message, LogTypeBase type) {
        BlockLog log = new BlockLog(message, type);
        return log;
    }

    @NonNull
    public static BlockLog CreateLogAndPushToBuffer(@Nullable String message, @NonNull String referencedBlockId, LogTypeReferencedBlock type) {
        BlockLog log = new BlockLog(message, referencedBlockId, type);
        return log;
    }
}
