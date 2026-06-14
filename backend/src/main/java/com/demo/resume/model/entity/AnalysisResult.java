package com.demo.resume.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("analysis_results")
public class AnalysisResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jdId;

    private Long resumeId;

    private Integer matchScore;

    private String matchReason;

    private String matchDimensions;

    private String questions;

    private String followupQuestions;

    private String recommendation;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
