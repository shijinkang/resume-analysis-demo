package com.demo.resume.controller;

import com.demo.resume.model.dto.MatchRequestDTO;
import com.demo.resume.model.dto.MatchResultDTO;
import com.demo.resume.model.dto.ResumeInfoDTO;
import com.demo.resume.model.entity.Resume;
import com.demo.resume.repository.ResumeRepo;
import com.demo.resume.service.MatchingService;
import com.demo.resume.service.ResumeExtractService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final ResumeExtractService resumeExtractService;
    private final MatchingService matchingService;
    private final ResumeRepo resumeRepo;
    private final ObjectMapper objectMapper;

    @PostMapping("/extract/{resumeId}")
    public ResponseEntity<ResumeInfoDTO> extractResume(@PathVariable Long resumeId) {
        Resume resume = resumeRepo.selectById(resumeId);
        if (resume == null) {
            throw new IllegalArgumentException("简历不存在，ID: " + resumeId);
        }
        if (resume.getRawText() == null || resume.getRawText().isBlank()) {
            throw new IllegalArgumentException("简历文本为空，无法提取，ID: " + resumeId);
        }

        String structuredJson = resumeExtractService.extractStructuredInfo(resumeId, resume.getRawText());
        ResumeInfoDTO dto = parseResumeInfo(resumeId, structuredJson);
        dto.setResumeId(resumeId);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/match")
    public ResponseEntity<List<MatchResultDTO>> matchResumes(@RequestBody MatchRequestDTO request) {
        if (request.getJdId() == null) {
            throw new IllegalArgumentException("JD ID 不能为空");
        }
        if (request.getResumeIds() == null || request.getResumeIds().isEmpty()) {
            throw new IllegalArgumentException("简历 ID 列表不能为空");
        }

        List<MatchResultDTO> results = request.getResumeIds().stream()
                .map(resumeId -> matchingService.calculateMatch(request.getJdId(), resumeId))
                .toList();

        return ResponseEntity.ok(results);
    }

    @GetMapping("/result/{resumeId}")
    public ResponseEntity<MatchResultDTO> getResult(@PathVariable Long resumeId,
                                                    @RequestParam Long jdId) {
        MatchResultDTO result = matchingService.calculateMatch(jdId, resumeId);
        return ResponseEntity.ok(result);
    }

    private ResumeInfoDTO parseResumeInfo(Long resumeId, String json) {
        try {
            return objectMapper.readValue(json, ResumeInfoDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("解析结构化简历数据失败，ID: " + resumeId, e);
        }
    }
}
