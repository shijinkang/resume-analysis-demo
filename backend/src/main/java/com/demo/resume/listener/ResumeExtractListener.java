package com.demo.resume.listener;

import com.demo.resume.event.ResumeUploadedEvent;
import com.demo.resume.service.ResumeExtractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "resume.extract.async-enabled", havingValue = "true", matchIfMissing = true)
public class ResumeExtractListener {

    private final ResumeExtractService resumeExtractService;

    @Async
    @EventListener
    public void onResumeUploaded(ResumeUploadedEvent event) {
        log.info("收到简历上传事件，开始异步结构化提取，简历 ID: {}", event.getResumeId());
        try {
            resumeExtractService.extractStructuredInfo(event.getResumeId(), event.getRawText());
        } catch (Exception e) {
            log.error("异步结构化提取失败，简历 ID: {}", event.getResumeId(), e);
        }
    }
}
