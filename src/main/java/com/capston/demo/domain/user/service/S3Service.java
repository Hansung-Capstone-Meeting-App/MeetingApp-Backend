package com.capston.demo.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class S3Service {

    @Value("${cloud.aws.s3.bucket}") //application.properties에 저장한 버킷 이름 가져옴
    private String bucket; // 버킷명을 자동으로 넣어줌
    private final S3Presigner s3Presigner;

    // PresignedURL 발급하는 메소드
    public String createPresignedUrl(String path) { // 경로만 파라미터로 지정
        var putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket) // 올릴 버킷명
                .key(path) // 경로
                .build();
        var preSignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(3)) // URL 유효기간(분)
                .putObjectRequest(putObjectRequest)
                .build();
        return s3Presigner.presignPutObject(preSignRequest).url().toString(); // PresignedURL 발급해줌
    }

}

