package com.lagou.demo.service;

import com.lagou.edu.framework.anno.MyService;

/**
 * Create By Waiting on 2020/2/8
 */
@MyService
public class DemoServiceImpl implements IDemoService {
    @Override
    public String get(String name) {
        System.out.println("service 实现类中的name参数：" + name) ;
        return name;
    }
}
