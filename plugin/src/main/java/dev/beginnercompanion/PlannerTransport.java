package dev.beginnercompanion;

import dev.beginnercompanion.bridge.PlannerClient;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import okio.Buffer;

/** Uses RuneLite's injected client; never follows a redirect carrying player observations. */
final class PlannerTransport implements PlannerClient.Transport {
    static final long MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final OkHttpClient client;

    PlannerTransport(OkHttpClient injectedClient) {
        client = injectedClient.newBuilder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS).build();
    }

    @Override public CompletableFuture<String> post(URI endpoint, String body) {
        Request request = new Request.Builder().url(endpoint.toString())
            .header("Accept", "application/json").header("Cache-Control", "no-store")
            .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body)).build();
        Call call = client.newCall(request);
        CompletableFuture<String> result = new CompletableFuture<>();
        result.whenComplete((value, failure) -> { if (result.isCancelled()) call.cancel(); });
        call.enqueue(new Callback() {
            @Override public void onFailure(Call ignored, IOException failure) { result.completeExceptionally(failure); }
            @Override public void onResponse(Call ignored, Response response) {
                try (Response closed = response) {
                    if (closed.code() != 200) throw new IOException("Planner returned HTTP " + closed.code() + ".");
                    ResponseBody content = closed.body();
                    if (content == null || content.contentLength() > MAX_RESPONSE_BYTES)
                        throw new IOException("Planner response is empty or too large.");
                    MediaType type = content.contentType();
                    if (type == null || !"application".equals(type.type()) || !"json".equals(type.subtype()))
                        throw new IOException("Planner returned a non-JSON response.");
                    Buffer buffer = new Buffer();
                    while (content.source().read(buffer, Math.min(8192, MAX_RESPONSE_BYTES + 1 - buffer.size())) != -1) {
                        if (buffer.size() > MAX_RESPONSE_BYTES) throw new IOException("Planner response is too large.");
                    }
                    result.complete(buffer.readUtf8());
                } catch (Exception failure) { result.completeExceptionally(failure); }
            }
        });
        return result;
    }
}
