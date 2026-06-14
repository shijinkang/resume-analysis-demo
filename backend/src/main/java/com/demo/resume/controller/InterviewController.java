package com.demo.resume.controller;

import com.demo.resume.model.dto.InterviewPackDTO;
import com.demo.resume.service.FollowUpService;
import com.demo.resume.service.QuestionGenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final QuestionGenService questionGenService;
    private final FollowUpService followUpService;

    /**
     * 为简历生成 10+ 道面试题
     */
    @PostMapping("/questions/{resumeId}")
    public ResponseEntity<InterviewPackDTO> generateQuestions(@PathVariable Long resumeId) {
        InterviewPackDTO result = questionGenService.generateQuestions(resumeId);
        return ResponseEntity.ok(result);
    }

    /**
     * 根据简历中的模糊点生成 3-5 道针对性追问题目
     */
    @PostMapping("/followup/{resumeId}")
    public ResponseEntity<InterviewPackDTO> generateFollowUp(@PathVariable Long resumeId) {
        InterviewPackDTO result = followUpService.generateFollowUp(resumeId);
        return ResponseEntity.ok(result);
    }
}
