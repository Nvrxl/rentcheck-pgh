package pgh.rentcheck;

import pgh.rentcheck.Models.ViolationItem;

import java.util.ArrayList;
import java.util.List;

/**
 * "Questions to ask before you sign": plain templates chosen by what the city records mention.
 * NO language models: it is a handful of keyword checks and fixed sentences, so the same records
 * always give the same questions. Questions about problems that are still open come first.
 *
 * The questions are a starting point. They do NOT say the landlord did anything wrong.
 */
public final class Questions {
    private Questions() {}

    static final int MAX_QUESTIONS = 6;

    static List<String> forRecords(List<ViolationItem> items) {
        List<String> out = new ArrayList<>();

        if (items.isEmpty()) {
            out.add("We found no city violation records, which is not a guarantee. Ask when the building was last "
                    + "inspected, and ask to see the inspection report.");
            out.add("Ask whether the property is registered in the City of Pittsburgh's rental registration program.");
            addBasics(out);
            return out;
        }

        long openSafety = items.stream()
                .filter(v -> ReportService.looksOpen(v.status()) && Categories.SAFETY.equals(Categories.bucket(v.code())))
                .count();
        if (openSafety > 0) {
            out.add("The city lists " + openSafety + (openSafety == 1 ? " building or fire safety case" : " building or fire safety cases")
                    + (openSafety == 1 ? " here that looks unresolved" : " here that look unresolved") + ". Ask what has been done about "
                    + (openSafety == 1 ? "it" : "each one") + ", when it will be fixed, and get the answer in writing.");
        }

        // Topic questions, in a fixed order. A topic is added once, and open problems count first.
        String[][] topics = {
                // {keywords (any of, lower case, separated by |), question}
                {"fire alarm|sprinkler|suppression|fire safety|fire extinguisher|smoke|fire escape|egress",
                        "When were the fire alarms, smoke detectors and sprinklers last tested and inspected? Can I see the record?"},
                {"electrical|wiring|outlet",
                        "Has the electrical system been inspected, and were any electrical repairs done with permits?"},
                {"hvac|heat|furnace|boiler|carbon monoxide",
                        "How is the unit heated, who pays for heat, and when was the heating system last serviced?"},
                {"permit",
                        "Was any renovation or construction in the building done with city permits? Can I see them?"},
                {"roof|wall|porch|stair|foundation|structural|retaining wall|ceiling|floor|window|chimney",
                        "The records mention structural problems (roofs, walls, stairs or porches). Have they been repaired, and by whom?"},
                {"sewer|plumb|water damage|water leak|leak|mold",
                        "Have there been leaks, sewer or plumbing problems? Who fixes them and how fast?"},
                {"vacant",
                        "The city lists a vacant property record. Is the building or unit fully occupied and in working order?"},
                {"weeds|refuse|recycl|trash|dump|junk|couch|set out",
                        "Who is responsible for trash, recycling and keeping the outside clean, and how does pickup work?"},
        };
        for (String[] topic : topics) {
            if (mentions(items, topic[0])) out.add(topic[1]);
        }
        // Keep the closing "how are repairs handled" question even when there are many topics.
        List<String> shown = out.size() > MAX_QUESTIONS - 1 ? new ArrayList<>(out.subList(0, MAX_QUESTIONS - 1)) : out;
        addBasics(shown);
        return shown;
    }

    private static void addBasics(List<String> out) {
        out.add("How do I report a repair, and how quickly are repairs usually done? Can I see the most recent inspection report?");
    }

    /** Does any record's type or description contain one of the keywords (separated by |)? */
    private static boolean mentions(List<ViolationItem> items, String keywords) {
        String[] words = keywords.split("\\|");
        for (ViolationItem v : items) {
            String text = ((v.code() == null ? "" : v.code()) + " " + (v.description() == null ? "" : v.description())
                    + " " + (v.codeSection() == null ? "" : v.codeSection())).toLowerCase();
            for (String w : words) if (text.contains(w)) return true;
        }
        return false;
    }
}
