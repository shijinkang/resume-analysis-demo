package com.demo.resume.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentParseService {

    /**
     * 从 MultipartFile 中提取文本内容
     */
    public String extractText(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        return extractTextFromBytes(file.getBytes(), filename);
    }

    /**
     * 从字节数组中提取文本，根据文件扩展名选择 PDF 或 Word 解析器
     */
    public String extractTextFromBytes(byte[] bytes, String filename) throws IOException {
        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String extension = filename.substring(filename.lastIndexOf(".")).toLowerCase();
        InputStream inputStream = new ByteArrayInputStream(bytes);

        String text = switch (extension) {
            case ".pdf" -> extractFromPdf(inputStream);
            case ".docx" -> extractFromWord(inputStream);
            default -> throw new IllegalArgumentException("不支持的文件类型：" + extension);
        };

        if (text == null || text.isBlank()) {
            log.warn("文件 [{}] 提取到的文本为空，可能是图片型 PDF（扫描件）", filename);
            throw new IllegalArgumentException(
                "无法提取文件文本内容，请确认上传的是文字型 PDF 或 Word 文档（不支持扫描件/图片型 PDF）"
            );
        }

        log.debug("文件 [{}] 提取文本成功，字符数：{}", filename, text.length());
        return text;
    }

    /**
     * 使用 PDFBox 从 PDF 文件中提取文本
     */
    private String extractFromPdf(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * 使用 Apache POI 从 Word 文档中提取文本
     */
    private String extractFromWord(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append("\n");
            }
            return text.toString();
        }
    }
}
