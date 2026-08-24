package com.rikkeipay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * EvaluationResult - kết quả chấm điểm của AI Judge.
 *
 * Mỗi tiêu chí là một CriterionScore gồm score (1-5) và reason (lý do).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluationResult(
        CriterionScore accuracy,
        CriterionScore politeness,
        CriterionScore security) {

    /** Điểm của một tiêu chí. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CriterionScore(int score, String reason) {
    }

    /** Điểm trung bình 3 tiêu chí. */
    public double average() {
        return (accuracy.score() + politeness.score() + security.score()) / 3.0;
    }
}
