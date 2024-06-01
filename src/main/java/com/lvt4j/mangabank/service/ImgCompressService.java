package com.lvt4j.mangabank.service;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableSet;
import com.lvt4j.mangabank.Resolution;
import com.lvt4j.mangabank.dao.ImgCacheDao;
import com.lvt4j.mangabank.po.ImgCache;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * webp图片压缩
 * https://developers.google.com/speed/webp/docs/cwebp?hl=zh-cn
 * @author chanceylee on 2024年5月29日
 */
@Slf4j
@Service
@ManagedResource(objectName="!Service:type=ImgCompressService")
public class ImgCompressService extends Thread{

    private static final String OrigFileName = "orig";
    private static final String DestFileFullName = "compressed.webp";
    
    private static final Set<String> SupportExts = ImmutableSet.of("jpg", "jpeg", "png", "gif", "bmp", "tiff");
    
    @Value("${cwebp.path:cwebp}")
    private String cwebpPath;
    
    @Value("${tmp.dir}")
    private String tmpDir;
    
    private File tmpFolder;
    
    private File origFile;
    private File destFile;
    
    @Autowired
    private ImgCacheDao imgCacheDao;
    
    @Autowired@Lazy
    private FileService fileService;
    
    private volatile boolean destoryed = false;
    
    private BlockingQueue<CompressTask> tasks = new ArrayBlockingQueue<>(1000);
    
    @PostConstruct
    private void init(){
        tmpFolder = new File(tmpDir);
        tmpFolder.mkdirs();
        setDaemon(true);
        setName("ImgCompressService");
        start();
    }
    
    @PreDestroy
    private void destory(){
        destoryed = true;
        interrupt();
    }
    
    @Override
    public void run(){
        CompressTask task=null;
        while(!destoryed){
            try{
                task = tasks.take();
                if(imgCacheDao.exist(task.path, task.resolution)) continue;
                
                doCompress(task.path, task.resolution);
            }catch(InterruptedException e){
                if(!destoryed) log.error("压缩图片异常", e);
            }catch (Exception e) {
                log.error("压缩图片{} {}异常", task.resolution, task.path, e);
            }
        }
    }
    
    /**
     * 提交压缩任务
     * @param path
     * @param resolution
     */
    public void submit(String path, Resolution resolution){
        if(FileService.DefaultCoverPath.equals(path)) return;
        try{
            CompressTask task = new CompressTask(path, resolution);
            if(tasks.stream().anyMatch(task::equals)) return;
            tasks.offer(new CompressTask(path, resolution));
        }catch(Exception e){
            log.error("提交压缩任务异常", e);
        }
    }
    
    @ManagedOperation(description="执行压缩图片")
    public synchronized void doCompress(String path, Resolution resolution) throws Exception{
        ImgCache imgCache = new ImgCache();
        imgCache.path = path;
        imgCache.resolution = resolution;
        imgCache.createTime = new Date();
        
        String ext = FilenameUtils.getExtension(path);
        if(!SupportExts.contains(ext)) {
            imgCache.misReason = "不支持的文件格式";
            imgCacheDao.set(imgCache);
            return;
        }
        
        origFile = new File(tmpFolder, OrigFileName+"."+ext);
        destFile = new File(tmpFolder, DestFileFullName);
        
        if(origFile.exists()) FileUtils.forceDelete(origFile);
        if(destFile.exists()) FileUtils.forceDelete(destFile);
        
        fileService.readPath(path, (size, is)->{
            try{
                FileUtils.copyInputStreamToFile(is, origFile);
            }catch(IOException e){
                throw new RuntimeException("复制原文件异常", e);
            }
        });
        
        String cmd = cwebpPath+" -m 6 -noalpha";
        if(resolution.width>0) cmd += " -resize "+resolution.width+" 0";
        cmd += " "+origFile.getName()+" -o "+DestFileFullName;
        
        Process process = Runtime.getRuntime().exec(cmd, null, tmpFolder);
        int rst = process.waitFor();
        
        if(rst==0 && destFile.exists() && destFile.length()>0){
            log.info("压缩图片{} {}成功", resolution, path);
            imgCache.data = FileUtils.readFileToByteArray(destFile);
        }else {
            log.error("压缩图片{} {}失败", resolution, path);
            imgCache.misReason = "压缩失败";
        }
        
        imgCacheDao.set(imgCache);
        
        FileUtils.forceDelete(origFile);
        FileUtils.forceDelete(destFile);
    }
    
    @Data
    class CompressTask {
        private final String path;
        private final Resolution resolution;
    }
    
}
