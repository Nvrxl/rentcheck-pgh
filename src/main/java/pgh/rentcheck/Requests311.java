package pgh.rentcheck;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 311 service requests around an address: potholes, streetlights, dumping, sidewalks.
 *
 * Violations and permits describe the BUILDING. 311 describes the street it sits on, which is the
 * other half of what a renter is actually choosing. NO LANGUAGE MODELS: counting and sorting.
 *
 * SOURCE: "Pittsburgh 311 Data" (pittsburgh-311-data), the CURRENT dataset. The city moved to a new
 * 311 system on 4 Feb 2025; the old archive stopped then and is not used here. This one covers
 * March 2025 onwards and is published four times a day. Verified Sat Sep 19 2026.
 *
 * WE LOOK UP BY NEIGHBOURHOOD, NOT BY RADIUS. Two reasons: the city's API cannot do "within X
 * metres", and the city deliberately withholds the exact location of some complaint types for
 * privacy. Neighbourhood works for every address, including ones we cannot place on a map, and it
 * never requires us to guess where a withheld request was.
 *
 * COLUMN NAMES ARE DISCOVERED AT RUN TIME (see {@link #detect}), not hardcoded, because we had not
 * seen this dataset's columns when this was written. Whatever we could not find simply goes unused,
 * and /api/debug/311 shows exactly which columns were picked.
 */
public final class Requests311 {
    private Requests311() {}

    static final int MAX_TYPES = 6;
    static final int MAX_ITEMS = 8;
    static final int RECENT_DAYS = 365;

    /** Which column holds what. Any of these can be null when the dataset has no such column. */
    public record Columns(String type, String status, String created, String closed,
                          String neighborhood, String latitude, String longitude) {}

    public record TypeCount(String type, int count) {}

    public record RequestItem(String date, String type, String status) {}

    public record Summary(
            String neighborhood,
            int total,              // requests we looked at
            int recent,             // ...created in the last year
            Integer open,           // still open, or null if the dataset has no status column
            String asOf,            // newest request date we saw (yyyy-MM-dd), or null
            List<TypeCount> topTypes,
            List<RequestItem> items,
            String summary,
            String note
    ) {}

    /**
     * Works out which column is which from the dataset's own field list, by looking for words in the
     * column names. Deliberately conservative: an unrecognised dataset gives nulls, and the caller
     * shows nothing rather than something wrong.
     */
    static Columns detect(JsonNode fields) {
        String type = null, status = null, created = null, closed = null;
        String neighborhood = null, lat = null, lon = null;
        for (JsonNode f : fields) {
            String id = f.path("id").asText("");
            if (id.isEmpty() || id.startsWith("_")) continue;
            String low = id.toLowerCase();

            if (type == null && (low.contains("request_type") || low.contains("issue") || low.equals("type")
                    || low.contains("subject") || low.contains("category"))) {
                type = id;
            }
            if (status == null && low.contains("status")) status = id;
            if (closed == null && (low.contains("closed") || low.contains("resolved") || low.contains("close_date"))) {
                closed = id;
            }
            // "created" must not steal the closed-date column, so check closed words first.
            if (created == null && !low.contains("closed") && !low.contains("resolved")
                    && (low.contains("created") || low.contains("opened") || low.contains("request_date")
                        || low.contains("submit") || low.equals("date"))) {
                created = id;
            }
            if (neighborhood == null && low.contains("neighborhood")) neighborhood = id;
            if (lat == null && (low.equals("latitude") || low.equals("lat") || low.equals("y"))) lat = id;
            if (lon == null && (low.equals("longitude") || low.equals("lon") || low.equals("lng") || low.equals("x"))) {
                lon = id;
            }
        }
        return new Columns(type, status, created, closed, neighborhood, lat, lon);
    }

    /** Statuses we read as "still open". Anything we don't recognise counts as not-open. */
    static boolean looksOpen(String status) {
        if (status == null) return false;
        String s = status.trim().toLowerCase();
        return s.contains("open") || s.contains("new") || s.contains("progress")
                || s.contains("pending") || s.contains("assigned");
    }

    /**
     * Counts what the city has been asked to fix around here. Pure and package-visible, so it is
     * tested without the network.
     */
    static Summary summarize(JsonNode records, Columns cols, String neighborhood, LocalDate today) {
        List<JsonNode> rows = new ArrayList<>();
        records.forEach(rows::add);
        if (rows.isEmpty()) return null;

        String cutoff = today.minusDays(RECENT_DAYS).toString();
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        List<RequestItem> items = new ArrayList<>();
        int recent = 0, open = 0;
        boolean haveStatus = cols.status() != null;
        String asOf = null;

        for (JsonNode r : rows) {
            String date = day(text(r, cols.created()));
            String type = text(r, cols.type());
            String status = text(r, cols.status());

            if (date != null) {
                if (date.compareTo(cutoff) >= 0) recent++;
                if (asOf == null || date.compareTo(asOf) > 0) asOf = date;
            }
            if (type != null) typeCounts.merge(type, 1, Integer::sum);
            if (haveStatus && looksOpen(status)) open++;
            items.add(new RequestItem(date, type, status));
        }

        List<TypeCount> top = new ArrayList<>();
        typeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_TYPES)
                .forEach(e -> top.add(new TypeCount(e.getKey(), e.getValue())));

        items.sort((a, b) -> {
            if (a.date() == null) return 1;
            if (b.date() == null) return -1;
            return b.date().compareTo(a.date());
        });
        List<RequestItem> shown = items.size() > MAX_ITEMS ? new ArrayList<>(items.subList(0, MAX_ITEMS)) : items;

        return new Summary(neighborhood, rows.size(), recent, haveStatus ? open : null, asOf, top, shown,
                summarize(rows.size(), recent, haveStatus ? open : null, top, neighborhood),
                "311 requests are things RESIDENTS reported to the city in " + neighborhood
                        + ", not problems with this particular building. Lots of requests can mean a "
                        + "neglected street or simply neighbours who report things. The city withholds the "
                        + "exact location of some complaint types for privacy, so this is a neighbourhood "
                        + "picture, not a street-by-street one. Data starts March 2025.");
    }

    static String summarize(int total, int recent, Integer open, List<TypeCount> top, String neighborhood) {
        StringBuilder sb = new StringBuilder();
        sb.append("Residents made ").append(total).append(total == 1 ? " 311 request" : " 311 requests")
                .append(" in ").append(neighborhood);
        if (recent > 0 && recent != total) sb.append(", ").append(recent).append(" in the last year");
        sb.append(".");
        if (open != null && open > 0) {
            sb.append(" ").append(open).append(open == 1 ? " is" : " are").append(" still open.");
        }
        if (!top.isEmpty()) {
            sb.append(" The most common is ").append(top.get(0).type().toLowerCase())
                    .append(" (").append(top.get(0).count()).append(").");
        }
        return sb.toString();
    }

    private static String day(String date) {
        if (date == null) return null;
        return date.length() >= 10 ? date.substring(0, 10) : date;
    }

    private static String text(JsonNode r, String field) {
        if (field == null) return null;
        JsonNode n = r.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        String s = n.asText().trim();
        return s.isEmpty() ? null : s;
    }
}
