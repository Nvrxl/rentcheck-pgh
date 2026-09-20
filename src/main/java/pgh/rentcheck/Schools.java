package pgh.rentcheck;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Pittsburgh universities: where the campus is, and the school's OWN off-campus housing office.
 *
 * Why this exists: RentCheck covers the whole city, not just Oakland. A Duquesne student looking
 * on the Bluff should not be sent to Pitt's housing office.
 *
 * Campus coordinates are the campus centre points published in each university's Wikipedia infobox
 * (read Sat Sep 19 2026). They are a single point for a campus that covers many blocks, so treat
 * every distance as approximate - and straight-line, which in Pittsburgh is not walking distance.
 *
 * The housing links are the universities' own pages. We do not list or check any rental ourselves.
 */
public final class Schools {
    private Schools() {}

    /** A campus. `id` is what the page sends us (e.g. "pitt"). */
    public record School(String id, String name, String shortName, double latitude, double longitude,
                         List<NeighborhoodService.Link> housingLinks) {}

    private static final List<School> ALL = List.of(
            new School("pitt", "University of Pittsburgh", "Pitt", 40.4446, -79.9533, List.of(
                    new NeighborhoodService.Link("Pitt Off-Campus Housing Marketplace",
                            "https://listings.ocl.pitt.edu/listing", "The university's own listings site"),
                    new NeighborhoodService.Link("Pitt Off-Campus Student Services",
                            "https://www.ocl.pitt.edu/", "Advice on leases and renting"))),
            new School("cmu", "Carnegie Mellon University", "CMU", 40.4425, -79.9433, List.of(
                    new NeighborhoodService.Link("CMU Off-Campus Housing Marketplace",
                            "https://offcampus.housing.cmu.edu/", "The university's own listings site"),
                    new NeighborhoodService.Link("CMU Off-Campus Housing (Housing & Residential Education)",
                            "https://www.cmu.edu/housing/our-communities/off-campus-housing/index.html", null))),
            new School("duquesne", "Duquesne University", "Duquesne", 40.4361, -79.9931, List.of(
                    new NeighborhoodService.Link("Duquesne Off-Campus Housing Information",
                            "https://www.duq.edu/life-at-duquesne/our-campus/off-campus-housing-info.php", null))));

    public static List<School> all() {
        return ALL;
    }

    /** Finds a campus by id, case-insensitively. Returns null when we don't know it. */
    public static School byId(String id) {
        if (id == null || id.isBlank()) return null;
        String want = id.trim().toLowerCase();
        for (School s : ALL) {
            if (s.id().equals(want)) return s;
        }
        return null;
    }

    /**
     * How far people will usually travel, by how they get around. These set the default radius on
     * the page; the student can always change it. Judgement calls, not measurements.
     */
    public record TravelMode(String id, String label, double defaultMiles, String note) {}

    private static final List<TravelMode> MODES = List.of(
            new TravelMode("walk", "Walking", 1.0, "About a 20 minute walk on flat ground. Pittsburgh is not flat."),
            new TravelMode("bike", "Biking", 3.0, "Includes POGOH bike share range. Check the hills on your route."),
            new TravelMode("transit", "Bus", 5.0, "Most of the city is within a bus ride, but check the route and frequency."),
            new TravelMode("car", "Driving", 8.0, "Remember to ask whether a place comes with parking."));

    public static List<TravelMode> modes() {
        return MODES;
    }

    public static TravelMode modeById(String id) {
        if (id == null || id.isBlank()) return null;
        String want = id.trim().toLowerCase();
        for (TravelMode m : MODES) {
            if (m.id().equals(want)) return m;
        }
        return null;
    }

    /**
     * Housing links for a place: the school's own office first (when we know the school), then
     * general searches. Search links only - we never list or vouch for a rental.
     */
    public static List<NeighborhoodService.Link> housingLinks(String neighborhood, School school) {
        List<NeighborhoodService.Link> links = new ArrayList<>();
        if (school != null) {
            links.addAll(school.housingLinks());
        } else {
            // No school chosen: offer every university's own office, since we cover the whole city.
            for (School s : ALL) {
                links.add(new NeighborhoodService.Link(s.shortName() + " off-campus housing",
                        s.housingLinks().get(0).url(), "The university's own listings site"));
            }
        }
        String place = neighborhood + " Pittsburgh PA";
        links.add(new NeighborhoodService.Link("Search Google for rentals in " + neighborhood,
                "https://www.google.com/search?q=" + URLEncoder.encode("apartments for rent " + place, StandardCharsets.UTF_8), null));
        links.add(new NeighborhoodService.Link("Craigslist Pittsburgh apartments",
                "https://pittsburgh.craigslist.org/search/apa?query=" + URLEncoder.encode(neighborhood, StandardCharsets.UTF_8),
                "Be careful of scams: never pay before seeing a place"));
        links.add(new NeighborhoodService.Link("Zillow rentals in Pittsburgh",
                "https://www.zillow.com/pittsburgh-pa/rentals/", null));
        return links;
    }
}
