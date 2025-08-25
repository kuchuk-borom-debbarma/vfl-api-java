package dev.kuku.vfl.internal.dto;

import dev.kuku.vfl.internal.models.Block;

public class PublishContext {
    public final Block publishedBLock;

    public PublishContext(Block publishedBLock) {
        this.publishedBLock = publishedBLock;
    }
}
