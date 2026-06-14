package com.demo.resume.model.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class MatchResultDTO {
    private Long resumeId;
    private String candidateName;
    private Integer score;
    private String reason;
    private Map<String, Integer> dimensions;
    private String recommendation;
    private List<String> strengths;
    private List<String> gaps;
}
