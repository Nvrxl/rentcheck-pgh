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

    private static final int HOTSPOT_LIMIT = 10000;

    /**
     * TESTING HELPER: addresses with the most STILL-OPEN building/fire-safety cases.
     * Use it to find interesting addresses to try (and to see if the risk rating looks right).
     * Optional neighborhood filter, e.g. "Oakland" matches North/South/Central Oakland.
     *
     * The city's SQL endpoint refused our first attempt (HTTP 403), so this uses the plain search
     * endpoint (filters) and does the counting here in Java instead.
     */
    public JsonNode hotspots(String neighborhood) throws IOException, InterruptedException {
        String hood = neighborhood == null ? "" : neighborhood.trim();
        if (!hood.matches("[A-Za-z .'-]{0,40}")) {
            throw new IOException("neighborhood may only contain letters, spaces, . ' and -");
        }
        String filters = "{\"status\":[\"In Violation\",\"In Court\",\"Clean & Lien\",\"Appealed\"],"
                + "\"case_file_type\":[\"Building Maintenance\",\"Building Maintenance Issues\",\"Fire Safety System Issue\"]}";
        String url = BASE + "datastore_search?resource_id=" + resourceId()
                + "&limit=" + HOTSPOT_LIMIT
                + "&fields=" + URLEncoder.encode("address,casefile_number,neighborhood", StandardCharsets.UTF_8)
                + "&sort=" + URLEncoder.encode("investigation_date desc", StandardCharsets.UTF_8)
                + "&filters=" + URLEncoder.encode(filters, StandardCharsets.UTF_8);
        JsonNode records = get(url).path("result").path("records");
        return summarizeHotspots(records, hood, mapper, HOTSPOT_LIMIT);
    }

    /** Counts distinct cases per address (top 25). Separate and public so it can be tested without the network. */
    public static JsonNode summarizeHotspots(JsonNode records, String hood, ObjectMapper mapper, int limit) {
        String wanted = hood == null ? "" : hood.toLowerCase();
        java.util.Map<String, java.util.Set<String>> casesByAddress = new java.util.HashMap<>();
        java.util.Map<String, String> hoodByAddress = new java.util.HashMap<>();
        for (JsonNode r : records) {
            String address = r.path("address").asText("");
            String hoodName = r.path("neighborhood").asText("");
            if (address.isEmpty()) continue;
            if (!wanted.isEmpty() && !hoodName.toLowerCase().contains(wanted)) continue;
            casesByAddress.computeIfAbsent(address, k -> new java.util.HashSet<>()).add(r.path("casefile_number").asText(""));
            hoodByAddress.put(address, hoodName);
        }
        com.fasterxml.jackson.databind.node.ArrayNode top = mapper.createArrayNode();
        casesByAddress.entrySet().stream()
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .limit(25)
                .forEach(e -> {
                    com.fasterxml.jackson.databind.node.ObjectNode o = top.addObject();
                    o.put("address", e.getKey());
                    o.put("open_cases", e.getValue().size());
                    o.put("neighborhood", hoodByAddress.get(e.getKey()));
                });
        com.fasterxml.jackson.databind.node.ObjectNode result = mapper.createObjectNode();
        result.put("rows_scanned", records.size());
        result.put("truncated", records.size() >= limit);
        result.set("hotspots", top);
        return result;
    }

    /**
     * One page of every STILL-OPEN case row (all case types; we sort out the building/fire safety ones
     * ourselves with Categories). Used by NeighborhoodService. Sorted by _id so paging is stable.
     */
    public JsonNode openCasesPage(int offset, int limit) throws IOException, InterruptedException {
        String filters = "{\"status\":[\"In Violation\",\"In Court\",\"Clean & Lien\",\"Appealed\"]}";
        String fields = String.join(",", Fields.CASEFILE, Fields.CASE_TYPE, Fields.ADDRESS, Fields.NEIGHBORHOOD,
                Fields.DATE, Fields.LATITUDE, Fields.LONGITUDE);
        String url = BASE + "datastore_search?resource_id=" + resourceId()
                + "&limit=" + limit + "&offset=" + offset
                + "&fields=" + URLEncoder.encode(fields, StandardCharsets.UTF_8)
                + "&sort=" + URLEncoder.encode("_id", StandardCharsets.UTF_8)
                + "&filters=" + URLEncoder.encode(filters, StandardCharsets.UTF_8);
        return get(url);
    }

    /** City of Pittsburgh neighborhood population, 2010 and 2020 (2020 Census redistricting extract). */
    private static final String POPULATION_RESOURCE_ID = "a8414ed5-c50f-417e-bb67-82b734660da6";

    public JsonNode population() throws IOException, InterruptedException {
        return get(BASE + "datastore_search?resource_id=" + POPULATION_RESOURCE_ID + "&limit=500");
    }

    /**
     * DEVELOPER HELPER: peek at another WPRDC dataset (e.g. "property-assessments") before we write code
     * against it. Lists its resources and shows the column names plus two example rows of the first
     * queryable one, so we check real column names instead of guessing.
     */
    public JsonNode peek(String packageId) throws IOException, InterruptedException {
        if (packageId == null || !packageId.matches("[a-z0-9-]{1,80}")) {
            throw new IOException("package must be a WPRDC dataset id like property-assessments");
        }
        JsonNode resources = get(BASE + "package_show?id=" + packageId).path("result").path("resources");
        com.fasterxml.jackson.databind.node.ArrayNode list = mapper.createArrayNode();
        String chosen = null;
        for (JsonNode r : resources) {
            com.fasterxml.jackson.databind.node.ObjectNode o = list.addObject();
            o.put("id", r.path("id").asText());
            o.put("name", r.path("name").asText());
            o.put("format", r.path("format").asText());
            o.put("datastore_active", r.path("datastore_active").asBoolean(false));
            if (chosen == null && r.path("datastore_active").asBoolean(false)) chosen = r.path("id").asText();
        }
        com.fasterxml.jackson.databind.node.ObjectNode out = mapper.createObjectNode();
        out.set("resources", list);
        out.put("firstQueryableResource", chosen);
        if (chosen != null) {
            out.set("sample", get(BASE + "datastore_search?resource_id=" + chosen + "&limit=2").path("result"));
        }
        return out;
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
