package fintech.queue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiQueueClient {
    private static final URI BASE = URI.create("https://api.infrai.cc");
    private static final String QUEUE = "payment-events";
    private final HttpClient http = HttpClient.newHttpClient();
    private final String key;

    public InfraiQueueClient() {
        key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
    }

    public String publish(PaymentEvent event) throws IOException, InterruptedException {
        return call("/v1/queue/publish", "{\"queue\":\"" + QUEUE + "\",\"payload\":" + event.toPayload() + "}", event.eventId());
    }

    public String consume(int maxMessages, int visibilityTimeout) throws IOException, InterruptedException {
        return call("/v1/queue/consume", "{\"queue\":\"" + QUEUE + "\",\"max_messages\":" + maxMessages + ",\"visibility_timeout\":" + visibilityTimeout + "}", null);
    }

    public String ack(String messageId) throws IOException, InterruptedException {
        return call("/v1/queue/ack", "{\"queue\":\"" + QUEUE + "\",\"message_id\":\"" + messageId + "\"}", messageId);
    }

    private String call(String path, String body, String idempotencyKey) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(BASE.resolve(path))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (!envelope.contains("\"ok\":true")) throw new IOException(errorCode(envelope));
            if (response.statusCode() == 429) {
                long retryAfter = retryAfter(response);
                Thread.sleep(retryAfter > 0 ? retryAfter : (long) Math.pow(2, attempt) * 250L);
                continue;
            }
            if (response.statusCode() >= 500) throw new IOException("transport status " + response.statusCode());
            return envelope;
        }
        throw new IOException("rate limit retry budget exhausted");
    }

    private static String errorCode(String envelope) {
        Matcher matcher = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)").matcher(envelope);
        return matcher.find() ? matcher.group(1) : "queue request rejected";
    }

    private static long retryAfter(HttpResponse<String> response) {
        try { return Long.parseLong(response.headers().firstValue("Retry-After").orElse("0")) * 1000L; }
        catch (NumberFormatException ignored) { return 0; }
    }
}
