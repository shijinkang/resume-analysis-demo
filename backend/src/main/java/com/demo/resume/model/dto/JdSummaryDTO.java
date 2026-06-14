package com.demo.resume.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JdSummaryDTO {
    private Long id;
    private String title;
    private LocalDateTime createdAt;
}
