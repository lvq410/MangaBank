package com.lvt4j.mangabank.controller;

import static org.springframework.http.HttpStatus.NO_CONTENT;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lvt4j.mangabank.MangaBankAPP;
import com.lvt4j.mangabank.dao.UserDao;
import com.lvt4j.mangabank.po.User;

/**
 *
 * @author LV on 2023年11月22日
 */
@Validated
@RestController
@RequestMapping
public class UserController {

    @Autowired
    private UserDao dao;
    
    @PostMapping("login")
    public boolean dologin(HttpSession session,
            @NotBlank
            @RequestParam String id,
            @Size(min=5,max=18)
            @RequestParam String pwd) throws Exception {
        User user = dao.getByCache(id);
        if(user==null) return false;
        String pwdMd5 = MangaBankAPP.md5(pwd);
        if(!pwdMd5.equalsIgnoreCase(user.pwdMd5)) return false;
        
        session.setAttribute("UserId", id);
        
        return true;
    }
    
    @PostMapping("register")
    @ResponseStatus(NO_CONTENT)
    public void register(HttpSession session,
            @NotBlank
            @RequestParam String id,
            @Size(min=5,max=18)
            @RequestParam String pwd) throws IOException {
        dao.register(id, MangaBankAPP.md5(pwd));
        
        session.setAttribute("UserId", id);
    }
    
    @GetMapping("login")
    public ResponseEntity<User> isLogin(HttpSession session, HttpServletResponse res) throws Exception {
        String id = (String) session.getAttribute("UserId");
        if(id==null) return ResponseEntity.noContent().build();
        User user = dao.getByCache(id);
        if(user==null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(user);
    }
    
}