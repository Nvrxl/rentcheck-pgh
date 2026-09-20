package pgh.rentcheck;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What is near a building: bike share docks, bus stops, food shops.
 *
 * NO LANGUAGE MODELS: parsing, distance arithmetic and sorting.
 *
 * HOW IT WORKS, and why. Each list is small enough to hold in memory (about a hundred POGOH
 * stations, a few thousand bus stops), so we download each one ONCE at start-up and answer every
 * "what is near here" from memory. A report therefore costs zero extra calls to the city - which
 * matters, because a report already makes five, and a slow report is a bad demo.
 *
 * Column names are DISCOVERED at run time, like the 311 data, because these datasets each name
 * their columns differently and we have not seen all of them. Anything we cannot find is skipped
 * rather than guessed.
 *
 * WE NEVER INVENT A PLACE. Every entry comes from a published dataset, and nothing appears on the
 * map that we did not read out of one.
 */
public final class NearbyPlaces {
    private NearbyPlaces() {}

    /** The pin colours the page uses come from this: BIKE, BUS, FOOD. */
    public static final String BIKE = "BIKE";
    public static final String BUS = "BUS";
    public static final String FOOD = "FOOD";

    static final double DEFAULT_MAX_MILES = 0.6;   // a comfortable walk, roughly 10-12 minutes
    static final int DEFAULT_PER_CATEGORY = 5;

    public record Place(String category, String name, double latitude, double longitude, String detail) {}

    public record NearbyPlace(String category, String name, double latitude, double longitude,
                              String detail, double distanceMiles) {}

    /** Which column holds what, for one dataset. */
    public record Columns(String name, String latitude, String longitude, String detail) {}

    /**
     * Works out which columns to read from a dataset's own field list. Looks for a human-readable
     * name and a pair of coordinates; everything else is optional.
     */
    static Columns detect(JsonNode fields) {
        String name = null, lat = null, lon = null, detail = null;
        for (JsonNode f : fields) {
            String id = f.path("id").asText("");
            if (id.isEmpty() || id.startsWith("_")) continue;
            String low = id.toLowerCase();

            if (lat == null && (low.equals("latitude") || low.equals("lat") || low.equals("y"))) lat = id;
            if (lon == null && (low.equals("longitude") || low.equals("lon") || low.equals("lng") || low.equals("x"))) {
                lon = id;
            }
            // A "station name", "facility name", "stop name" - anything ending in or equal to name.
            if (name == null && (low.equals("name") || low.endsWith("_name") || low.endsWith(" name")
                    || low.contains("station") && low.contains("name")
                    || low.contains("stop") && low.contains("name")
                    || low.contains("facility") && low.contains("name"))) {
                name = id;
            }
            if (detail == null && (low.contains("address") || low.contains("docks") || low.contains("category")
                    || low.contains("type") || low.contains("routes"))) {
                detail = id;
            }
        }
        return new Columns(name, lat, lon, detail);
    }

    /**
     * Turns rows into places. Rows without a usable position are dropped: a pin we cannot place is
     * worse than no pin. Positions outside greater Pittsburgh are treated as data errors.
     */
    static List<Place> parse(JsonNode records, Columns cols, String category) {
        List<Place> out = new ArrayList<>();
        if (cols.latitude() == null || cols.longitude() == null) return out;   // nothing we can map
        for (JsonNode r : records) {
            double lat = num(r, cols.latitude()), lon = num(r, cols.longitude());
            if (Double.isNaN(lat) || Double.isNaN(lon) || lat == 0 || lon == 0) continue;
            if (lat < 40.2 || lat > 40.6 || lon < -80.2 || lon > -79.7) continue;
            String name = text(r, cols.name());
            out.add(new Place(category, name == null ? category : name, lat, lon, text(r, cols.detail())));
        }
        return out;
    }

    /**
     * The closest places to a point, capped per category so one dense category (bus stops) cannot
     * crowd out the others. Sorted nearest first.
     */
    static List<NearbyPlace> nearest(double lat, double lon, List<Place> places,
                                     double maxMiles, int perCategory) {
        List<NearbyPlace> all = new ArrayList<>();
        for (Place p : places) {
            double miles = NeighborhoodService.distanceKm(lat, lon, p.latitude(), p.longitude())
                    * NeighborhoodService.KM_TO_MILES;
            if (miles > maxMiles) continue;
            all.add(new NearbyPlace(p.category(), p.name(), p.latitude(), p.longitude(), p.detail(),
                    Math.round(miles * 100) / 100.0));
        }
        all.sort(Comparator.comparingDouble(NearbyPlace::distanceMiles));

        Map<String, Integer> used = new HashMap<>();
        List<NearbyPlace> out = new ArrayList<>();
        for (NearbyPlace p : all) {
            int n = used.getOrDefault(p.category(), 0);
            if (n >= perCategory) continue;
            used.put(p.category(), n + 1);
            out.add(p);
        }
        return out;
    }

    /** The sentence under the map. Says what is missing as clearly as what is there. */
    static String summarize(List<NearbyPlace> found, double maxMiles) {
        Map<String, Integer> counts = new HashMap<>();
        for (NearbyPlace p : found) counts.merge(p.category(), 1, Integer::sum);
        List<String> parts = new ArrayList<>();
        if (counts.containsKey(BUS)) parts.add(counts.get(BUS) + " bus stop" + (counts.get(BUS) == 1 ? "" : "s"));
        if (counts.containsKey(BIKE)) parts.add(counts.get(BIKE) + " bike share station" + (counts.get(BIKE) == 1 ? "" : "s"));
        if (counts.containsKey(FOOD)) parts.add(counts.get(FOOD) + " food shop" + (counts.get(FOOD) == 1 ? "" : "s"));
        if (parts.isEmpty()) {
            return "Nothing we track is within " + maxMiles + " miles of here. That does not mean there is "
                    + "nothing nearby: we only map bus stops, bike share and food shops from city and county data.";
        }
        return "Within " + maxMiles + " miles: " + String.join(", ", parts)
                + ". Distances are straight-line, not walking distance, and Pittsburgh's hills make the "
                + "difference real.";
    }

    private static String text(JsonNode r, String field) {
        if (field == null) return null;
        JsonNode n = r.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        String s = n.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static double num(JsonNode r, String field) {
        JsonNode n = r.path(field);
        if (n.isMissingNode() || n.isNull()) return Double.NaN;
        if (n.isNumber()) return n.asDouble();
        try {
            return Double.parseDouble(n.asText().trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
