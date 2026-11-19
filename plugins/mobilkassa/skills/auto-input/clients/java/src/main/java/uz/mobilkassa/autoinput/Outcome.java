package uz.mobilkassa.autoinput;

import com.google.gson.JsonObject;
import java.util.List;

/**
 * What a pre-receipt call ended with.
 * Waiting: the cashier has not finished (14827 or an HTTP timeout) — ask getPreReceiptStatus later.
 * Rejected: the cashier rejected it (14828) — a corrected pre-receipt may go out with the same number.
 */
public sealed interface Outcome permits Outcome.Issued, Outcome.Waiting, Outcome.Rejected, Outcome.Failed {

    /** The ISSUED result: payCheckNumber, status, type, changed, items, payments, total. */
    record Issued(JsonObject result) implements Outcome {}

    record Waiting() implements Outcome {}

    record Rejected() implements Outcome {}

    /** Code 0: no connection to the kassa. */
    record Failed(List<RpcError> errors) implements Outcome {}

    record RpcError(int code, String message) {}
}
