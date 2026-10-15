package org.sattva.educationlaw;

public record Act(
        String id,
        String category,
        String level,
        String actName,
        Integer year,
        String purpose,
        String jurisdiction) {
}
