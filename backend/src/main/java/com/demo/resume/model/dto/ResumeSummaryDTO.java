package com.demo.resume.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResumeSummaryDTO {
    private Long id;
    private String fileName;
    private LocalDateTime createdAt;
}
