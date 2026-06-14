package com.demo.resume.model.dto;

import lombok.Data;

@Data
public class UploadResultDTO {
    private Long id;
    private String fileName;
    private String status;
    private String message;
}
