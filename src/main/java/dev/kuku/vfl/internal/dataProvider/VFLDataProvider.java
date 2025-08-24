package dev.kuku.vfl.internal.dataProvider;

import dev.kuku.vfl.internal.buffer.VFLBuffer;
import dev.kuku.vfl.internal.dto.BlockContext;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface VFLDataProvider {
    @Nullable
    BlockContext getBlockContext();

    @Nullable
    VFLBuffer getVFLBuffer();
}
