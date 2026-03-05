package com.capston.demo.domain.user.controller;

import com.capston.demo.domain.user.dto.request.RegisterRequestDto;
import com.capston.demo.domain.user.dto.response.RegisterResponseDto;
import com.capston.demo.domain.user.service.S3Service;
import com.capston.demo.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final S3Service s3Service;

    @GetMapping("/register")
    String register() {
        return "register.html";
    }

    @PostMapping("/user") // 회원가입 정보
    @ResponseBody
    public ResponseEntity<RegisterResponseDto> adduser(@Valid @RequestBody RegisterRequestDto requestDto) {
        RegisterResponseDto response = userService.registerUser(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/login")
    public String login() {
        return "login.html";
    }

    // PresignedUrl 발급받기
    @GetMapping("/presigned-url")
    @ResponseBody
    String getURL(@RequestParam String filename) { // 쿼리 스트링 받기
        var result = s3Service.createPresignedUrl("profileImg/" + filename); // PresignedUrl 발급

        return result; // 발급 받은 PresignedUrl을 html에 반환
    }


}
