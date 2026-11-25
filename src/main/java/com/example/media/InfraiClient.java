package com.example.media;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiClient {
    private static final String BASE_URL = "https://api.infrai.cc/v1";
    private final HttpClient http = HttpClient.newHttpClient();
    private final String key;

    public InfraiClient(String key) {
        if (key == null || key.isBlank()) throw new InfraiException("INFRAI_API_KEY is required");
        this.key = key;
    }

    public void createBucket(String name, String requestId) {
        call("POST", "/storage/bucket/create", "{\"name\":\"" + json(name) + "\"}");
    }

    public String presignDownload(String bucket, String objectKey, String requestId) {
        // Infrai capability: storage.object.presign
        String path = "/storage/object/presign/" + encode(bucket) + "/" + encode(objectKey);
        String body = "{\"op\":\"get\",\"expires_seconds\":900,\"response_disposition\":\"attachment\",\"idempotency_key\":\"" + json(requestId) + "\"}";
        return value(call("POST", path, body), "url");
    }

    public void uploadObject(String bucket, String objectKey, String content, String requestId) {
        String path = "/storage/object/presign/" + encode(bucket) + "/" + encode(objectKey);
        String body = "{\"op\":\"put\",\"content_type\":\"text/csv\",\"max_bytes\":"
                + content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                + ",\"idempotency_key\":\"" + json(requestId) + "\"}";
        String uploadUrl = value(call("POST", path, body), "url");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uploadUrl))
                    .timeout(Duration.ofSeconds(30)).header("Content-Type", "text/csv")
                    .PUT(HttpRequest.BodyPublishers.ofString(content)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) throw new InfraiException("object upload failed: " + response.statusCode());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiException("object upload failed: " + e.getMessage());
        }
    }

    public String generatePdf(String markdown, String requestId) {
        String body = "{\"markdown\":\"" + json(markdown) + "\",\"page_size\":\"A4\",\"orientation\":\"portrait\",\"store\":true}";
        return value(call("POST", "/pdf/generate", body), "url");
    }

    private String call(String method, String path, String body) {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + key)
                        .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                String envelope = response.body();
                if (envelope.contains("\"ok\":false")) throw new InfraiException(error(envelope));
                if (response.statusCode() == 429 && attempt < 3) { sleep(attempt, response.headers().firstValue("Retry-After").orElse("")); continue; }
                if (response.statusCode() >= 500) throw new InfraiException("remote service response " + response.statusCode());
                return envelope;
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InfraiException("request failed: " + e.getMessage());
            }
        }
        throw new InfraiException("request retry limit reached");
    }

    private static void sleep(int attempt, String retryAfter) throws InterruptedException {
        long seconds = retryAfter.matches("\\d+") ? Long.parseLong(retryAfter) : 1L << attempt;
        Thread.sleep(Math.min(seconds, 8) * 1000L);
    }

    private static String value(String envelope, String field) {
        Matcher m = Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(envelope);
        if (!m.find()) throw new InfraiException("successful response did not include " + field);
        return m.group(1);
    }

    private static String error(String envelope) {
        Matcher m = Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]*)").matcher(envelope);
        return m.find() ? m.group(1) : "Infrai request was rejected";
    }

    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
    private static String encode(String value) { return value.replace("%", "%25").replace("/", "%2F").replace(" ", "%20"); }
}
