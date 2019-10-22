package com.example.es.esdemo.test.controller;

import com.example.es.esdemo.test.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @Autowired
    private TestService testService;

    @GetMapping("/bulkSave")
    public String bulkSave() {
        testService.bulkSave();
        return "success";
    }
}
