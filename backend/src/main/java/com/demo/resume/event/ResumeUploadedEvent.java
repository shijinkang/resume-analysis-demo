package com.demo.resume.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ResumeUploadedEvent {

    private final Long resumeId;
    private final String rawText;
}
