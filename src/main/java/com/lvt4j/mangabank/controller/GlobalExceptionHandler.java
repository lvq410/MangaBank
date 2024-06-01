package com.lvt4j.mangabank.controller;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
public class GlobalExceptionHandler{

    @ExceptionHandler(ResponseStatusException.class) @ResponseBody
    public final ResponseEntity<String> responseStatusException(HttpServletRequest request, ResponseStatusException e) throws Exception {
        return new ResponseEntity<String>(e.getReason(), e.getStatus());
    }
    
}