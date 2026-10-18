package app.opensuite.one;

import org.json.JSONObject;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Native HTTPS API client: in-memory session cookies, no disk token persistence. */
final class ApiClient {
    private final OkHttpClient http = new OkHttpClient.Builder()
        .connectTimeout(12,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build();
    private String base = "";
    private String cookie = "";
    private String csrf = "";

    void configure(String server) {
        String clean = server.trim();
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        try {
            URL url = new URL(clean);
            if (!"https".equals(url.getProtocol()) && !(BuildConfig.DEBUG && "http".equals(url.getProtocol())))
                throw new IllegalArgumentException("HTTPS required (HTTP permitted only in debug builds)");
            if (url.getHost().isEmpty() || url.getUserInfo() != null || url.getPort() > 65535)
                throw new IllegalArgumentException("Invalid server URL");
            if (!url.getPath().isEmpty() && !"/".equals(url.getPath()))
                throw new IllegalArgumentException("Enter only the server address, without a path");
            if (url.getQuery() != null || url.getRef() != null)
                throw new IllegalArgumentException("Remove query and fragment from server URL");
        } catch (java.net.MalformedURLException e) { throw new IllegalArgumentException("Enter a valid server URL", e); }
        base = clean;
        clear();
    }

    boolean hasSession() { return !cookie.isEmpty() && !csrf.isEmpty(); }
    void clear() { cookie = ""; csrf = ""; }

    JSONObject login(String email, String password) throws Exception {
        JSONObject body = new JSONObject().put("email", email.trim()).put("password", password);
        JSONObject response = request("POST", "/api/login", body);
        csrf = response.getString("csrf");
        if (cookie.isEmpty()) throw new Exception("Session cookie missing. Check server configuration.");
        return response;
    }

    JSONObject request(String method, String path, JSONObject body) throws Exception {
        if (base.isEmpty()) throw new IllegalStateException("Connect to a server first");
        if (!path.startsWith("/api/")) throw new IllegalArgumentException("Invalid API path");
        Request.Builder request = new Request.Builder().url(base + path)
            .header("Accept", "application/json").header("Cache-Control", "no-store");
        if (!cookie.isEmpty()) request.header("Cookie",cookie);
        if (!"GET".equals(method)) request.header("X-CSRF-Token",csrf);
        if ("POST".equals(method) && "/api/tasks".equals(path))
            request.header("Idempotency-Key",UUID.randomUUID().toString());
        RequestBody payload = body == null ? null : RequestBody.create(body.toString(),MediaType.get("application/json; charset=utf-8"));
        request.method(method,payload);
        try (Response response = http.newCall(request.build()).execute()) {
            String value = response.header("Set-Cookie", "");
            if (value.startsWith("opensuite_session=")) cookie = value.split(";",2)[0];
            int status=response.code();
            if (status >= 300 && status < 400) throw new Exception("Redirect refused. Use the final HTTPS server URL.");
            if (response.body()==null) throw new Exception("Empty response (HTTP " + status + ")");
            String raw=response.body().string();
            if (raw.length()>2_000_000) throw new Exception("Response too large");
            JSONObject json = new JSONObject(raw);
            if (!response.isSuccessful()) {
                String message=json.optJSONObject("error") != null ? json.optJSONObject("error").optString("message","Request failed") : "Request failed";
                if (status == 401) clear();
                throw new Exception(message + " (HTTP " + status + ")");
            }
            return json;
        }
    }
}
