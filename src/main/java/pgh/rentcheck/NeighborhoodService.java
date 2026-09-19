package pgh.rentcheck;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Neighborhood ranking and "near you" lookup. NO language models: counting, dividing and sorting.
 *
 * WHAT WE RANK: neighborhoods by how many building and fire safety cases are still UNRESOLVED and were
 * issued in the last 3 years (the same "recent" cut-off as the address rating), per 1,000 residents when
 * we know the population. This is about housing conditions from city inspection records. It is NOT a crime
 * ranking: the city's open crime data stopped updating in November 2023, so we have no live crime feed.
 *
 * HOW: the first time (and every few hours after) we download every still-open case from the city, page
 * by page, and count them per neighborhood. That takes a while, so it runs in the background and the
 * endpoints answer "still loading" until it is done.
 */
public class NeighborhoodService {
    static final int PAGE_SIZE = 10000;
    static final int MAX_PAGES = 12;                 // safety stop: 120,000 rows is far more than exist
    static final long REFRESH_MS = 6L * 60 * 60 * 1000;
    static final int MIN_POPULATION_FOR_RATE = 1000; // tiny neighborhoods make wild per-1,000 numbers
    static final int NEARBY_COUNT = 6;
    static final double OUTSIDE_CITY_KM = 5.0;

    private final WprdcClient wprdc;
    private volatile Snapshot snapshot;
    private volatile long loadedAt;
    private volatile boolean loading;
    private volatile String lastError;

    public NeighborhoodService(WprdcClient wprdc) {
        this.wprdc = wprdc;
    }

    // ---------------------------------------------------------------- loading (background)

    /** Starts a background download if we have nothing yet or the data is old. Safe to call often. */
    public synchronized void refreshIfNeeded() {
        boolean stale = snapshot == null || System.currentTimeMillis() - loadedAt > REFRESH_MS;
        if (!stale || loading) return;
        loading = true;
        Thread t = new Thread(() -> {
            try {
                Aggregator agg = new Aggregator();
                int rows = 0;
                boolean truncated = false;
                for (int page = 0; page < MAX_PAGES; page++) {
                    JsonNode records = wprdc.openCasesPage(page * PAGE_SIZE, PAGE_SIZE).path("result").path("records");
                    agg.add(records);
                    rows += records.size();
                    if (records.size() < PAGE_SIZE) break;
                    if (page == MAX_PAGES - 1) truncated = true;
                }
                PopulationResult pop;
                try {
                    pop = parsePopulation(wprdc.population().path("result"));
                } catch (Exception e) {
                    pop = new PopulationResult(Map.of(), null, null);   // rank by counts instead
                }
                snapshot = agg.build(LocalDate.now(), pop.byName(), rows, truncated);
                loadedAt = System.currentTimeMillis();
                lastError = null;
                System.out.println("[neighborhoods] loaded " + rows + " rows, "
                        + snapshot.hoods().size() + " neighborhoods, rankedBy=" + snapshot.rankedBy());
            } catch (Exception e) {
                lastError = String.valueOf(e.getMessage());
                System.out.println("[neighborhoods] load failed: " + lastError);
            } finally {
                loading = false;
            }
        }, "neighborhood-loader");
        t.setDaemon(true);
        t.start();
    }

    /** The ranking, or null while the first download is still running (see status()). */
    public Snapshot current() {
        refreshIfNeeded();
        return snapshot;
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", lastError != null && snapshot == null ? "error" : "loading");
        m.put("message", lastError != null && snapshot == null
                ? "The city data could not be loaded: " + lastError
                : "Loading city data for the first time. This takes about a minute. Try again shortly.");
        return m;
    }

    // ---------------------------------------------------------------- results

    /** rateNote explains why per1000/rank is null (small population, unclear population), else null. */
    public record Hood(String name, int openRecent, int openOlder, int addresses,
                       Integer population, Double per1000, Integer rank, String rateNote) {}

    public record Nearby(String name, double distanceKm, Integer rank, int openRecent, Double per1000) {}

    /** Immutable result of one download. Points are parallel arrays so "nearest" is a cheap loop. */
    public record Snapshot(LocalDate asOf, List<Hood> hoods, String rankedBy, int rowsScanned, boolean truncated,
                           double[] lat, double[] lon, int[] hoodIndex) {

        /** The neighborhoods closest to a spot, using the nearest city case in each one. */
        public List<Nearby> nearby(double userLat, double userLon, int count) {
            double[] best = new double[hoods.size()];
            java.util.Arrays.fill(best, Double.MAX_VALUE);
            for (int i = 0; i < lat.length; i++) {
                double d = distanceKm(userLat, userLon, lat[i], lon[i]);
                if (d < best[hoodIndex[i]]) best[hoodIndex[i]] = d;
            }
            List<Nearby> out = new ArrayList<>();
            for (int h = 0; h < best.length; h++) {
                if (best[h] == Double.MAX_VALUE) continue;   // no coordinates for this neighborhood
                Hood hood = hoods.get(h);
                out.add(new Nearby(hood.name(), Math.round(best[h] * 100) / 100.0, hood.rank(), hood.openRecent(), hood.per1000()));
            }
            out.sort(Comparator.comparingDouble(Nearby::distanceKm));
            return out.size() > count ? out.subList(0, count) : out;
        }
    }

    // ---------------------------------------------------------------- counting (pure, unit-tested)

    /** One city case, merged from its rows. */
    private static final class CaseAcc {
        String earliestDate, address;
        double lat = Double.NaN, lon = Double.NaN;
    }

    /** Adds pages of city rows, then builds the ranking. */
    static final class Aggregator {
        // neighborhood -> case number -> merged case
        private final Map<String, Map<String, CaseAcc>> byHood = new HashMap<>();

        void add(JsonNode records) {
            for (JsonNode r : records) {
                if (!Categories.SAFETY.equals(Categories.bucket(text(r, Fields.CASE_TYPE)))) continue;
                String hood = text(r, Fields.NEIGHBORHOOD);
                String caseId = text(r, Fields.CASEFILE);
                if (hood == null || hood.isBlank() || caseId == null || caseId.isBlank()) continue;

                CaseAcc c = byHood.computeIfAbsent(hood.trim(), k -> new HashMap<>())
                        .computeIfAbsent(caseId, k -> new CaseAcc());
                String date = text(r, Fields.DATE);
                if (date != null && date.length() >= 10) {
                    date = date.substring(0, 10);
                    if (c.earliestDate == null || date.compareTo(c.earliestDate) < 0) c.earliestDate = date;
                }
                if (c.address == null) c.address = text(r, Fields.ADDRESS);
                if (Double.isNaN(c.lat)) {
                    double la = number(r, Fields.LATITUDE), lo = number(r, Fields.LONGITUDE);
                    if (!Double.isNaN(la) && !Double.isNaN(lo) && la != 0 && lo != 0) { c.lat = la; c.lon = lo; }
                }
            }
        }

        Snapshot build(LocalDate today, Map<String, Integer> populationByName, int rows, boolean truncated) {
            String cutoff = today.minusYears(ReportService.RECENT_YEARS).toString();
            Map<String, Integer> pop = new HashMap<>();
            populationByName.forEach((k, v) -> pop.put(normalizeName(k), v));

            // The city's table sometimes repeats one population figure for two neighborhoods (a copy
            // error is likely). We can't tell which is right, so we don't compute a rate for either.
            Map<Integer, Integer> figureCount = new HashMap<>();
            pop.values().forEach(v -> figureCount.merge(v, 1, Integer::sum));

            record Tmp(String name, int recent, int older, int addresses, Integer population, Double per1000,
                       boolean populationKnown, String rateNote) {}
            List<Tmp> tmp = new ArrayList<>();
            List<List<double[]>> pointsPerHood = new ArrayList<>();
            for (Map.Entry<String, Map<String, CaseAcc>> e : byHood.entrySet()) {
                int recent = 0, older = 0;
                Set<String> addresses = new HashSet<>();
                List<double[]> pts = new ArrayList<>();
                for (CaseAcc c : e.getValue().values()) {
                    boolean isRecent = c.earliestDate == null || c.earliestDate.compareTo(cutoff) >= 0;
                    if (isRecent) recent++; else older++;
                    if (c.address != null) addresses.add(c.address);
                    if (!Double.isNaN(c.lat)) pts.add(new double[]{c.lat, c.lon});
                }
                Integer population = lookupPopulation(e.getKey(), pop);
                Double per1000 = null;
                String rateNote = null;
                if (population == null) {
                    rateNote = "population not found";
                } else if (population < MIN_POPULATION_FOR_RATE) {
                    rateNote = "small population, so a rate would be misleading";
                } else if (figureCount.get(population) > 1) {
                    rateNote = "the city's population table lists the same figure for two neighborhoods, so we don't trust it";
                } else {
                    per1000 = Math.round(recent * 10000.0 / population) / 10.0;
                }
                tmp.add(new Tmp(e.getKey(), recent, older, addresses.size(), population, per1000, population != null, rateNote));
                pointsPerHood.add(pts);
            }

            // Rank per 1,000 residents if we found a population for at least 90% of neighborhoods;
            // otherwise fall back to raw counts.
            long known = tmp.stream().filter(Tmp::populationKnown).count();
            boolean perCapita = !tmp.isEmpty() && known >= Math.ceil(tmp.size() * 0.9);
            Comparator<Tmp> order = perCapita
                    ? Comparator.comparing((Tmp t) -> t.per1000() == null ? -1.0 : t.per1000()).reversed()
                            .thenComparing(Comparator.comparingInt(Tmp::recent).reversed())
                    : Comparator.comparingInt(Tmp::recent).reversed();
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < tmp.size(); i++) idx.add(i);
            idx.sort((a, b) -> order.compare(tmp.get(a), tmp.get(b)));

            List<Hood> hoods = new ArrayList<>();
            int rank = 0;
            int[] newIndexOf = new int[tmp.size()];
            for (int pos = 0; pos < idx.size(); pos++) {
                Tmp t = tmp.get(idx.get(pos));
                Integer r = null;
                if (!perCapita || t.per1000() != null) r = ++rank;
                hoods.add(new Hood(t.name(), t.recent(), t.older(), t.addresses(), t.population(), t.per1000(), r,
                        perCapita ? t.rateNote() : null));
                newIndexOf[idx.get(pos)] = pos;
            }

            int n = pointsPerHood.stream().mapToInt(List::size).sum();
            double[] lat = new double[n], lon = new double[n];
            int[] hoodIndex = new int[n];
            int k = 0;
            for (int old = 0; old < pointsPerHood.size(); old++) {
                for (double[] p : pointsPerHood.get(old)) {
                    lat[k] = p[0]; lon[k] = p[1]; hoodIndex[k] = newIndexOf[old]; k++;
                }
            }
            return new Snapshot(today, hoods, perCapita ? "per1000" : "count", rows, truncated, lat, lon, hoodIndex);
        }
    }

    // ---------------------------------------------------------------- population (2020 census, from the city)

    public record PopulationResult(Map<String, Integer> byName, String nameColumn, String populationColumn) {}

    /**
     * Reads the city's 2010/2020 neighborhood population table. We have NOT yet seen its real column
     * names, so this guesses them and reports what it picked (see /api/debug/population). If the guess
     * is wrong we simply get no populations and rank by counts instead.
     * `result` is the "result" object of a datastore_search response (it has "fields" and "records").
     */
    static PopulationResult parsePopulation(JsonNode result) {
        String nameCol = null, popCol = null;
        for (JsonNode f : result.path("fields")) {
            String id = f.path("id").asText("");
            String low = id.toLowerCase();
            if (low.equals("_id") || low.equals("_full_text")) continue;
            if (nameCol == null && (low.contains("neighborhood") || low.contains("hood") || low.equals("name"))) nameCol = id;
            boolean is2020 = low.contains("2020");
            boolean looksTotal = low.contains("total") || low.contains("pop");
            boolean isBreakdown = low.matches(".*(change|pct|percent|white|black|asian|hispanic|latino|race|native|hawaiian|other|two|multi|aian|nhpi|under|over|adult|voting|housing|vacan|occupied).*");
            if (is2020 && looksTotal && !isBreakdown) {
                if (popCol == null || (low.contains("total") && !popCol.toLowerCase().contains("total"))) popCol = id;
            }
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        if (nameCol != null && popCol != null) {
            for (JsonNode r : result.path("records")) {
                String name = r.path(nameCol).asText("");
                double value = number(r, popCol);
                if (!name.isBlank() && !Double.isNaN(value) && value > 0) out.put(name.trim(), (int) Math.round(value));
            }
        }
        return new PopulationResult(out, nameCol, popCol);
    }

    // ---------------------------------------------------------------- rental search links

    public record Link(String label, String url, String note) {}

    /**
     * Plain search links to other websites. We do NOT list, copy or verify any rentals ourselves:
     * live listings belong to those sites. The Pitt marketplace and office are official student resources.
     */
    public static List<Link> rentalLinks(String neighborhood) {
        String place = neighborhood + " Pittsburgh PA";
        String q = URLEncoder.encode(place, StandardCharsets.UTF_8);
        List<Link> links = new ArrayList<>();
        links.add(new Link("Pitt Off-Campus Housing Marketplace", "https://listings.ocl.pitt.edu/listing",
                "Listings for University of Pittsburgh students"));
        links.add(new Link("Pitt Off-Campus Student Services", "https://www.ocl.pitt.edu/",
                "Advice on renting and leases"));
        links.add(new Link("Search Google for rentals in " + neighborhood,
                "https://www.google.com/search?q=" + URLEncoder.encode("apartments for rent " + place, StandardCharsets.UTF_8), null));
        links.add(new Link("Craigslist Pittsburgh apartments",
                "https://pittsburgh.craigslist.org/search/apa?query=" + URLEncoder.encode(neighborhood, StandardCharsets.UTF_8),
                "Be careful of scams: never pay before seeing a place"));
        links.add(new Link("Zillow rentals in Pittsburgh", "https://www.zillow.com/pittsburgh-pa/rentals/", null));
        return links;
    }

    // ---------------------------------------------------------------- small helpers

    /** Lower-case, no punctuation, no "(Downtown)"-style extras, so the two city tables' spellings line up. */
    static String normalizeName(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("\\(.*?\\)", " ").replace("&", "and")
                .replaceAll("[^a-z0-9]+", " ").trim();
    }

    /**
     * Finds a neighborhood's population by name. The two city tables spell a few names differently
     * ("Central Business District" vs "Central Business District (Downtown)", "Arlington" vs
     * "Arlington - Arlington Heights (Combined)", "Spring Hill-City View" vs "Spring Hill-City"), so if there
     * is no exact match we accept a name that starts with the other, but only if exactly one does.
     * `pop` keys must already be normalized.
     */
    static Integer lookupPopulation(String hoodName, Map<String, Integer> pop) {
        String key = normalizeName(hoodName);
        Integer exact = pop.get(key);
        if (exact != null) return exact;
        Integer found = null;
        for (Map.Entry<String, Integer> e : pop.entrySet()) {
            String other = e.getKey();
            if (other.startsWith(key + " ") || key.startsWith(other + " ")) {
                if (found != null) return null;   // ambiguous: don't guess
                found = e.getValue();
            }
        }
        return found;
    }

    /** Straight-line distance in km (haversine formula). Good enough for "nearest neighborhood". */
    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    private static String text(JsonNode r, String field) {
        JsonNode n = r.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asText();
    }

    /** Reads a number that may arrive as a JSON number or as text like "5,123". NaN if it isn't one. */
    private static double number(JsonNode r, String field) {
        JsonNode n = r.path(field);
        if (n.isMissingNode() || n.isNull()) return Double.NaN;
        if (n.isNumber()) return n.asDouble();
        try {
            return Double.parseDouble(n.asText().replace(",", "").trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
