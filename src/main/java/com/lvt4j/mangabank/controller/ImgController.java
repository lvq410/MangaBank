package com.lvt4j.mangabank.controller;

import static org.springframework.http.HttpHeaders.CACHE_CONTROL;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.lvt4j.mangabank.Resolution;
import com.lvt4j.mangabank.dao.ImgCacheDao;
import com.lvt4j.mangabank.po.ImgCache;
import com.lvt4j.mangabank.service.FileService;
import com.lvt4j.mangabank.service.ImgCompressService;

/**
 *
 * @author LV on 2023年9月10日
 */
@Controller
@RequestMapping({"img","admin/img"})
class ImgController {

    @Autowired
    private ImgCacheDao imgCacheDao;
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private ImgCompressService compressService;
    
    @GetMapping
    public void dbImg(HttpServletResponse res,
            @RequestParam String path,
            @RequestParam(defaultValue="OrigRaw") Resolution resolution) throws Exception {
        if(Resolution.OrigRaw!=resolution) {
            ImgCache imgCache = imgCacheDao.get(path, resolution);
            if(imgCache != null && imgCache.data!=null){
                res.setHeader(CACHE_CONTROL, "max-age=31536000");
                res.setContentType("image/webp");
                res.setContentLength(imgCache.data.length);
                res.getOutputStream().write(imgCache.data);
                return;
            }
            compressService.submit(path, resolution);
        }
        
        fileService.readPath(path, (size,is)->{
            res.setHeader(CACHE_CONTROL, "max-age=31536000");
            MediaType mediaType = MediaTypeFactory.getMediaType(path).orElse(null);
            if(mediaType!=null) res.setContentType(mediaType.toString());
            res.setContentLength(size);
            try{
                IOUtils.copy(is, res.getOutputStream());
            }catch(Exception e){
                throw new RuntimeException(e);
            }
        });
    }
    
}