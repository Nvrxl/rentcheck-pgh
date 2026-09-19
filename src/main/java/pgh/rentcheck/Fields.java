package pgh.rentcheck;

/**
 * Column names in the city's violations dataset.
 * VERIFIED against real data on Sat Sep 19 2026 (from /api/debug/fields).
 *
 * Notes from the real data:
 *  - address looks like "1231 LAKEWOOD ST, Pittsburgh, PA 15220-"
 *  - casefile_number looks like "CF-ES-2026-008155"; the part after "CF-" is the department
 *    (ES = Environmental Services, PLI = Permits Licenses Inspections, DOMI = Mobility & Infrastructure)
 *  - violation_description / violation_code_section are often null; investigation_findings has the text
 *  - investigation_outcome says whether a violation was actually found (e.g. "Violation Found")
 */
public final class Fields {
    private Fields() {}

    public static final String CASEFILE    = "casefile_number";
    public static final String ADDRESS     = "address";
    public static final String PARCEL      = "parcel_id";
    public static final String STATUS      = "status";
    public static final String CASE_TYPE   = "case_file_type";
    public static final String DATE        = "investigation_date";
    public static final String OUTCOME     = "investigation_outcome";
    public static final String FINDINGS    = "investigation_findings";
    public static final String DESCRIPTION = "violation_description";
    public static final String CODE        = "violation_code_section";
    public static final String LATITUDE    = "latitude";
    public static final String LONGITUDE   = "longitude";
    public static final String NEIGHBORHOOD = "neighborhood";
}
