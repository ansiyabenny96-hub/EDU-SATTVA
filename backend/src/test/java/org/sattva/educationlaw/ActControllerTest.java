package org.sattva.educationlaw;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActControllerTest {
    @TempDir
    Path temporaryDirectory;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        Path dataFile = temporaryDirectory.resolve("acts.json");
        Files.writeString(dataFile, """
                [
                  {"id":"RTE-001","category":"Central Act","level":"Central","actName":"Right to Education Act, 2009","year":2009,"purpose":"Free and compulsory elementary education","jurisdiction":"India"},
                  {"id":"STATE-001","category":"State Act","level":"State","actName":"School Fee Regulation Act, 2018","year":2018,"purpose":"Regulates school fees","jurisdiction":"Example State"}
                ]
                """);
        ActService service = new ActService(new ObjectMapper(), dataFile.toString());
        mockMvc = MockMvcBuilders.standaloneSetup(new ActController(service)).build();
    }

    @Test
    void exposesAllActsAndReturnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/v1/acts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("RTE-001"))
                .andExpect(jsonPath("$[0].year").value(2009));

        mockMvc.perform(get("/api/v1/acts/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exposesCategoryAndKeywordSearchEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/acts/category/{category}", "State Act"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("STATE-001"));

        mockMvc.perform(get("/api/v1/acts/search").param("query", "school"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void doesNotExposeBulkDatasetReplacementEndpoint() throws Exception {
        mockMvc.perform(post("/api/acts/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acts":[{"actName":"Injected record"}]}
                                """))
                .andExpect(status().isNotFound());
    }
}
