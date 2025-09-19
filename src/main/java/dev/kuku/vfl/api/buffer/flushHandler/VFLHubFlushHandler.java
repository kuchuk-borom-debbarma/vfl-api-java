package dev.kuku.vfl.api.buffer.flushHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kuku.vfl.internal.buffer.flushHandler.VFLFlushHandler;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class VFLHubFlushHandler implements VFLFlushHandler {
    private static final Logger log = LoggerFactory.getLogger(VFLHubFlushHandler.class);
    private final String url;
    HttpClient client = HttpClient.newHttpClient();
    ObjectMapper objectMapper = new ObjectMapper();

    public VFLHubFlushHandler(String url) {
        //TODO remove extra "/" at the end if it exists
        this.url = url;
    }

    @Override
    public void flushLogs(List<BlockLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(logs)))
                    .header("Content-Type", "application/json")
                    .uri(java.net.URI.create(url + "/api/v1/logs"))
                    .version(HttpClient.Version.HTTP_2)
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException | IOException e) {
            // Only log error, do not throw
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void flushBlocks(List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(blocks)))
                    .header("Content-Type", "application/json")
                    .uri(java.net.URI.create(url + "/api/v1/blocks"))
                    .version(HttpClient.Version.HTTP_2)
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException | IOException e) {
            log.error("Failed to flush blocks with error ${e.getMessage()}");
        }
    }

    @Override
    public void flushBlockEntered(Map<String, Long> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(blockIds)))
                    .header("Content-Type", "application/json")
                    .uri(java.net.URI.create(url + "/api/v1/block-entered"))
                    .version(HttpClient.Version.HTTP_2)
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException | IOException e) {
            log.error(Arrays.toString(e.getStackTrace()));
            log.error(e.getMessage(), e);
            // Only log error, do not throw
        }
    }

    @Override
    public void flushBlockExited(Map<String, Long> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(blockIds)))
                    .header("Content-Type", "application/json")
                    .uri(java.net.URI.create(url + "/api/v1/block-exited"))
                    .version(HttpClient.Version.HTTP_2)
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException | IOException e) {
            log.error(Arrays.toString(e.getStackTrace()));
            log.error(e.getMessage(), e);
            // Only log error, do not throw
        }
    }

    @Override
    public void flushBlockReturned(Map<String, Long> blockIds) {
        if (blockIds == null || blockIds.isEmpty()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(blockIds)))
                    .header("Content-Type", "application/json")
                    .uri(java.net.URI.create(url + "/api/v1/block-returned"))
                    .version(HttpClient.Version.HTTP_2)
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException | IOException e) {
            log.error(Arrays.toString(e.getStackTrace()));
            log.error(e.getMessage(), e);
        }
    }
}
