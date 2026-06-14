package com.demo.resume.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.resume.event.ResumeUploadedEvent;
import com.demo.resume.model.dto.JdDetailDTO;
import com.demo.resume.model.dto.JdSummaryDTO;
import com.demo.resume.model.dto.JdUpdateDTO;
import com.demo.resume.model.dto.ResumeDetailDTO;
import com.demo.resume.model.dto.ResumeSummaryDTO;
import com.demo.resume.model.dto.ResumeUpdateDTO;
import com.demo.resume.model.dto.UploadResultDTO;
import com.demo.resume.model.entity.JobDescription;
import com.demo.resume.model.entity.Resume;
import com.demo.resume.repository.JobDescriptionRepo;
import com.demo.resume.repository.ResumeRepo;
import com.demo.resume.service.DocumentParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".docx");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

    private final DocumentParseService documentParseService;
    private final JobDescriptionRepo jobDescriptionRepo;
    private final ResumeRepo resumeRepo;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @GetMapping("/jd/list")
    public ResponseEntity<List<JdSummaryDTO>> listJds() {
        LambdaQueryWrapper<JobDescription> wrapper = new LambdaQueryWrapper<JobDescription>()
                .orderByDesc(JobDescription::getCreatedAt);
        List<JdSummaryDTO> list = jobDescriptionRepo.selectList(wrapper).stream()
                .map(jd -> {
                    JdSummaryDTO dto = new JdSummaryDTO();
                    dto.setId(jd.getId());
                    dto.setTitle(jd.getTitle());
                    dto.setCreatedAt(jd.getCreatedAt());
                    return dto;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/resumes/list")
    public ResponseEntity<List<ResumeSummaryDTO>> listResumes() {
        LambdaQueryWrapper<Resume> wrapper = new LambdaQueryWrapper<Resume>()
                .orderByDesc(Resume::getCreatedAt);
        List<ResumeSummaryDTO> list = resumeRepo.selectList(wrapper).stream()
                .map(r -> {
                    ResumeSummaryDTO dto = new ResumeSummaryDTO();
                    dto.setId(r.getId());
                    dto.setFileName(r.getFileName());
                    dto.setCreatedAt(r.getCreatedAt());
                    return dto;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/jd/{id}")
    public ResponseEntity<JdDetailDTO> getJd(@PathVariable Long id) {
        JobDescription jd = jobDescriptionRepo.selectById(id);
        if (jd == null) {
            return ResponseEntity.notFound().build();
        }
        JdDetailDTO dto = new JdDetailDTO();
        dto.setId(jd.getId());
        dto.setTitle(jd.getTitle());
        dto.setContent(jd.getContent());
        dto.setCreatedAt(jd.getCreatedAt());
        dto.setUpdatedAt(jd.getUpdatedAt());
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/jd/{id}")
    public ResponseEntity<JdDetailDTO> updateJd(@PathVariable Long id, @RequestBody JdUpdateDTO request) {
        JobDescription jd = jobDescriptionRepo.selectById(id);
        if (jd == null) {
            return ResponseEntity.notFound().build();
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("JD 内容不能为空");
        }
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            jd.setTitle(request.getTitle());
        }
        jd.setContent(request.getContent());
        jobDescriptionRepo.updateById(jd);

        JdDetailDTO dto = new JdDetailDTO();
        dto.setId(jd.getId());
        dto.setTitle(jd.getTitle());
        dto.setContent(jd.getContent());
        dto.setCreatedAt(jd.getCreatedAt());
        dto.setUpdatedAt(jd.getUpdatedAt());
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/jd/{id}")
    public ResponseEntity<Void> deleteJd(@PathVariable Long id) {
        JobDescription jd = jobDescriptionRepo.selectById(id);
        if (jd == null) {
            return ResponseEntity.notFound().build();
        }
        jobDescriptionRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/resumes/{id}")
    public ResponseEntity<ResumeDetailDTO> getResume(@PathVariable Long id) {
        Resume resume = resumeRepo.selectById(id);
        if (resume == null) {
            return ResponseEntity.notFound().build();
        }
        ResumeDetailDTO dto = new ResumeDetailDTO();
        dto.setId(resume.getId());
        dto.setFileName(resume.getFileName());
        dto.setRawText(resume.getRawText());
        dto.setCreatedAt(resume.getCreatedAt());
        dto.setUpdatedAt(resume.getUpdatedAt());
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/resumes/{id}")
    public ResponseEntity<ResumeDetailDTO> updateResume(@PathVariable Long id, @RequestBody ResumeUpdateDTO request) {
        Resume resume = resumeRepo.selectById(id);
        if (resume == null) {
            return ResponseEntity.notFound().build();
        }
        if (request.getRawText() == null || request.getRawText().isBlank()) {
            throw new IllegalArgumentException("简历内容不能为空");
        }
        resume.setRawText(request.getRawText());
        resumeRepo.updateById(resume);

        ResumeDetailDTO dto = new ResumeDetailDTO();
        dto.setId(resume.getId());
        dto.setFileName(resume.getFileName());
        dto.setRawText(resume.getRawText());
        dto.setCreatedAt(resume.getCreatedAt());
        dto.setUpdatedAt(resume.getUpdatedAt());
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/resumes/{id}")
    public ResponseEntity<Void> deleteResume(@PathVariable Long id) {
        Resume resume = resumeRepo.selectById(id);
        if (resume == null) {
            return ResponseEntity.notFound().build();
        }
        if (resume.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(resume.getFilePath()));
            } catch (IOException e) {
                log.warn("删除简历文件失败: {}", resume.getFilePath(), e);
            }
        }
        resumeRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/jd")
    public ResponseEntity<UploadResultDTO> uploadJd(@RequestParam("file") MultipartFile file) {
        UploadResultDTO result = new UploadResultDTO();
        result.setFileName(file.getOriginalFilename());

        try {
            validateFile(file);

            String text = documentParseService.extractText(file);

            JobDescription jd = new JobDescription();
            jd.setTitle(stripExtension(file.getOriginalFilename()));
            jd.setContent(text);
            jobDescriptionRepo.insert(jd);

            result.setId(jd.getId());
            result.setStatus("SUCCESS");
        } catch (IllegalArgumentException e) {
            log.warn("JD 上传文件验证失败: {}", e.getMessage());
            result.setStatus("FAILED");
            result.setMessage(e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (IOException e) {
            log.error("JD 文件解析失败: {}", file.getOriginalFilename(), e);
            result.setStatus("FAILED");
            result.setMessage("文件解析失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/resumes")
    public ResponseEntity<List<UploadResultDTO>> uploadResumes(
            @RequestParam("files") List<MultipartFile> files) {

        List<UploadResultDTO> results = new ArrayList<>();

        for (MultipartFile file : files) {
            UploadResultDTO result = new UploadResultDTO();
            result.setFileName(file.getOriginalFilename());

            try {
                validateFile(file);

                byte[] bytes = file.getBytes();

                String text = documentParseService.extractTextFromBytes(bytes, file.getOriginalFilename());
                String savedPath = saveFileToDisk(bytes, file.getOriginalFilename());

                Resume resume = new Resume();
                resume.setFileName(file.getOriginalFilename());
                resume.setFilePath(savedPath);
                resume.setRawText(text);
                resumeRepo.insert(resume);

                result.setId(resume.getId());
                result.setStatus("SUCCESS");
                eventPublisher.publishEvent(new ResumeUploadedEvent(resume.getId(), text));
            } catch (IllegalArgumentException e) {
                log.warn("简历上传文件验证失败: {}", e.getMessage());
                result.setStatus("FAILED");
                result.setMessage(e.getMessage());
            } catch (IOException e) {
                log.error("简历文件处理失败: {}", file.getOriginalFilename(), e);
                result.setStatus("FAILED");
                result.setMessage("文件处理失败：" + e.getMessage());
            }

            results.add(result);
        }

        return ResponseEntity.ok(results);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过 10MB");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("无效的文件名");
        }

        String ext = filename.substring(filename.lastIndexOf(".")).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("不支持的文件类型：" + ext + "，仅支持 .pdf 和 .docx");
        }
    }

    private String saveFileToDisk(byte[] bytes, String originalFilename) throws IOException {
        Path resumeDir = Paths.get(uploadDir, "resumes").toAbsolutePath().normalize();
        Files.createDirectories(resumeDir);

        String filename = System.currentTimeMillis() + "_" + originalFilename;
        Path dest = resumeDir.resolve(filename);
        Files.copy(new ByteArrayInputStream(bytes), dest);
        return dest.toString();
    }

    private String stripExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf(".");
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
