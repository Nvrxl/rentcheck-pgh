package pgh.rentcheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Talks to Pittsburgh's open-data portal (WPRDC, a CKAN site).
 * No API key needed. Docs: https://docs.ckan.org/en/latest/maintainer/datastore.html
 *
 * We do NOT hardcode the table ("resource") id. On first use we ask the portal
 * for the dataset's resources and pick the first one that has a queryable
 * datastore. Override with the env var WPRDC_RESOURCE_ID if it picks wrong.
 */
public class WprdcClient {
    private static final String BASE = "https://data.wprdc.org/api/3/action/";
    private static final String PACKAGE_ID = "pittsburgh-pli-violations-report";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private String resourceId;

    /** Finds (and remembers) the id of the violations table. */
    public synchronized String resourceId() throws IOException, InterruptedException {
        if (resourceId != null) return resourceId;

        String fromEnv = System.getenv("WPRDC_RESOURCE_ID");
        if (fromEnv != null && !fromEnv.isBlank()) {
            resourceId = fromEnv;
            return resourceId;
        }

        JsonNode resources = get(BASE + "package_show?id=" + PACKAGE_ID).path("result").path("resources");
        for (JsonNode r : resources) {
            System.out.println("[wprdc] resource: " + r.path("id").asText()
                    + " | " + r.path("name").asText()
                    + " | format=" + r.path("format").asText()
                    + " | datastore_active=" + r.path("datastore_active").asText());
        }
        for (JsonNode r : resources) {
            if (r.path("datastore_active").asBoolean(false)) {
                resourceId = r.path("id").asText();
                System.out.println("[wprdc] using resource " + resourceId);
                return resourceId;
            }
        }
        throw new IOException("No queryable resource found for dataset " + PACKAGE_ID
                + ". Set WPRDC_RESOURCE_ID manually (see console output above).");
    }

    /** Full-text search across the violations table. Returns the whole CKAN JSON response. */
    public JsonNode search(String text, int limit) throws IOException, InterruptedException {
        String url = BASE + "datastore_search?resource_id=" + resourceId()
                + "&limit=" + limit
                + "&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        return get(url);
    }

    /**
     * Searches ONLY the address column (much more precise than searching every column).
     * CKAN accepts q as a JSON object like {"address": "1231 LAKEWOOD ST"}.
     */
    public JsonNode searchByAddress(String normalizedAddress, int limit) throws IOException, InterruptedException {
        String q = "{\"address\":\"" + normalizedAddress.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        String url = BASE + "datastore_search?resource_id=" + resourceId()
                + "&limit=" + limit
                + "&sort=" + URLEncoder.encode("investigation_date desc", StandardCharsets.UTF_8)
                + "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
        return get(url);
    }

    /**
     * Which different values does a column contain, and how many rows each?
     * Used by /api/debug/distinct?field=status so we can see the REAL values
     * (e.g. all the possible investigation outcomes) instead of guessing.
     * Only whitelisted column names are allowed, because we build SQL text.
     */
    public JsonNode distinct(String field) throws IOException, InterruptedException {
        if (!java.util.Set.of(Fields.STATUS, Fields.CASE_TYPE, Fields.OUTCOME, Fields.NEIGHBORHOOD).contains(field)) {
            throw new IOException("field must be one of: status, case_file_type, investigation_outcome, neighborhood");
        }
        String sql = "SELECT \"" + field + "\" AS value, COUNT(*) AS n FROM \"" + resourceId()
                + "\" GROUP BY \"" + field + "\" ORDER BY n DESC LIMIT 60";
        return get(BASE + "datastore_search_sql?sql=" + URLEncoder.encode(sql, StandardCharsets.UTF_8));
    }

    /** Shows the real column names plus one example row. Used by /api/debug/fields. */
    public JsonNode describeFields() throws IOException, InterruptedException {
        return get(BASE + "datastore_search?resource_id=" + resourceId() + "&limit=1");
    }

    private JsonNode get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("WPRDC returned HTTP " + resp.statusCode() + " for " + url);
        }
        return mapper.readTree(resp.body());
    }
}
