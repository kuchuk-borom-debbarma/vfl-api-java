package dev.kuku.vfl.internal.dto;

import dev.kuku.vfl.internal.models.Block;

public class PublishContext {
    public  Block publishedBLock;

    public PublishContext(Block publishedBLock) {
        this.publishedBLock = publishedBLock;
    }

    public PublishContext() {}

    public Block getPublishedBLock() {
        return publishedBLock;
    }

    public void setPublishedBLock(Block publishedBLock) {
        this.publishedBLock = publishedBLock;
    }
}
