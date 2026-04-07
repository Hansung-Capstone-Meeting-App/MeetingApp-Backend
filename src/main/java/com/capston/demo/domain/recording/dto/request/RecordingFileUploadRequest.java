package com.capston.demo.domain.recording.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

/**
 * Swagger UI 파일 업로드 표시용 DTO
 */
public class RecordingFileUploadRequest {

    @Schema(type = "string", format = "binary", description = "업로드할 음성 파일 (.mp3, .m4a)")
    public MultipartFile file;
}
