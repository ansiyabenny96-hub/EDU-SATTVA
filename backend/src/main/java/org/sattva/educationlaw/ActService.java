package org.sattva.educationlaw;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ActService {
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "about", "an", "and", "are", "can", "do", "does", "for", "from", "how",
            "i", "in", "is", "it", "me", "of", "on", "please", "say", "the", "tell", "to",
            "under", "what", "when", "where", "which", "who", "why", "with");
    private static final Map<String, List<String>> SEARCH_SYNONYMS = searchSynonyms();
    private static final String INDIA_CODE_URL = "https://www.indiacode.nic.in/";

    private final ObjectMapper objectMapper;
    private final Path dataFile;
    private List<Act> acts;

    public ActService(
            ObjectMapper objectMapper,
            @Value("${sattva.data-file:data/acts.json}") String dataFile) throws IOException {
        this.objectMapper = objectMapper;
        this.dataFile = Path.of(dataFile).toAbsolutePath().normalize();
        this.acts = loadActs();
    }

    public synchronized List<Act> getAllActs() {
        return List.copyOf(acts);
    }

    public synchronized Optional<Act> getActById(String id) {
        return acts.stream().filter(act -> act.id().equalsIgnoreCase(id)).findFirst();
    }

    public synchronized List<Act> getActsByCategory(String category) {
        String normalizedCategory = normalize(category);
        return acts.stream()
                .filter(act -> normalize(act.category()).equals(normalizedCategory))
                .toList();
    }

    public synchronized List<Act> searchActs(String query) {
        List<String> queryTerms = tokens(query);
        if (queryTerms.isEmpty()) {
            return getAllActs();
        }
        return acts.stream().filter(act -> matchesQuery(act, queryTerms)).toList();
    }

    public synchronized Map<String, Object> listActs(String query, String ministry, String category) {
        String normalizedMinistry = normalize(ministry);
        String normalizedCategory = normalize(category);
        List<Act> filtered = (query == null || query.isBlank()) ? acts : searchActs(query);
        List<Map<String, Object>> items = filtered.stream()
                .filter(act -> normalizedMinistry.isEmpty()
                        || normalize(act.jurisdiction()).contains(normalizedMinistry))
                .filter(act -> normalizedCategory.isEmpty()
                        || legacyCategory(act).equals(normalizedCategory))
                .map(this::toLegacyAct)
                .toList();
        return Map.of("count", items.size(), "items", items);
    }

    public synchronized Map<String, Object> summary() {
        TreeSet<String> jurisdictions = new TreeSet<>();
        TreeSet<String> categories = new TreeSet<>();
        for (Act act : acts) {
            if (!act.jurisdiction().isBlank()) {
                jurisdictions.add(act.jurisdiction());
            }
            if (!act.category().isBlank()) {
                categories.add(act.category());
            }
        }
        return Map.of(
                "count", acts.size(),
                "ministries", List.copyOf(jurisdictions),
                "categories", List.copyOf(categories));
    }

    public synchronized int replaceActs(List<?> incoming) throws IOException {
        List<Act> replacement = new ArrayList<>();
        for (Object value : incoming) {
            if (value instanceof Map<?, ?> record) {
                replacement.add(normalizeAct(record, replacement.size()));
            }
        }
        saveActs(replacement);
        acts = replacement;
        return acts.size();
    }

    private List<Act> loadActs() throws IOException {
        if (!Files.exists(dataFile)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> loaded = objectMapper.readValue(
                dataFile.toFile(), new TypeReference<>() {});
        if (loaded == null) {
            return new ArrayList<>();
        }
        List<Act> normalized = new ArrayList<>();
        for (Map<String, Object> record : loaded) {
            normalized.add(normalizeAct(record, normalized.size()));
        }
        return normalized;
    }

    private void saveActs(List<Act> records) throws IOException {
        Path parent = dataFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporaryFile = Files.createTempFile(parent, "acts-", ".json.tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryFile.toFile(), records);
            try {
                Files.move(temporaryFile, dataFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private boolean matchesQuery(Act act, List<String> queryTerms) {
        String searchable = normalize(String.join(" ",
                act.id(), act.category(), act.level(), act.actName(), act.purpose(), act.jurisdiction(),
                String.valueOf(act.year())));
        for (String token : queryTerms) {
            if (termMatches(searchable, token)) {
                return true;
            }
            for (String related : SEARCH_SYNONYMS.getOrDefault(token, List.of())) {
                if (termMatches(searchable, related)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean termMatches(String searchable, String term) {
        String normalizedTerm = normalize(term);
        if (searchable.contains(normalizedTerm)) {
            return true;
        }
        return normalizedTerm.length() >= 4
                && java.util.Arrays.stream(searchable.split("[^a-z0-9]+"))
                        .anyMatch(word -> word.startsWith(normalizedTerm));
    }

    private List<String> tokens(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(normalize(value).split("[^a-z0-9]+"))
                .filter(token -> token.length() > 1 && !STOP_WORDS.contains(token))
                .distinct()
                .toList();
    }

    private static Map<String, List<String>> searchSynonyms() {
        Map<String, List<String>> synonyms = new HashMap<>();
        synonyms.put("education", List.of("educational", "learning", "school", "student", "teacher", "child", "rights"));
        synonyms.put("school", List.of("schools", "classroom", "classrooms", "student", "students",
                "teacher", "teachers", "education"));
        synonyms.put("student", List.of("students", "pupil", "pupils", "learner", "learners"));
        synonyms.put("teacher", List.of("teachers", "educator", "educators", "faculty"));
        synonyms.put("admission", List.of("admissions", "enrolment", "enrollment", "reservation", "access"));
        synonyms.put("admissions", List.of("admission", "enrolment", "enrollment", "reservation", "access"));
        synonyms.put("right", List.of("rights", "fundamental", "constitutional", "access", "inclusion"));
        synonyms.put("rights", List.of("right", "fundamental", "constitutional", "access", "inclusion"));
        synonyms.put("university", List.of("universities", "higher education", "ugc"));
        synonyms.put("universities", List.of("university", "higher education", "ugc"));
        synonyms.put("disability", List.of("disabled", "accessibility", "inclusive", "accommodation"));
        synonyms.put("ragging", List.of("anti-ragging", "harassment", "student safety"));
        synonyms.put("rte", List.of("right to education", "free education", "elementary education"));
        synonyms.put("pocso", List.of("child protection", "safeguarding", "child safety"));
        synonyms.put("nep", List.of("national education policy", "curriculum"));
        return Map.copyOf(synonyms);
    }

    private Act normalizeAct(Map<?, ?> incoming, int index) {
        String actName = firstNonBlank(incoming.get("actName"), incoming.get("title"), "Untitled Act");
        String id = firstNonBlank(incoming.get("id"), slug(actName, index));
        String level = firstNonBlank(incoming.get("level"), legacyLevel(incoming));
        String category = normalizeCategory(firstNonBlank(
                incoming.get("category"), incoming.get("type"), "Central Act"), level);
        String purpose = firstNonBlank(incoming.get("purpose"), incoming.get("summary"),
                incoming.get("detail"), actName);
        String jurisdiction = firstNonBlank(incoming.get("jurisdiction"), incoming.get("ministry"),
                incoming.get("state"), "India");
        Integer year = parseYear(incoming.get("year"), actName);
        return new Act(id, category, level, actName, year, purpose, jurisdiction);
    }

    private Map<String, Object> toLegacyAct(Act act) {
        String category = legacyCategory(act);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("id", act.id());
        result.put("title", act.actName());
        result.put("short", act.actName().replaceFirst("^The ", "").replaceFirst(",? \\d{4}$", ""));
        result.put("year", act.year() == null ? "" : act.year().toString());
        result.put("citation", act.actName());
        result.put("type", act.category());
        result.put("category", category);
        result.put("color", "mint-card");
        result.put("icon", "book-open-check");
        result.put("ministry", act.jurisdiction());
        result.put("department", act.level());
        result.put("summary", act.purpose());
        result.put("detail", act.purpose());
        result.put("sourceUrl", INDIA_CODE_URL);
        result.put("sourceLabel", "India Code");
        result.put("keywords", List.of(act.category(), act.level(), act.actName(), act.purpose(), act.jurisdiction()));
        return result;
    }

    private String legacyCategory(Act act) {
        String category = normalize(act.category());
        if (category.contains("state")) {
            return "policy";
        }
        if (category.contains("constitutional")) {
            return "foundational";
        }
        if (act.actName().toLowerCase(Locale.ROOT).contains("disabil")) {
            return "inclusive";
        }
        if (act.actName().toLowerCase(Locale.ROOT).contains("child") ||
                act.actName().toLowerCase(Locale.ROOT).contains("pocso")) {
            return "safeguarding";
        }
        return "policy";
    }

    private String legacyLevel(Map<?, ?> incoming) {
        String state = text(incoming.get("state"));
        if (!state.isBlank() && !state.equalsIgnoreCase("CENTRAL")) {
            return "State";
        }
        return "Central";
    }

    private String normalizeCategory(String value, String level) {
        return switch (normalize(value)) {
            case "constitutional provision" -> "Constitutional Provision";
            case "central act" -> "Central Act";
            case "ini act" -> "INI Act";
            case "central university act" -> "Central University Act";
            case "state act" -> "State Act";
            case "policy", "foundational", "inclusive", "safeguarding" ->
                    "State".equalsIgnoreCase(level) ? "State Act" : "Central Act";
            default -> value;
        };
    }

    private Integer parseYear(Object value, String actName) {
        try {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value != null && !value.toString().isBlank()) {
                return Integer.valueOf(value.toString());
            }
        } catch (NumberFormatException exception) {
            // Fall through to extracting the year from the act name.
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(18|19|20)\\d{2}").matcher(actName);
        return matcher.find() ? Integer.valueOf(matcher.group()) : null;
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        return "";
    }

    private String slug(String value, int index) {
        String slug = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return slug.isBlank() ? "act-" + (index + 1) : slug;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
