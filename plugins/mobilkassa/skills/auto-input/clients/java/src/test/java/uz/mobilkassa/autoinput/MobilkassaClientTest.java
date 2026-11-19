package uz.mobilkassa.autoinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Scenario tests against the mock kassa started with --idle-seconds 2; skipped when it is not running.
class MobilkassaClientTest {

    private static final String MOCK = System.getenv().getOrDefault("MOCK_URL", "http://127.0.0.1:6461");
    private final HttpClient http = HttpClient.newHttpClient();
    private final MobilkassaClient client = new MobilkassaClient(MOCK + "/pospoint", "mock-kassa-key", Duration.ofSeconds(10), Duration.ofMillis(200));

    private static final String TEA = """
        {"name":"Чай чёрный 100 г","amount":2,"price":15000,"mxik":"02101001001000000","ownerType":0,"unit":"1","packageCode":1500,"taxPercent":12,"sku":"2050"}""";

    private static JsonObject sale(String n) {
        return JsonParser.parseString("{\"payCheckNumber\":\"" + n + "\",\"type\":\"SALE\",\"byCash\":30000,\"items\":[" + TEA + "]}").getAsJsonObject();
    }

    private String get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(MOCK + path)).build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private int post(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(MOCK + path)).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private void cashier(String action, String n) throws Exception {
        long until = System.currentTimeMillis() + 5000;
        while (!get("/mock/state").contains("\"payCheckNumber\":\"" + n + "\"")) {
            if (System.currentTimeMillis() > until) throw new AssertionError(n + " never reached the desk");
            Thread.sleep(20);
        }
        assertEquals(200, post("/mock/" + action, "{\"payCheckNumber\":\"" + n + "\"}"));
    }

    private CompletableFuture<Outcome> async(Callable call) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return call.run();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private interface Callable {
        Outcome run() throws InterruptedException;
    }

    @BeforeEach
    void reset() throws Exception {
        boolean up;
        try {
            up = get("/health").contains("UP");
        } catch (Exception e) {
            up = false;
        }
        assumeTrue(up, "mock kassa is not running at " + MOCK);
        post("/mock/reset", "");
    }

    @Test
    void s1SaleIssued() throws Exception {
        CompletableFuture<Outcome> call = async(() -> client.sendPreReceipt(sale("S1")));
        cashier("issue", "S1");
        Outcome.Issued issued = assertInstanceOf(Outcome.Issued.class, call.get());
        assertEquals(30000.0, issued.result().get("total").getAsDouble());
        assertEquals(false, issued.result().get("changed").getAsBoolean());
    }

    @Test
    void s3IdleIsWaitingThenIssued() throws Exception {
        assertInstanceOf(Outcome.Waiting.class, client.sendPreReceipt(sale("S3")));
        cashier("issue", "S3");
        assertInstanceOf(Outcome.Issued.class, client.waitForResult("S3", Duration.ofSeconds(5)));
    }

    @Test
    void s4Rejected() throws Exception {
        CompletableFuture<Outcome> call = async(() -> client.sendPreReceipt(sale("S4")));
        cashier("reject", "S4");
        assertInstanceOf(Outcome.Rejected.class, call.get());
    }

    @Test
    void s6PartialRefundBySku() throws Exception {
        CompletableFuture<Outcome> sale = async(() -> client.sendPreReceipt(sale("S6")));
        cashier("issue", "S6");
        sale.get();
        JsonObject refund = JsonParser.parseString("{\"payCheckNumber\":\"S6-R\",\"salePayCheckNumber\":\"S6\",\"items\":[{\"sku\":\"2050\",\"amount\":1}]}").getAsJsonObject();
        CompletableFuture<Outcome> call = async(() -> client.sendRefundPreReceipt(refund));
        cashier("issue", "S6-R");
        Outcome.Issued issued = assertInstanceOf(Outcome.Issued.class, call.get());
        assertEquals("REFUND", issued.result().get("type").getAsString());
        assertEquals(1.0, issued.result().getAsJsonArray("items").get(0).getAsJsonObject().get("amount").getAsDouble());
    }

    @Test
    void s5RepeatOfAnIssuedDocumentIsIssued() throws Exception {
        CompletableFuture<Outcome> call = async(() -> client.sendPreReceipt(sale("S5")));
        cashier("issue", "S5");
        call.get();
        // 14824: already issued; the client asks the status instead of reporting a failure.
        Outcome.Issued issued = assertInstanceOf(Outcome.Issued.class, client.sendPreReceipt(sale("S5")));
        assertEquals(30000.0, issued.result().get("total").getAsDouble());
    }

    @Test
    void aShortTimeoutIsWaiting() throws Exception {
        MobilkassaClient impatient = new MobilkassaClient(MOCK + "/pospoint", "mock-kassa-key", Duration.ofMillis(300), Duration.ofMillis(100));
        assertInstanceOf(Outcome.Waiting.class, impatient.sendPreReceipt(sale("T")));
    }
}
