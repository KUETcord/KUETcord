package com.kuetcord.cthub.model;

/**
 * Discriminator for academic assessments managed by the unified assessment hub engine
 * ({@code /ct-hub}, {@code /labtest-hub}, and {@code /quiz-hub}).
 */
public enum AssessmentType {
    CT("Class Test"),
    LABTEST("Lab Test"),
    QUIZ("Quiz");

    private final String displayLabel;

    AssessmentType(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String displayLabel() {
        return displayLabel;
    }
}
