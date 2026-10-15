package org.sattva.educationlaw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void getsActsByIdCategoryAndSearchTerm() throws Exception {
        ActService service = serviceWith("""
                [
                  {"id":"CENTRAL-001","category":"Central Act","level":"Central","actName":"Right to Education Act, 2009","year":2009,"purpose":"Free and compulsory school admission and education","jurisdiction":"India"},
                  {"id":"STATE-001","category":"State Act","level":"State","actName":"School Fee Regulation Act, 2018","year":2018,"purpose":"Regulates school fees","jurisdiction":"Example State"}
                ]
                """);

        assertEquals("Right to Education Act, 2009",
                service.getActById("central-001").orElseThrow().actName());
        assertEquals(1, service.getActsByCategory("State Act").size());
        assertEquals(1, service.searchActs("admiss").size());
        assertEquals(2, service.searchActs("school").size());
        assertTrue(service.getActById("missing").isEmpty());
    }

    @Test
    void keepsDashboardActListingCompatibleWithTheNewSchema() throws Exception {
        ActService service = serviceWith("""
                [
                  {"id":"RTE-001","category":"Central Act","level":"Central","actName":"Right to Education Act, 2009","year":2009,"purpose":"Free and compulsory elementary education","jurisdiction":"India"}
                ]
                """);

        Map<String, Object> response = service.listActs("education", null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");

        assertEquals(1, response.get("count"));
        assertEquals("Right to Education Act, 2009", items.get(0).get("title"));
        assertTrue(items.get(0).containsKey("summary"));
        assertTrue(items.get(0).containsKey("keywords"));
    }

    @Test
    void replacesLegacyBulkRecordsWithSchemaRecordsAndPersistsThem() throws Exception {
        Path dataFile = temporaryDirectory.resolve("nested/acts.json");
        ActService service = new ActService(new ObjectMapper(), dataFile.toString());

        int count = service.replaceActs(List.of(
                Map.of("id", "RTE-001", "title", "Right to Education Act, 2009",
                        "year", "2009", "summary", "Provides for free elementary education",
                        "ministry", "Government of India"),
                "ignore non-object records"));

        assertEquals(1, count);
        List<Act> savedActs = new ObjectMapper().readValue(
                dataFile.toFile(),
                new com.fasterxml.jackson.core.type.TypeReference<>() {});
        assertEquals(1, savedActs.size());
        assertEquals("Right to Education Act, 2009", savedActs.get(0).actName());
        assertEquals(2009, savedActs.get(0).year());
        assertEquals("Provides for free elementary education", savedActs.get(0).purpose());
    }

    private ActService serviceWith(String json) throws Exception {
        Path dataFile = temporaryDirectory.resolve("acts-" + System.nanoTime() + ".json");
        Files.writeString(dataFile, json);
        return new ActService(new ObjectMapper(), dataFile.toString());
    }
}
