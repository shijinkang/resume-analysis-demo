package com.demo.resume.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResumeDetailDTO {
    private Long id;
    private String fileName;
    private String rawText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
