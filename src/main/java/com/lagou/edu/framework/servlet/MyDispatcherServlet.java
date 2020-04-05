package com.lagou.edu.framework.servlet;

import com.lagou.demo.pojo.Handler;
import com.lagou.edu.framework.anno.MyAutoweird;
import com.lagou.edu.framework.anno.MyController;
import com.lagou.edu.framework.anno.MyRequestMapping;
import com.lagou.edu.framework.anno.MyService;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Create By Waiting on 2020/2/6
 */
public class MyDispatcherServlet extends HttpServlet {

    Properties properties = new Properties();

    List<String> classNames = new ArrayList<>();

    Map<String, Object> ioc = new HashMap<>();

    List<Handler> handerMapping = new ArrayList<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        // 加載配置文件
        String contextConfigLocation = config.getInitParameter("contextConfigLocation");
        doLoadConfig(contextConfigLocation);

        // 掃描包，取得包底下所有類的全限定類名
        doScan(properties.getProperty("scanPackage"));

        // 掃描註解，初始化Bean，放到IOC容器中
        doInstance();


        // 依賴注入
        doAutoweird();

        // 填充HandlerMapping
        initHandlerMapping();


        System.out.println(handerMapping);
    }

    private void initHandlerMapping() {

        if (ioc.isEmpty()) { return;}

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {

            Class<?> aClass = entry.getValue().getClass();

            String url = "";

            if (aClass.isAnnotationPresent(MyRequestMapping.class)) {
                url = aClass.getAnnotation(MyRequestMapping.class).value();
            }

            Method[] methods = aClass.getMethods();
            for (Method method : methods) {
                if (method.isAnnotationPresent(MyRequestMapping.class)) {
                    url = url + method.getAnnotation(MyRequestMapping.class).value();

                    Handler handler = new Handler(entry.getValue(), method, Pattern.compile(url));

                    Parameter[] parameters = method.getParameters();

                    for (int i = 0; i < parameters.length; i++) {

                        Parameter parameter = parameters[i];

                        if (parameter.getType() == HttpServletRequest.class || parameter.getType() == HttpServletResponse.class) {
                            handler.getParamIndexMapping().put(parameter.getType().getSimpleName(), i);
                        } else {
                            handler.getParamIndexMapping().put(parameter.getName(), i);
                        }

                    }

                    handerMapping.add(handler);

                }
            }

        }

    }

    private void doAutoweird() {
        if(ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();

            for (Field field : fields) {
                if (field.isAnnotationPresent(MyAutoweird.class)) {
                    MyAutoweird anno = field.getAnnotation(MyAutoweird.class);
                    String beanName = anno.value();
                    if ("".equals(beanName.trim())) {
                        beanName = field.getType().getName();
                        System.out.println(beanName);
                    }

                    field.setAccessible(true);

                    try {
                        field.set(entry.getValue(), ioc.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void doInstance() {
        if (classNames.size() == 0) return;

        try {
            for (String className : classNames) {

                Class<?> aClass = Class.forName(className);

                if(aClass.isAnnotationPresent(MyController.class)) {
                    // 取得ID
                    String simpleName = aClass.getSimpleName();
                    String lowerFirstSimpleName = lowerFirst(simpleName);
                    // 放入IOC容器
                    Object o = aClass.newInstance();
                    ioc.put(lowerFirstSimpleName, o);
                } else if(aClass.isAnnotationPresent(MyService.class)){
                    MyService anno = aClass.getAnnotation(MyService.class);
                    String beanName = anno.value().trim();
                    Object o = aClass.newInstance();
                    if("".equals(beanName)) { // 未指定名稱
                        String simpleName = aClass.getSimpleName();
                        String lowerFirstSimpleName = lowerFirst(simpleName);
                        ioc.put(lowerFirstSimpleName, o);
                    } else {
                        ioc.put(beanName, o);
                    }

                    Class<?>[] interfaces = aClass.getInterfaces();
                    for (Class<?> aInterface : interfaces) {
                        ioc.put(aInterface.getName(), o);
                    }
                } else {
                    continue;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void doScan(String scanPackage) {

        //scanPackage: com.lagou.demo  package---->  磁盘上的文件夹（File）  com/lagou/demo
        String scanPackagePath = this.getClass().getClassLoader().getResource("").getPath() + scanPackage.replaceAll("\\.", "/");

        File pack = new File(scanPackagePath);

        File[] files = pack.listFiles();

        for (File file : files) {
            if(file.isDirectory()) { //子Package

                doScan(scanPackage + "." + file.getName()); // com.lagou.demo.controller

            } else if (file.getName().endsWith(".class")){
                String className = scanPackage + "." + file.getName().replaceAll(".class", "");
                classNames.add(className);
            }
        }
    }

    // 首字母小写方法
    public String lowerFirst(String str) {
        char[] chars = str.toCharArray();
        if('A' <= chars[0] && chars[0] <= 'Z') {
            chars[0] += 32;
        }
        return String.valueOf(chars);
    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        Handler handler = getHandler(req);

        if (handler == null) {
            resp.getWriter().write("404 ....");
        }

        Class<?>[] methodParaTypes = handler.getMethod().getParameterTypes();

        Object[] invokeParamValues = new Object[methodParaTypes.length];

        Map<String, String[]> reqParameterMap = req.getParameterMap();

        for (Map.Entry<String, String[]> reqParam : reqParameterMap.entrySet()) {

            String reqParamValue = StringUtils.join(reqParam.getValue(), ",");

            if (!handler.getParamIndexMapping().containsKey(reqParam.getKey())) {continue;}

            Integer index = handler.getParamIndexMapping().get(reqParam.getKey());

            invokeParamValues[index] = reqParamValue;
        }

        Integer requestIndex = handler.getParamIndexMapping().get(HttpServletRequest.class.getSimpleName());
        invokeParamValues[requestIndex] = req;

        Integer responseIndex = handler.getParamIndexMapping().get(HttpServletResponse.class.getSimpleName());
        invokeParamValues[responseIndex] = resp;

        try {
            handler.getMethod().invoke(handler.getController(), invokeParamValues);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }

    private Handler getHandler(HttpServletRequest req) {
        if (handerMapping.isEmpty()) {return null;}

        String url = req.getRequestURI();

        for (Handler handler : handerMapping) {
            if (handler.getPattern().matcher(url).matches()) {
                return handler;
            }
        }

        return null;
    }
}
