package com.demo.resume.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("job_descriptions")
public class JobDescription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private String requirements;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
