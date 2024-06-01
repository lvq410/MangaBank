package com.lvt4j.mangabank.controller.admin;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.lvt4j.mangabank.SpringMVCConfig;
import com.lvt4j.mangabank.dao.UserDao;
import com.lvt4j.mangabank.po.User;

/**
 *
 * @author LV on 2023年11月22日
 */
@Component
public class AdminFilter extends FilterRegistrationBean<AdminFilter> implements Filter {

    @Autowired
    private SpringMVCConfig mvcConfig;
    
    @Autowired
    private UserDao userDao;
    
    public AdminFilter() {
        addUrlPatterns("/admin/*");
    }
    
    @Override public AdminFilter getFilter() { return this; }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        HttpSession session = httpRequest.getSession();
        String userId = (String) session.getAttribute("UserId");
        User user = userDao.get(userId);
        if(user!=null && user.admin) {
            chain.doFilter(httpRequest, httpResponse);
            return;
        }
        
        if(user==null){
            if(httpRequest.getRequestURI().endsWith(".html")) {
                String redirect = httpRequest.getRequestURI();
                if(StringUtils.isNotBlank(httpRequest.getContextPath())) redirect = redirect.substring(httpRequest.getContextPath().length());
                redirect = StringUtils.stripStart(redirect, "/");
                
                if(StringUtils.isNotBlank(httpRequest.getQueryString())) redirect += "?"+httpRequest.getQueryString();
                
                httpResponse.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
                httpResponse.setHeader("Location", mvcConfig.getCtxPath()+"/login.html?redirect="+URLEncoder.encode(redirect, "utf8"));
                return;
            }
            
            httpResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
            PrintWriter writer = httpResponse.getWriter();
            writer.println("Not login");
            writer.flush();
            return;
        }
        
        httpResponse.setStatus(HttpStatus.FORBIDDEN.value());
        PrintWriter writer = httpResponse.getWriter();
        writer.println("Forbbidden");
        writer.flush();
    }

}