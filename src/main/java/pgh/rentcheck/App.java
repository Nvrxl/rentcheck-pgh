package pgh.rentcheck;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.util.Map;

/**
 * Entry point. Starts the web server on http://localhost:7070
 *
 *   /                    -> the web page (src/main/resources/public)
 *   /api/health          -> quick "is it alive" check
 *   /api/report?address= -> real report from city data
 *   /api/report/sample   -> fake report, so the page can be built without real data
 *   /api/debug/fields    -> shows the real column names in the city dataset
 */
public class App {
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));

        WprdcClient wprdc = new WprdcClient();
        ReportService reports = new ReportService(wprdc);

        Javalin app = Javalin.create(config ->
                config.staticFiles.add("/public", Location.CLASSPATH)
        ).start(port);

        app.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));

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
