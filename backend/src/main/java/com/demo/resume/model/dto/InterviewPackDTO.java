package com.demo.resume.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class InterviewPackDTO {
    private Long resumeId;
    private String candidateName;
    private List<QuestionDTO> questions;
    private List<FollowUpQuestionDTO> followUpQuestions;

    @Data
    public static class QuestionDTO {
        private Integer id;
        private String question;
        private String category;
        private Integer difficulty;
        private List<String> keyPoints;
        private String scoringCriteria;
        private String type;
    }

    @Data
    public static class FollowUpQuestionDTO {
        private Integer id;
        private String question;
        private String context;
        private String reason;
    }
}
