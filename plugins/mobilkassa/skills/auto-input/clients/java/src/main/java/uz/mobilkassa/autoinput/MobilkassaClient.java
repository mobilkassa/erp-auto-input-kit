package uz.mobilkassa.autoinput;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mobilkassa auto-input client: pre-receipts on port 6461, protocol 1.0.2. Copy into the ERP; needs Gson.
 * Always send your own payCheckNumber: a repeat with it never makes a second receipt.
 */
public final class MobilkassaClient {

    private static final int WAITING = 14827;
    private static final int REJECTED = 14828;
    private static final int ALREADY_ISSUED = 14824;

    private final URI url;
    private final String key;
    private final Duration timeout;
    private final Duration poll;
    private final HttpClient http = HttpClient.newHttpClient();
    private final AtomicLong nextId = new AtomicLong(1);

    /** url: http://&lt;kassa&gt;:6461/pospoint; key: from the kassa's «Автоввод данных чека» block; timeout above the 90 s hold. */
    public MobilkassaClient(String url, String key, Duration timeout, Duration poll) {
        this.url = URI.create(url);
        this.key = key;
        this.timeout = timeout;
        this.poll = poll;
    }

    public MobilkassaClient(String url, String key) {
        this(url, key, Duration.ofSeconds(120), Duration.ofSeconds(5));
    }

    /** Held until the cashier issues or rejects the pre-receipt. */
    public Outcome sendPreReceipt(JsonObject request) throws InterruptedException {
        return send("sendPreReceipt", request);
    }

    /** The sale is named by salePayCheckNumber, or by saleId (the old idRefundReceipt). */
    public Outcome sendRefundPreReceipt(JsonObject request) throws InterruptedException {
        return send("sendRefundPreReceipt", request);
    }

    /**
     * 14824 on a re-send means this document was already issued (its answer was lost, or the ERP restarted):
     * its status carries the composition, so the caller gets Issued, never a failure that invites a second receipt.
     */
    private Outcome send(String method, JsonObject request) throws InterruptedException {
        Outcome outcome = call(method, request);
        if (outcome instanceof Outcome.Failed failed && failed.errors().stream().anyMatch(e -> e.code() == ALREADY_ISSUED)) {
            Outcome status = getPreReceiptStatus(request.get("payCheckNumber").getAsString());
            if (status instanceof Outcome.Issued) return status;
        }
        return outcome;
    }

    public Outcome getPreReceiptStatus(String payCheckNumber) throws InterruptedException {
        JsonObject params = new JsonObject();
        params.addProperty("payCheckNumber", payCheckNumber);
        return call("getPreReceiptStatus", params);
    }

    /** After Waiting: asks the status every poll interval until issued, rejected, failed or max passes. */
    public Outcome waitForResult(String payCheckNumber, Duration max) throws InterruptedException {
        Instant until = Instant.now().plus(max);
        while (true) {
            Outcome outcome = getPreReceiptStatus(payCheckNumber);
            if (!(outcome instanceof Outcome.Waiting) || Instant.now().plus(poll).isAfter(until)) return outcome;
            Thread.sleep(poll.toMillis());
        }
    }

    private Outcome call(String method, JsonObject params) throws InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("id", String.valueOf(nextId.getAndIncrement()));
        body.addProperty("key", key);
        body.addProperty("version", "1.0.2");
        body.addProperty("method", method);
        body.add("params", params);
        HttpRequest request = HttpRequest.newBuilder(url)
            .timeout(timeout) // HttpClient has no request timeout by default
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();
        JsonObject answer;
        try {
            String text = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
            answer = JsonParser.parseString(text).getAsJsonObject();
        } catch (HttpTimeoutException e) {
            return new Outcome.Waiting();
        } catch (IOException | RuntimeException e) {
            return new Outcome.Failed(List.of(new Outcome.RpcError(0, "Нет связи с кассой: " + e.getMessage())));
        }
        List<Outcome.RpcError> errors = errors(answer.getAsJsonArray("error"));
        if (errors.stream().anyMatch(e -> e.code() == WAITING)) return new Outcome.Waiting();
        if (errors.stream().anyMatch(e -> e.code() == REJECTED)) return new Outcome.Rejected();
        JsonElement result = answer.get("result");
        if (!errors.isEmpty() || result == null || !result.isJsonObject()) return new Outcome.Failed(errors);
        return switch (result.getAsJsonObject().get("status").getAsString()) {
            case "ISSUED" -> new Outcome.Issued(result.getAsJsonObject());
            case "REJECTED" -> new Outcome.Rejected();
            default -> new Outcome.Waiting();
        };
    }

    private static List<Outcome.RpcError> errors(JsonArray array) {
        List<Outcome.RpcError> errors = new ArrayList<>();
        if (array == null) return errors;
        for (JsonElement e : array) {
            JsonObject error = e.getAsJsonObject();
            errors.add(new Outcome.RpcError(error.get("code").getAsInt(), error.get("message").getAsString()));
        }
        return errors;
    }
}
