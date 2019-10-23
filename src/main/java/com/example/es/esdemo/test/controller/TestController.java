package com.example.es.esdemo.test.controller;

import com.alibaba.fastjson.JSON;
import com.example.es.esdemo.test.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
public class TestController {

    @Autowired
    private TestService testService;

    @GetMapping("/bulkSave")
    public String bulkSave() {
        testService.bulkSave();
        return "success";
    }


    @RequestMapping("/queryDSL")
    public String queryDSL(HttpServletRequest request) {
        String json = request.getParameter("json");
        Map param = JSON.parseObject(json, Map.class);
        testService.queryDSL(param);
        return "";
    }

}
