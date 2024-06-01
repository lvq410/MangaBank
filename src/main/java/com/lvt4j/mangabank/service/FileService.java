package com.lvt4j.mangabank.service;

import static java.util.stream.Collectors.toCollection;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.google.common.collect.ImmutableSet;
import com.lvt4j.mangabank.dao.BookDao;
import com.lvt4j.mangabank.dao.ImgCacheDao;
import com.lvt4j.mangabank.dto.NumberPath;
import com.lvt4j.mangabank.po.ImgCache;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FileService{

    public static final Set<String> CoverImgFileBaseNames = ImmutableSet.of("cover","folder");
    public static final String DefaultCoverPath = "default_cover.webp";
    
    @Value("${book.watch.enable:true}")
    private boolean watchEnable;
    
    @Value("${book.dir}")
    private String bookDir;
    private File bookFolder;
    
    @Value("${tmp.dir}")
    private String tmpDir;
    private File tmpFolder;
    
    private byte[] defaultCoverData;
    
    @Autowired@Lazy
    private SyncService syncService;
    
    @Autowired@Lazy
    private ImgCacheDao imgCacheDao;
    @Autowired@Lazy
    private BookDao bookDao;
    
    private volatile boolean destoryed = false;
    
    private Watcher fileWatcher;
    
    private ChangeWorker changeWorker;
    
    
    @PostConstruct
    private void init() throws IOException{
        bookFolder = new File(bookDir); bookFolder.mkdirs();
        tmpFolder = new File(tmpDir); tmpFolder.mkdirs();
        
        defaultCoverData = IOUtils.toByteArray(getClass().getResourceAsStream("/"+DefaultCoverPath));
        
        if(watchEnable) fileWatcher = new Watcher();
        if(watchEnable) changeWorker = new ChangeWorker();
    }
    @PreDestroy
    private void destory(){
        destoryed = true;
        if(fileWatcher!=null) fileWatcher.interrupt();
        if(changeWorker!=null) changeWorker.interrupt();
    }
    
    private class Watcher extends Thread {
        private WatchService watchService;
        private Map<WatchKey, Path> watchedKeys = new HashMap<>();
        
        private Watcher() throws IOException {
            watchService = FileSystems.getDefault().newWatchService();
            watchRegisterAll(bookFolder.toPath());
            
            setName("FileWatcher");
            setDaemon(true);
            start();
        }
        @Override
        public void run(){
            while(!destoryed){
                WatchKey key;
                try{
                    key = watchService.take();
                }catch(InterruptedException ex){
                    continue;
                }
                
                Path eventDir = watchedKeys.get(key);
                
                for(WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if(kind == StandardWatchEventKinds.OVERFLOW) continue;
                    
                    @SuppressWarnings("unchecked")
                    Path path = eventDir.resolve((((WatchEvent<Path>) event)).context());
                    // 如果是ENTRY_CREATE事件，并且是目录，则注册新目录
                    if(event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        if(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                            try{
                                watchRegisterAll(path);
                            }catch(IOException e){
                                log.error("注册监视目录[{}]失败", path, e);
                            }
                        }
                    }
                    
                    /*
                     * 文件新增时事件：
                     * 
                     * 
                     * 文件删除时事件：
                     * ENTRY_DELETE 被删文件
                     * ENTRY_MODIFY 被删文件所在文件夹
                     * 
                     * 文件移动时事件：文件名修改时相同，但仅有一个ENTRY_MODIFY事件
                     * ENTRY_DELETE 原文件路径
                     * ENTRY_CREATE 新文件路径
                     * ENTRY_MODIFY 原文件路径所在文件夹
                     * ENTRY_MODIFY 新文件路径所在文件夹
                     * 
                     */
                    
                    path = bookFolder.toPath().relativize(path);
                    String pathStr = normalize(path.toString());
                    log.info("book目录文件变更：{} {}", event.kind(), pathStr);
                    
                    changeWorker.addChange(pathStr);
                }
                
                boolean valid = key.reset();
                if(!valid) watchedKeys.remove(key);
            }
        }
        private void watchRegisterAll(Path start) throws IOException {
            Files.walk(start).filter(Files::isDirectory).forEach(dir -> {
                try {
                    WatchKey key = dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                    watchedKeys.put(key, dir);
                } catch (IOException e) {
                    log.error("注册监视目录[{}]失败", dir, e);
                }
            });
        }
    }
    
    private class ChangeWorker extends Thread {
        private final BlockingQueue<String> changedPaths = new LinkedBlockingQueue<>();
        
        public ChangeWorker(){
            setName("FileChangeWorker");
            setDaemon(true);
            start();
        }
        
        public void addChange(String path){
            if(changedPaths.contains(path)) return;
            changedPaths.add(path);
        }
        
        @Override
        public void run(){
            Set<String> deltaChangedPaths = new HashSet<>();
            while(!destoryed){
                try{
                    deltaChangedPaths.add(changedPaths.take()); sleep(1000);
                    changedPaths.drainTo(deltaChangedPaths);
                    doChange(deltaChangedPaths);
                }catch(InterruptedException e){
                    continue;
                }catch(Exception e){
                    log.error("文件变更处理失败：{}", StringUtils.join(deltaChangedPaths, "\n"), e);
                }finally {
                    deltaChangedPaths.clear();
                }
            }
            changedPaths.drainTo(deltaChangedPaths);
            if(!deltaChangedPaths.isEmpty()){
                try{
                    doChange(deltaChangedPaths);
                }catch(Exception e){
                    log.error("文件变更处理失败：{}", StringUtils.join(deltaChangedPaths, "\n"), e);
                }
            }
        }
        
        private void doChange(Set<String> deltaChangedPaths) throws IOException {
            log.debug("处理文件变更：\n{}", StringUtils.join(deltaChangedPaths, "\n"));
            
            //整理出所有的rootPath
            Set<String> rootPaths = deltaChangedPaths.stream().map(path->{
                if(path.contains("/"))
                    return path.substring(0, path.indexOf("/"));
                return path;
            }).collect(Collectors.toSet());
            log.debug("涉及book目录下根文件夹: \n{}", StringUtils.join(rootPaths, "\n"));
            
            //清理掉rootPath下的所有图片缓存
            log.debug("清理图片缓存");
            imgCacheDao.delete(ImgCache.Query.builder().pathPrefixes(rootPaths).build());
            
            //重新执行rootPaths的导入
            syncService.syncRootPaths(rootPaths);
        }
    }
    
    public List<String> extractImgPathsFromPath(String path) throws Exception{
        File file = new File(bookFolder, path);
        if(!file.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        List<String> pathRelativePaths = file.isDirectory()?extractImgPathsFromFolder(file):extractImgPathsFromCompressFile(file);
        return pathRelativePaths.stream().map(pathRelativePath->path+"/"+pathRelativePath).collect(Collectors.toList());
    }
    private List<String> extractImgPathsFromCompressFile(File file) throws Exception{
        String baseName = FilenameUtils.getBaseName(file.getName());
        List<NumberPath> imgPaths = new LinkedList<>();
        List<NumberPath> coverImgPaths = new LinkedList<>();
        
        @Cleanup FileInputStream fis = new FileInputStream(file);
        @Cleanup ZipInputStream zis = new ZipInputStream(fis);
        ZipEntry ze;
        while((ze = zis.getNextEntry()) != null){
            String name = ze.getName();
            if(!isImgFile(name)) continue;
            NumberPath np = new NumberPath(name);
            imgPaths.add(np);
            if(isCoverImgFile(name, baseName)) coverImgPaths.add(np);
        }
        
        Collections.sort(imgPaths);
        
        if(!coverImgPaths.isEmpty()){
            imgPaths.removeAll(coverImgPaths);
            imgPaths.addAll(0, coverImgPaths);
        }
        
        return imgPaths.stream().map(np->np.path).collect(Collectors.toList());
    }
    private List<String> extractImgPathsFromFolder(File file){
        List<NumberPath> imgPaths = new LinkedList<>();
        List<NumberPath> coverImgPaths = new LinkedList<>();
        for(File subFile: file.listFiles()){
            if(!isImgFile(subFile)) continue;
            NumberPath np = new NumberPath(subFile.getName());
            imgPaths.add(np);
            if(isCoverImgFile(subFile.getName(), file.getName())) coverImgPaths.add(np);
        }
        
        Collections.sort(imgPaths);
        
        if(!coverImgPaths.isEmpty()){
            imgPaths.removeAll(coverImgPaths);
            imgPaths.addAll(0, coverImgPaths);
        }
        
        return imgPaths.stream().map(np->np.path).collect(Collectors.toList());
    }
    
    static boolean isImgFile(File file){
        return isImgFile(file.getName());
    }
    static boolean isImgFile(String fileName){
        MediaType mediaType = MediaTypeFactory.getMediaType(fileName).orElse(null);
        if(mediaType==null) return false;
        return "image".equals(mediaType.getType());
    }
    /**
     * 判断一个文件是否是封面图片文件
     * 文件名形如 cover.jpg, folder.png，或与其所在文件夹名相同(仅当文件夹名不是纯数字时)
     * @param fileName
     * @param parentBaseName 父文件夹名，如果是压缩包内的图片文件，则为压缩包的文件base名
     * @return
     */
    static boolean isCoverImgFile(String fileName, String parentBaseName){
        if(!isImgFile(fileName)) return false;
        String baseName = FilenameUtils.getBaseName(fileName);
        if(CoverImgFileBaseNames.stream().anyMatch(sd->sd.equalsIgnoreCase(baseName))) return true;
        
        if(NumberUtils.isDigits(baseName)) return false; //文件夹名为纯数字时，不再寻找与同名文件的封面图片
        return parentBaseName.equalsIgnoreCase(baseName);
    }

    static boolean isCompressFile(File file){
        return isCompressFile(file.getName());
    }

    static boolean isCompressFile(String fileName){
        String ext = FilenameUtils.getExtension(fileName);
        return "zip".equalsIgnoreCase(ext);
    }
    
    public boolean pathExist(String path) throws IOException {
        if(StringUtils.isBlank(path)) return false;
        path = normalize(path);
        if(DefaultCoverPath.equals(path)) return false;
        
        File file = new File(bookDir);
        while(path.contains("/")){
            String node = path.substring(0, path.indexOf("/"));
            path = path.substring(path.indexOf("/")+1);
            file = new File(file, node);
            if(!file.exists()) return false;
            
            String ext = FilenameUtils.getExtension(node);
            if("zip".equalsIgnoreCase(ext)){
                @Cleanup ZipFile zipFile = new ZipFile(file);
                ZipEntry zipEntry = zipFile.getEntry(path);
                if(zipEntry==null) return false;
                return true;
            }
        }

        file = new File(file, path);
        return file.exists();
    }
    
    public void readPath(String path, BiConsumer<Integer, InputStream> reader) throws IOException{
        path = normalize(path);
        
        if(DefaultCoverPath.equals(path)){
            reader.accept(defaultCoverData.length, new ByteArrayInputStream(defaultCoverData));
            return;
        }
        
        File file = new File(bookDir);
        while(path.contains("/")){
            String node = path.substring(0, path.indexOf("/"));
            path = path.substring(path.indexOf("/")+1);
            file = new File(file, node);
            if(!file.exists())throw new ResponseStatusException(HttpStatus.NOT_FOUND, "path not exist");
            
            String ext = FilenameUtils.getExtension(node);
            if("zip".equalsIgnoreCase(ext)){
                @Cleanup ZipFile zipFile = new ZipFile(file);
                ZipEntry zipEntry = zipFile.getEntry(path);
                if(zipEntry==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "path not exist");
                
                @Cleanup InputStream is = zipFile.getInputStream(zipEntry);
                reader.accept((int)zipEntry.getSize(), is);
                return;
            }
        }

        file = new File(file, path);
        if(!file.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "path not exist");
        @Cleanup FileInputStream fis = new FileInputStream(file);
        reader.accept((int)file.length(), fis);
    }
    
    public static String normalize(String path){
        path = FilenameUtils.normalizeNoEndSeparator(path, true);
        path = StringUtils.stripStart(path, "/");
        path = StringUtils.stripEnd(path, "/");
        return path;
    }
    
    public synchronized void setImgs(String path, LinkedHashSet<String> imgPaths, Set<String> delImgPaths) throws IOException {
        path = normalize(path);
        
        if(isCompressFile(path)) {
            setCompressFileImgs(path, imgPaths, delImgPaths);
        }else{
            setFolderImgs(path, imgPaths, delImgPaths);
        }
    }
    private void setCompressFileImgs(String path, LinkedHashSet<String> imgPaths, Set<String> delImgPaths) throws IOException {
        File file = new File(bookFolder, path);
        if(!file.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "压缩文件不存在："+path);
        
        LinkedHashSet<String> imgRelativePaths = imgPaths.stream().map(imgPath->imgPath.substring(path.length()+1)).collect(toCollection(LinkedHashSet::new));
        Set<String> delImgRelativePaths = delImgPaths.stream().map(imgPath->imgPath.substring(path.length()+1)).collect(toCollection(HashSet::new));
        
        //整理出要保留的非图片文件
        List<ZipEntry> remainOtherEntries = new LinkedList<>();
        @Cleanup FileInputStream fis = new FileInputStream(file);
        @Cleanup ZipInputStream zis = new ZipInputStream(fis);
        ZipEntry ze;
        while((ze = zis.getNextEntry()) != null){
            String name = ze.getName();
            if(delImgRelativePaths.contains(name)) continue;
            if(!imgRelativePaths.contains(name)) remainOtherEntries.add(ze);
        }
        zis.close(); fis.close();
        //要保留的非图片文件中，可能有文件夹类型，需要检查下这种文件夹下是否还有文件存在，若无则删除
        List<ZipEntry> remainOtherEmptyFolderEntries = new LinkedList<>();
        for(ZipEntry remainOtherEntry: remainOtherEntries){
            String name = remainOtherEntry.getName();
            if(!name.endsWith("/")) continue;
            boolean isEmpty = remainOtherEntries.stream().filter(e->!e.getName().endsWith("/")).noneMatch(e->e.getName().startsWith(name));
            if(isEmpty) remainOtherEmptyFolderEntries.add(remainOtherEntry);
        }
        remainOtherEntries.removeAll(remainOtherEmptyFolderEntries);
        
        @Cleanup ZipFile zipFile = new ZipFile(file);
        
        File tmpCompressFile = new File(tmpFolder, FilenameUtils.getName(path));
        if(tmpCompressFile.exists()) FileUtils.forceDelete(tmpCompressFile);
        
        @Cleanup FileOutputStream fos = new FileOutputStream(tmpCompressFile);
        @Cleanup ZipOutputStream zos = new ZipOutputStream(fos);
        
        //写入图片文件
        int no = 1; int numberLength = numberLength(imgRelativePaths.size());
        for(String imgRelativePath: imgRelativePaths){
            ze = zipFile.getEntry(imgRelativePath);
            if(ze==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在 : "+path+"/"+imgRelativePath);
            
            String ext = FilenameUtils.getExtension(imgRelativePath);
            String newName = String.format("%0"+numberLength+"d.%s", no++, ext);
            @Cleanup InputStream is = zipFile.getInputStream(ze);
            zos.putNextEntry(new ZipEntry(newName));
            IOUtils.copy(is, zos);
        }
        
        //写入其他要保留的非图片文件
        for(ZipEntry remainOtherEntry: remainOtherEntries){
            @Cleanup InputStream is = zipFile.getInputStream(remainOtherEntry);
            zos.putNextEntry(new ZipEntry(remainOtherEntry.getName()));
            IOUtils.copy(is, zos);
        }
        
        zos.close(); fos.close();
        
        zipFile.close();
        
        if(!file.delete()) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "删除原文件失败："+path);
        if(!tmpCompressFile.renameTo(file)) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "移动文件从"+tmpCompressFile.getAbsolutePath()+"到"+file.getAbsolutePath()+"失败");
    }
    private void setFolderImgs(String path, LinkedHashSet<String> imgPaths, Set<String> delImgPaths) throws IOException {
        File file = new File(bookFolder, path);
        if(!file.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在："+path);
        
        File tmpFile = new File(tmpFolder, FilenameUtils.getName(path));
        if(tmpFile.exists()) FileUtils.forceDelete(tmpFile);
        tmpFile.mkdirs();
        
        //要保留的图片路径中，移除封面图片
        imgPaths = imgPaths.stream().filter(imgPath->!isCoverImgFile(FilenameUtils.getName(imgPath), file.getName())).collect(toCollection(LinkedHashSet::new));
        
        //检查要保留的图片文件是否都存在
        for(String imgPath: imgPaths){
            File imgFile = new File(bookFolder, imgPath);
            if(!imgFile.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在 : "+imgPath);
        }
        
        //移动并重命名保留的图片文件到临时文件夹
        int no=1; int numberLength = numberLength(imgPaths.size());
        for(String imgPath: imgPaths){
            File imgFile = new File(bookFolder, imgPath);
            String ext = FilenameUtils.getExtension(imgPath);
            String newName = String.format("%0" + numberLength + "d.%s", no++, ext);
            File newImgFile = new File(tmpFile, newName);
            if(!imgFile.renameTo(newImgFile)) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "移动文件从"+imgFile.getAbsolutePath()+"到"+newImgFile.getAbsolutePath()+"失败");
        }
        
        //删除要删除的图片文件
        for(String delImgPath: delImgPaths){
            File delImgFile = new File(bookFolder, delImgPath);
            if(!delImgFile.exists()) continue;
            if(!delImgFile.delete()) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "删除文件失败：" + delImgPath);
        }
        
        //保留的图片从临时文件夹 移动回原文件夹
        for(File tmpImgFile: tmpFile.listFiles()){
            File imgFile = new File(file, tmpImgFile.getName());
            if(!tmpImgFile.renameTo(imgFile)) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "移动文件从" + tmpImgFile.getAbsolutePath() + "到" + imgFile.getAbsolutePath() + "失败");
        }
        
        //删除临时文件夹
        FileUtils.forceDelete(tmpFile);
    }
    public static int numberLength(int n){
        int length = 0;
        do{
            n = n/10;
            length +=1;
        }while(n>0);
        return length;
    }
    
}
