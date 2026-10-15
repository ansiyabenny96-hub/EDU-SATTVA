package org.sattva.educationlaw;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sattva.scrape-on-start", havingValue = "true")
public class MinistryActsScraper implements ApplicationRunner {
    private static final String CATALOG_URL =
            "https://www.india.gov.in/my-government/acts-and-rules?ministry=Ministry%20of%20Education";
    private static final Pattern YEAR_PATTERN = Pattern.compile("(19|20)\\d{2}");

    private final ActService actService;

    public MinistryActsScraper(ActService actService) {
        this.actService = actService;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        List<Map<String, Object>> acts = scrape();
        int count = actService.replaceActs(acts);
        System.out.printf("Saved %d items from the official Ministry of Education catalog.%n", count);
    }

    List<Map<String, Object>> scrape() throws IOException {
        Element document = Jsoup.connect(CATALOG_URL)
                .userAgent("Mozilla/5.0")
                .timeout(30_000)
                .get();
        Elements links = document.select("a[href*='view-casepdf']");
        Set<String> seenTitles = new HashSet<>();
        List<Map<String, Object>> acts = new ArrayList<>();

        for (Element link : links) {
            String title = cleanText(link.text());
            if (title.isBlank() || !seenTitles.add(title)) {
                continue;
            }
            Element container = link.parent();
            String summary = "";
            String ministry = "Ministry of Education";
            String department = "";
            String state = "";
            String year = "";

            for (Element sibling : link.nextElementSiblings()) {
                if ("p".equals(sibling.tagName()) && !cleanText(sibling.text()).isBlank()) {
                    summary = cleanText(sibling.text());
                    break;
                }
            }
            if (container != null) {
                for (Element paragraph : container.select("p")) {
                    String text = cleanText(paragraph.text());
                    String lower = text.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("year:")) {
                        year = afterColon(text);
                    } else if (lower.startsWith("ministry:")) {
                        ministry = afterColon(text);
                    } else if (lower.startsWith("department:")) {
                        department = afterColon(text);
                    } else if (lower.startsWith("state:")) {
                        state = afterColon(text);
                    }
                }
            }
            if (year.isBlank()) {
                Matcher matcher = YEAR_PATTERN.matcher(title);
                if (matcher.find()) {
                    year = matcher.group();
                }
            }

            String detail = summary.isBlank() ? title : summary;
            LinkedHashMap<String, Object> act = new LinkedHashMap<>();
            act.put("id", slug(title, acts.size()));
            act.put("title", title);
            act.put("short", title);
            act.put("year", year);
            act.put("citation", title);
            act.put("type", "Central Act");
            act.put("category", "policy");
            act.put("color", "mint-card");
            act.put("icon", "book-open-check");
            act.put("ministry", ministry);
            act.put("department", department);
            act.put("state", state);
            act.put("summary", detail);
            act.put("detail", detail);
            act.put("sourceUrl", link.absUrl("href"));
            act.put("sourceLabel", "Official source");
            act.put("keywords", List.of(title.toLowerCase(Locale.ROOT)));
            acts.add(act);
        }
        return acts;
    }

    private String cleanText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String afterColon(String value) {
        int colon = value.indexOf(':');
        return colon < 0 ? value : value.substring(colon + 1).trim();
    }

    private String slug(String title, int index) {
        String slug = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (slug.length() > 80) {
            slug = slug.substring(0, 80).replaceAll("-$", "");
        }
        return slug.isBlank() ? "act-" + (index + 1) : slug;
    }
}
