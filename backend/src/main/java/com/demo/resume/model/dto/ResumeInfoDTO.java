package com.demo.resume.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class ResumeInfoDTO {
    private Long resumeId;
    private String name;
    private Integer yearsOfExperience;
    private List<String> skills;
    private List<ProjectDTO> projects;
    private List<EducationDTO> education;

    @Data
    public static class ProjectDTO {
        private String name;
        private String role;
        private String description;
        private List<String> techStack;
    }

    @Data
    public static class EducationDTO {
        private String degree;
        private String major;
        private String school;
        private String graduationYear;
    }
}
