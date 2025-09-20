package dev.kuku.vfl.api.buffer.flushHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kuku.vfl.internal.buffer.flushHandler.VFLFlushHandler;
import dev.kuku.vfl.internal.models.Block;
import dev.kuku.vfl.internal.models.BlockLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class VFLHubFlushHandler implements VFLFlushHandler {
    private static final Logger log = LoggerFactory.getLogger(VFLHubFlushHandler.class);
    private final String url;
    private final HttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean throwExceptions;

    // Constructor for backward compatibility - defaults to safe mode
    public VFLHubFlushHandler(String url) {
        this(url, false);
    }

    public VFLHubFlushHandler(String url, boolean throwExceptions) {
        this.throwExceptions = throwExceptions;
        // Remove trailing slash if it exists
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;

        // Configure HttpClient with timeouts to prevent hanging
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
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
                    .timeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_2)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Log non-2xx responses
            if (response.statusCode() >= 400) {
                String errorMsg = String.format("Failed to flush logs to VFL Hub. Status: %d, Response: %s",
                        response.statusCode(), response.body());
                log.warn(errorMsg);

                if (throwExceptions) {
                    throw new RuntimeException(errorMsg);
                }
            }

        } catch (RuntimeException e) {
            // Re-throw RuntimeException if it came from the status check above
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to flush %d logs to VFL Hub: %s", logs.size(), e.getMessage());
            log.error(errorMsg);

            if (throwExceptions) {
                throw new RuntimeException(errorMsg, e);
            }
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
                    .timeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_2)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                String errorMsg = String.format("Failed to flush blocks to VFL Hub. Status: %d, Response: %s",
                        response.statusCode(), response.body());
                log.warn(errorMsg);

                if (throwExceptions) {
                    throw new RuntimeException(errorMsg);
                }
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to flush %d blocks to VFL Hub: %s", blocks.size(), e.getMessage());
            log.error(errorMsg);

            if (throwExceptions) {
                throw new RuntimeException(errorMsg, e);
            }
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
                    .timeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_2)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                String errorMsg = String.format("Failed to flush block entered events to VFL Hub. Status: %d, Response: %s",
                        response.statusCode(), response.body());
                log.warn(errorMsg);

                if (throwExceptions) {
                    throw new RuntimeException(errorMsg);
                }
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to flush %d block entered events to VFL Hub: %s", blockIds.size(), e.getMessage());
            log.error(errorMsg);

            if (throwExceptions) {
                throw new RuntimeException(errorMsg, e);
            }
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
                    .timeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_2)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                String errorMsg = String.format("Failed to flush block exited events to VFL Hub. Status: %d, Response: %s",
                        response.statusCode(), response.body());
                log.warn(errorMsg);

                if (throwExceptions) {
                    throw new RuntimeException(errorMsg);
                }
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to flush %d block exited events to VFL Hub: %s", blockIds.size(), e.getMessage());
            log.error(errorMsg);

            if (throwExceptions) {
                throw new RuntimeException(errorMsg, e);
            }
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
                    .timeout(Duration.ofSeconds(10))
                    .version(HttpClient.Version.HTTP_2)
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                String errorMsg = String.format("Failed to flush block returned events to VFL Hub. Status: %d, Response: %s",
                        response.statusCode(), response.body());
                log.warn(errorMsg);

                if (throwExceptions) {
                    throw new RuntimeException(errorMsg);
                }
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Failed to flush %d block returned events to VFL Hub: %s", blockIds.size(), e.getMessage());
            log.error(errorMsg);

            if (throwExceptions) {
                throw new RuntimeException(errorMsg, e);
            }
        }
    }
}
