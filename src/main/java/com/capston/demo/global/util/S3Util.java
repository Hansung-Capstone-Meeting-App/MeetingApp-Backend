package com.capston.demo.global.util;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@Component
public class S3Util {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".m4a", ".mp3");

    // recordings/{meetingId}/{uuid}.{ext}
    public String generateKey(Long meetingId, String originalFilename) {
        String ext = extractExtension(originalFilename);
        return "recordings/" + meetingId + "/" + UUID.randomUUID() + ext;
    }

    public void validateAudioFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("파일명이 없습니다.");
        }
        String ext = extractExtension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다. 허용: .m4a, .mp3");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
        }
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            throw new IllegalArgumentException("확장자가 없는 파일입니다.");
        }
        return filename.substring(dotIndex).toLowerCase();
    }
}
