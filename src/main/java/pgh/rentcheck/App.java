package pgh.rentcheck;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point. Starts the web server on http://localhost:7070
 *
 *   /                    -> the web page (src/main/resources/public)
 *   /api/health          -> quick "is it alive" check
 *   /api/report?address= -> real report from city data
 *   /api/report/sample   -> fake report, so the page can be built without real data
 *   /api/debug/fields    -> shows the real column names in the city dataset
 *   /api/neighborhoods   -> neighborhoods ranked by unresolved building/fire safety cases
 *   /api/neighborhoods/near?lat=&lon= -> the neighborhoods closest to a spot, with rental search links
 */
public class App {
    private static final String NEIGHBORHOOD_NOTE =
            "This ranks housing conditions from city inspection records, not crime: the city's open crime data "
            + "stopped updating in November 2023. Counts depend on how many inspections happen, and a "
            + "neighborhood with more cases is not necessarily unsafe. Cases include commercial and "
            + "mixed-use buildings, so busy business districts can rank high. Where shown, per-1,000 rates "
            + "use 2020 Census population.";

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));

        WprdcClient wprdc = new WprdcClient();
        ReportService reports = new ReportService(wprdc);
        PropertyService properties = new PropertyService(wprdc);
        NeighborhoodService hoods = new NeighborhoodService(wprdc);
        hoods.refreshIfNeeded();   // start the big city download in the background right away

        Javalin app = Javalin.create(config ->
                config.staticFiles.add("/public", Location.CLASSPATH)
        ).start(port);

        // /api/health also says WHEN this server started and what it can do, so we can tell at a glance
        // whether the browser is talking to an old copy that is still running from an earlier build.
        String startedAt = java.time.ZonedDateTime.now().toString();
        app.get("/api/health", ctx -> ctx.json(Map.of(
                "status", "ok",
                "startedAt", startedAt,
                "features", List.of("report", "questions", "neighborhoods", "neighborhoods/near",
                        "debug/peek", "debug/hotspots", "debug/population"))));

        app.get("/api/report/sample", ctx -> ctx.json(ReportService.sample()));

        app.get("/api/report", ctx -> {
            String address = ctx.queryParam("address");
            if (address == null || address.isBlank()) {
                ctx.status(400).json(Map.of("error", "Please provide ?address=..."));
                return;
            }
            ctx.json(reports.buildReport(address.trim()));
        });

        app.get("/api/debug/fields", ctx -> ctx.json(wprdc.describeFields()));

        // e.g. /api/debug/rows?address=1231 Lakewood St  -> the RAW city rows for an address (casefile numbers etc.)
        app.get("/api/debug/rows", ctx -> {
            String address = ctx.queryParam("address");
            if (address == null || address.isBlank()) {
                ctx.status(400).json(Map.of("error", "Please provide ?address=..."));
                return;
            }
            ctx.json(wprdc.searchByAddress(AddressNormalizer.searchTerms(address), 50));
        });

        // e.g. /api/debug/hotspots?neighborhood=Oakland -> addresses with the most open building/fire safety cases
        app.get("/api/debug/hotspots", ctx -> {
            String hood = ctx.queryParam("neighborhood");
            ctx.json(wprdc.hotspots(hood == null ? "" : hood));
        });

        // Neighborhoods ranked by still-unresolved building & fire safety cases from the last 3 years.
        app.get("/api/neighborhoods", ctx -> {
            NeighborhoodService.Snapshot snap = hoods.current();
            if (snap == null) {
                ctx.status(503).json(hoods.status());
                return;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("asOf", snap.asOf().toString());
            out.put("rankedBy", snap.rankedBy());
            out.put("basis", "Building and fire safety cases that are still unresolved and were issued in the last "
                    + ReportService.RECENT_YEARS + " years");
            out.put("rowsScanned", snap.rowsScanned());
            out.put("truncated", snap.truncated());
            out.put("neighborhoods", snap.hoods());
            out.put("note", NEIGHBORHOOD_NOTE);
            ctx.json(out);
        });

        // e.g. /api/neighborhoods/near?lat=40.4444&lon=-79.9532 -> closest neighborhoods + rental search links
        app.get("/api/neighborhoods/near", ctx -> {
            double lat, lon;
            try {
                lat = Double.parseDouble(ctx.queryParam("lat"));
                lon = Double.parseDouble(ctx.queryParam("lon"));
            } catch (Exception e) {
                ctx.status(400).json(Map.of("error", "Please provide ?lat=...&lon=... as numbers"));
                return;
            }
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                ctx.status(400).json(Map.of("error", "lat/lon out of range"));
                return;
            }
            NeighborhoodService.Snapshot snap = hoods.current();
            if (snap == null) {
                ctx.status(503).json(hoods.status());
                return;
            }
            List<NeighborhoodService.Nearby> near = snap.nearby(lat, lon, NeighborhoodService.NEARBY_COUNT);
            if (near.isEmpty()) {
                ctx.status(404).json(Map.of("error", "No neighborhood data with coordinates yet."));
                return;
            }
            List<Map<String, Object>> items = new java.util.ArrayList<>();
            for (NeighborhoodService.Nearby n : near) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", n.name());
                item.put("distanceKm", n.distanceKm());
                item.put("rank", n.rank());
                item.put("openRecent", n.openRecent());
                item.put("per1000", n.per1000());
                item.put("rentalLinks", NeighborhoodService.rentalLinks(n.name()));
                items.add(item);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("asOf", snap.asOf().toString());
            out.put("rankedBy", snap.rankedBy());
            out.put("totalNeighborhoods", snap.hoods().size());
            out.put("insideCity", near.get(0).distanceKm() <= NeighborhoodService.OUTSIDE_CITY_KM);
            out.put("nearby", items);   // closest first; items[0] is where you are
            out.put("note", NEIGHBORHOOD_NOTE + " Rental links open other websites: we do not list, "
                    + "check or endorse any rental.");
            ctx.json(out);
        });

        // Shows the city's population table and which columns we picked (we had not seen its real column names).
        app.get("/api/debug/population", ctx -> {
            JsonNode result = wprdc.population().path("result");
            NeighborhoodService.PopulationResult parsed = NeighborhoodService.parsePopulation(result);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("fields", result.path("fields"));
            out.put("firstRows", result.path("records").size() > 3
                    ? List.of(result.path("records").get(0), result.path("records").get(1), result.path("records").get(2))
                    : result.path("records"));
            out.put("pickedNameColumn", parsed.nameColumn());
            out.put("pickedPopulationColumn", parsed.populationColumn());
            out.put("parsedCount", parsed.byName().size());
            out.put("parsed", parsed.byName());
            ctx.json(out);
        });

        // e.g. /api/debug/property?address=1231 Lakewood St -> what the COUNTY knows about the building
        app.get("/api/debug/property", ctx -> {
            String address = ctx.queryParam("address");
            if (address == null || address.isBlank()) {
                ctx.status(400).json(Map.of("error", "Please provide ?address=..."));
                return;
            }
            String normalized = AddressNormalizer.normalize(address);
            String[] parts = PropertyService.splitHouseAndStreet(normalized);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("normalized", normalized);
            out.put("houseNumber", parts == null ? null : parts[0]);
            out.put("streetName", parts == null ? null : parts[1]);
            if (parts == null) {
                out.put("error", "Could not read a house number and street from that address.");
                ctx.json(out);
                return;
            }
            JsonNode result = wprdc.parcelsByAddress(parts[0], parts[1]).path("result");
            out.put("matchCount", result.path("records").size());
            out.put("facts", PropertyService.summarize(result.path("records"), "address"));
            out.put("rawFirstRow", result.path("records").size() > 0 ? result.path("records").get(0) : null);
            ctx.json(out);
        });

        // e.g. /api/debug/peek?package=property-assessments -> resources + real column names + 2 example rows
        app.get("/api/debug/peek", ctx -> {
            String pkg = ctx.queryParam("package");
            ctx.json(wprdc.peek(pkg == null ? "" : pkg));
        });

        // e.g. /api/debug/distinct?field=investigation_outcome  -> every real value + how common
        app.get("/api/debug/distinct", ctx -> {
            String field = ctx.queryParam("field");
            ctx.json(wprdc.distinct(field == null ? "" : field));
        });

        // Any crash inside a route becomes a readable JSON error instead of a blank page.
        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace();
            ctx.status(502).json(Map.of("error", String.valueOf(e.getMessage())));
        });
    }
}
