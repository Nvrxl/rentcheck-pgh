package pgh.rentcheck;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Facts about the BUILDING itself, from Allegheny County's property assessment records
 * (a different dataset from the city's violations). NO language models: a lookup and some counting.
 *
 * Why this matters: the violations data alone can't tell a renter whether an address is a house, a
 * 40-unit apartment building or a restaurant. A restaurant with three fire-safety cases is not the
 * same story as a house with three. Year built and building type give the reader that context.
 *
 * WHAT IS NOT IN HERE: owner names. The county deliberately excludes them (Ordinance 3478-07).
 * We only get the owner TYPE (an individual or a company) and whether someone claimed the
 * homestead tax reduction, which owners only get on a home they live in themselves.
 *
 * Column names below were verified against the real dataset on Sat Sep 19 2026 via
 * /api/debug/peek?package=property-assessments.
 */
public class PropertyService {

    private final WprdcClient wprdc;

    public PropertyService(WprdcClient wprdc) {
        this.wprdc = wprdc;
    }

    /** What we show about a building. Any field can be null: the county's records have gaps. */
    public record PropertyFacts(
            String parcelId,          // county parcel number, or null when several parcels share the address
            String type,              // plain English, e.g. "Residential", "Commercial", "University or college property"
            String countyClass,       // the county's own word: RESIDENTIAL / COMMERCIAL / GOVERNMENT / ... (CLASSDESC)
            Boolean residential,      // true only when the county calls it RESIDENTIAL
            String use,               // the county's land-use category, verbatim (USEDESC), e.g. "RETL/STOR OVER"
            Integer yearBuilt,
            Double stories,
            Integer bedrooms,
            Integer fullBaths,
            Integer livingAreaSqFt,
            String ownerType,         // "an individual" | "a company" | null (names are not published)
            Boolean ownerOccupied,    // true only when the homestead tax reduction is claimed; null = unknown
            String conditionRating,   // the assessor's rating, which can be years out of date
            int parcelsAtAddress,     // >1 means condo/apartment units assessed separately
            String matchedBy,         // "address" | "parcel id"
            String context,           // a sentence when the building is NOT ordinary housing, else null
            String note               // plain-English caveat for the page
    ) {}

    /**
     * The county's CLASSDESC is a TAX classification, so a university building is filed under
     * "GOVERNMENT". Printing that word next to a renter's address would be misleading, so we
     * translate it. We keep the county's own wording in countyClass so nothing is hidden.
     */
    static String friendlyType(String classDesc, String useDesc) {
        if (classDesc == null) return null;
        String c = classDesc.trim().toUpperCase();
        String u = useDesc == null ? "" : useDesc.toUpperCase();
        switch (c) {
            case "RESIDENTIAL": return "Residential";
            case "COMMERCIAL": return "Commercial";
            case "INDUSTRIAL": return "Industrial";
            case "AGRICULTURAL": return "Agricultural";
            case "UTILITIES": return "Utility property";
            case "GOVERNMENT":
                if (u.contains("COLLEGE") || u.contains("UNIV") || u.contains("ACADEMY")) {
                    return "University or college property";
                }
                if (u.contains("CHURCH") || u.contains("RELIGIOUS")) return "Religious organization property";
                if (u.contains("HOSPITAL")) return "Hospital property";
                if (u.contains("SCHOOL")) return "School property";
                return "Government or tax-exempt property";
            case "OTHER": return "Other";
            default: return classDesc.trim();
        }
    }

    /** A warning when the address is not ordinary housing, so violation counts are read correctly. */
    static String context(String classDesc, String useDesc, String friendly) {
        if (classDesc == null) return null;
        String c = classDesc.trim().toUpperCase();
        if (c.equals("RESIDENTIAL")) return null;
        String what = friendly == null ? "not classed as residential" : friendly.toLowerCase();
        if (c.equals("GOVERNMENT")) {
            return "The county lists this as " + what + " for tax purposes, not as housing. City cases here "
                    + "may involve a business or institution that uses the building rather than an apartment. "
                    + "Some university and institutional buildings do contain student housing.";
        }
        return "The county lists this as " + what + ", not as housing. City cases here may involve a business "
                + "in the building rather than an apartment. Mixed buildings with shops below and flats above "
                + "are common, so check which part a case refers to.";
    }

    /**
     * Looks up an address. Returns null when nothing matched (which is common: the county stores the
     * street name separately, so unusual spellings simply miss).
     */
    public PropertyFacts lookupByAddress(String address) throws Exception {
        String normalized = AddressNormalizer.normalize(address);
        String[] parts = splitHouseAndStreet(normalized);
        if (parts == null) return null;
        JsonNode records = wprdc.parcelsByAddress(parts[0], parts[1]).path("result").path("records");
        return summarize(records, "address");
    }

    /** "1231 LAKEWOOD ST" -> ["1231", "LAKEWOOD ST"]; null if it doesn't start with a house number. */
    static String[] splitHouseAndStreet(String normalized) {
        if (normalized == null || normalized.isBlank()) return null;
        String[] tokens = normalized.trim().split("\\s+");
        if (tokens.length < 2 || !tokens[0].matches("\\d+")) return null;
        return new String[]{tokens[0], String.join(" ", java.util.Arrays.copyOfRange(tokens, 1, tokens.length))};
    }

    /**
     * Turns the county's rows into one set of facts. Separate and package-visible so it can be
     * tested without the network.
     *
     * Several parcels can share one street address (a condo building is assessed unit by unit). In
     * that case we report the most common building type and the OLDEST year built, and we leave the
     * room counts out, because "2 bedrooms" would describe one unit and mislead.
     */
    static PropertyFacts summarize(JsonNode records, String matchedBy) {
        List<JsonNode> rows = new ArrayList<>();
        records.forEach(rows::add);
        if (rows.isEmpty()) return null;

        String type = mostCommon(rows, "CLASSDESC");
        String use = mostCommon(rows, "USEDESC");
        Integer yearBuilt = oldestYear(rows);
        String ownerType = ownerType(mostCommon(rows, "OWNERDESC"));
        Boolean ownerOccupied = anyHomestead(rows);

        String friendly = friendlyType(type, use);
        Boolean residential = type == null ? null : type.trim().equalsIgnoreCase("RESIDENTIAL");
        String context = context(type, use, friendly);
        String source = "From Allegheny County assessment records, which describe the property for tax "
                + "purposes. They can be out of date, and they never name the owner.";

        if (rows.size() > 1) {
            String note = rows.size() + " separate parcels share this street address, which usually means "
                    + "units assessed one by one (a condo or apartment building). The details below describe "
                    + "the building as a whole, not one apartment. " + source;
            return new PropertyFacts(null, friendly, type, residential, use, yearBuilt, null, null, null, null,
                    ownerType, ownerOccupied, null, rows.size(), matchedBy, context, note);
        }

        JsonNode r = rows.get(0);
        return new PropertyFacts(
                text(r, "PARID"), friendly, type, residential, use, yearBuilt,
                number(r, "STORIES"),
                integer(r, "BEDROOMS"), integer(r, "FULLBATHS"), integer(r, "FINISHEDLIVINGAREA"),
                ownerType, ownerOccupied, text(r, "CONDITIONDESC"), 1, matchedBy, context,
                // Only houses have these details: the county leaves the dwelling columns empty for
                // commercial, industrial and institutional parcels.
                source + (Boolean.TRUE.equals(residential) ? "" : " The county only records room counts, "
                        + "stories and year built for residential parcels, so those are blank here."));
    }

    /** "REGULAR" and "REGULAR-ETAL" mean a person; "CORPORATION" means a company. */
    static String ownerType(String ownerDesc) {
        if (ownerDesc == null) return null;
        String d = ownerDesc.toUpperCase();
        if (d.startsWith("REGULAR")) return "an individual";
        if (d.contains("CORPORATION")) return "a company";
        return null;
    }

    /**
     * True when ANY parcel at this address claims the homestead reduction, which owners can only get
     * on a home they live in themselves. A missing flag does NOT prove the place is a rental: owners
     * have to apply for it. So we return true or null, never false.
     */
    static Boolean anyHomestead(List<JsonNode> rows) {
        for (JsonNode r : rows) {
            String flag = text(r, "HOMESTEADFLAG");
            if (flag != null && flag.equalsIgnoreCase("HOM")) return true;
        }
        return null;
    }

    static Integer oldestYear(List<JsonNode> rows) {
        Integer oldest = null;
        for (JsonNode r : rows) {
            Integer y = integer(r, "YEARBLT");
            if (y == null || y < 1700 || y > 2100) continue;   // the county has a few junk values
            if (oldest == null || y < oldest) oldest = y;
        }
        return oldest;
    }

    static String mostCommon(List<JsonNode> rows, String field) {
        Map<String, Integer> counts = new HashMap<>();
        for (JsonNode r : rows) {
            String v = text(r, field);
            if (v != null && !v.isBlank()) counts.merge(v.trim(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).orElse(null);
    }

    private static String text(JsonNode r, String field) {
        JsonNode n = r.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        String s = n.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer integer(JsonNode r, String field) {
        Double d = number(r, field);
        return d == null ? null : (int) Math.round(d);
    }

    private static Double number(JsonNode r, String field) {
        JsonNode n = r.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        if (n.isNumber()) return n.asDouble();
        try {
            return Double.parseDouble(n.asText().replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
