package com.demo.resume.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class MatchRequestDTO {
    private Long jdId;
    private List<Long> resumeIds;
}
