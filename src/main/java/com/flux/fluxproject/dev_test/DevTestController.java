package com.flux.fluxproject.dev_test;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DevTestController {

    @GetMapping("/test")
    public String showTestPage() {
        return "test-oauth"; // → test-oauth.html
    }
}