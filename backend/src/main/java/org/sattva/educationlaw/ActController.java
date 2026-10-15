package org.sattva.educationlaw;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class ActController {
    private final ActService actService;

    public ActController(ActService actService) {
        this.actService = actService;
    }

    @GetMapping("/api/acts")
    public Map<String, Object> listActs(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String ministry,
            @RequestParam(required = false) String category) {
        return actService.listActs(q, ministry, category);
    }

    @GetMapping("/api/acts/summary")
    public Map<String, Object> summary() {
        return actService.summary();
    }

    @GetMapping("/api/v1/acts")
    public List<Act> getAllActs() {
        return actService.getAllActs();
    }

    @GetMapping("/api/v1/acts/{id}")
    public ResponseEntity<Act> getActById(@PathVariable String id) {
        return actService.getActById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/api/v1/acts/category/{category}")
    public List<Act> getActsByCategory(
            @PathVariable String category) {
        return actService.getActsByCategory(category);
    }

    @GetMapping("/api/v1/acts/search")
    public List<Act> searchActs(@RequestParam String query) {
        return actService.searchActs(query);
    }
}
