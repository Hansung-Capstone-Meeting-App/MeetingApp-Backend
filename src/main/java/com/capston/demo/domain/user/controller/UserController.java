package com.capston.demo.domain.user.controller;

import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/register")
    String register() {
        return "register.html";
    }

    @PostMapping("/user")
    @ResponseBody
    String adduser(
            String email,
            String password,
            String displayName) {
        User user = new User();
        var hash = passwordEncoder.encode(password); // 비밀번호는 해싱해서 저장
        user.setEmail(email);
        user.setPassword(hash);
        user.setName(displayName);
        userRepository.save(user);

        return "회원가입 완료";
    }

    @GetMapping("/login")
    public String login() {
        return "login.html";
    }


}
