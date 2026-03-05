package com.capston.demo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class TestController {
    @GetMapping("/")
    String hello(){
        return "main.html";
    }

    @GetMapping("/test")
    @ResponseBody
    String test(){
        return "JWT 통과 성공";
    }
}
