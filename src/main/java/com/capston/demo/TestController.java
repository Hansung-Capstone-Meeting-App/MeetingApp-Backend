package com.capston.demo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@ResponseBody
public class TestController {
    @GetMapping("/")
    String hello(){
        return "auth 로그인 성공";
    }
}
