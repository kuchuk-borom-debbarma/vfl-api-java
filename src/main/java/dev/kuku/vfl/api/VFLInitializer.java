package dev.kuku.vfl.api;

import dev.kuku.vfl.internal.dataProvider.VFLDataProvider;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
//TODO figure out another way
public class VFLInitializer {
    public static @MonotonicNonNull VFLDataProvider DATA_PROVIDER;

    public static void initialize(
            @NonNull VFLDataProvider dataProvider
    ) {
        DATA_PROVIDER = dataProvider;
    }
}
