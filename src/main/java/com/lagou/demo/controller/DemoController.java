package com.lagou.demo.controller;

import com.lagou.demo.service.IDemoService;
import com.lagou.edu.framework.anno.MyAutoweird;
import com.lagou.edu.framework.anno.MyController;
import com.lagou.edu.framework.anno.MyRequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Create By Waiting on 2020/2/8
 */
@MyController
@MyRequestMapping("/demo")
public class DemoController {

    @MyAutoweird
    private IDemoService demoService;

    @MyRequestMapping("/query")
    public String query(HttpServletRequest request, HttpServletResponse response, String name) {
        return demoService.get(name);
    }
}
