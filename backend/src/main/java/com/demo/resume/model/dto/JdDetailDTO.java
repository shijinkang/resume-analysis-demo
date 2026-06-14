package com.demo.resume.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JdDetailDTO {
    private Long id;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
