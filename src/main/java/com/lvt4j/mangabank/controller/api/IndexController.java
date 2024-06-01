package com.lvt4j.mangabank.controller.api;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller("index")
@RequestMapping("")
public class IndexController{

    @GetMapping({"","/","index"})
    public void index(HttpServletRequest request, HttpServletResponse response){
        response.setStatus(HttpStatus.TEMPORARY_REDIRECT.value());
        String location = null;
        if(request.getRequestURI().endsWith("/")) location = "index.html";
        else if(request.getRequestURI().endsWith("index")) location = "index.html";
        else location = "./index.html";
        response.setHeader("Location", location);
    }
    
}