package com.lvt4j.mangabank.service;

import static com.lvt4j.mangabank.service.FileService.isCoverImgFile;
import static com.lvt4j.mangabank.service.FileService.isImgFile;
import static java.util.stream.Collectors.toCollection;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.lvt4j.mangabank.dao.BookDao;
import com.lvt4j.mangabank.dao.TagDao;
import com.lvt4j.mangabank.dto.NumberPath;
import com.lvt4j.mangabank.po.Book;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ManagedResource(objectName = "!Service:name=SyncService")
public class SyncService{
    
    public static final Set<String> IgnoreFolderNames = ImmutableSet.of("@eaDir");
    
    @Value("${book.dir}")
    private String bookDir;
    
    private File bookFolder;
    
    @Autowired
    private BookDao bookDao;
    
    @Autowired
    private TagDao tagDao;
    
    @Autowired
    private FileService fileService;
    
    @PostConstruct
    private void init(){
        bookFolder = new File(bookDir);
        bookFolder.mkdirs();
    }
    
    @ManagedOperation(description = "全量检查和同步")
    public void full() throws IOException {
        log.info("全量检查和同步开始，book目录：{}", bookFolder.getAbsolutePath());
        syncRootPaths(Collections.emptySet());
        log.info("全量检查和同步结束");
    }
    
    /**
     * 同步rootPaths下的book
     * @param rootPaths 为空则全量同步
     * @throws IOException
     */
    public void syncRootPaths(Set<String> rootPaths) throws IOException {
        File[] rootFiles = Stream.of(bookFolder.listFiles()).filter(f->rootPaths.isEmpty() || rootPaths.contains(f.getName())).toArray(File[]::new);
        for(int i = 0; i < rootFiles.length; i++){
            File rootFile = rootFiles[i];
            log.info("处理book目录下第{}/{}个：{}", i+1, rootFiles.length, rootFile.getName());
            
            try{
                sync(rootFile);
            }catch(Exception e){
                log.error("处理book目录下第{}/{}个：{}失败", i+1, rootFiles.length, rootFile.getName(), e);
            }
        }
        //清理rootPath前缀，但path对应文件已不存在的book
        List<Book> books = bookDao.search(Book.Query.builder().pathPrefixes(rootPaths).build(), null, 1, Integer.MAX_VALUE).getRight();
        for(Book book: books){
            File bookFile = new File(bookFolder, book.path);
            if(bookFile.exists()) {
                if(bookFile.isDirectory() && IgnoreFolderNames.contains(bookFile.getName())) {
                    log.info("book文件需要忽略，清理：{}", book.path);
                    bookDao.delete(book.path);
                    continue;
                }
                continue;
            }
            log.info("book文件不存在，清理：{}", book.path);
            bookDao.delete(book.path);
        }
    }
    
    public void sync(File file) throws IOException {
        if(FileService.isCompressFile(file)) {
            syncFileVol(file);
        }else if(file.isDirectory()){
            if(IgnoreFolderNames.contains(file.getName())) return;
            for(File subFile: file.listFiles()){
                sync(subFile);
            }
            syncFolder(file);
        }
    }
    private void syncFileVol(File file) throws IOException {
        Path path = bookFolder.toPath().relativize(file.toPath());
        String pathStr = FilenameUtils.separatorsToUnix(path.toString());
        log.info("同步压缩文件：{}", pathStr);
        
        Book book = bookDao.get(pathStr);
        if(book == null){
            log.debug("未导入过，新增");
            book = genBook(file, path, pathStr);
            book.collection = false;
            
            bookDao.set(book);
            tagDao.correctTagedCount(book.tags);
            return;
        }
        log.debug("已导入过，检查更新");
        boolean changed = syncFileChange(file, book);
        if(!changed) {
            log.debug("无变化，跳过");
            return;
        }
        log.debug("更新入库");
        bookDao.set(book);
    }
    private void syncFolder(File file) throws IOException {
        Path path = bookFolder.toPath().relativize(file.toPath());
        String pathStr = FilenameUtils.separatorsToUnix(path.toString());
        log.info("同步文件夹：{}", pathStr);
        
        Book book = bookDao.get(pathStr);
        if(book == null){
            log.debug("未导入过，新增");
            book = genBook(file, path, pathStr);
            book.collection = true;
            
            bookDao.set(book);
            tagDao.correctTagedCount(book.tags);
            return;
        }
        log.debug("已导入过，检查更新");
        boolean changed = syncFileChange(file, book);
        if(!changed) {
            log.debug("无变化，跳过");
            return;
        }
        log.debug("更新入库");
        bookDao.set(book);
    }
    private String parentPath(Path path){
        return Optional.ofNullable(path.getParent()).map(Path::toString).map(FilenameUtils::separatorsToUnix).orElse(null);
    }

    private Integer sequenceInParent(File file){
        File parent = file.getParentFile();
        
        if(parent.equals(bookFolder)) return null;
        
        return Stream.of(parent.listFiles()).filter(f->f.isDirectory()||FileService.isCompressFile(f))
            .map(File::getName).map(NumberPath::new).sorted().map(np->new File(parent, np.path)).collect(Collectors.toList())
            .indexOf(file);
    }

    private Book genBook(File file, Path path, String pathStr) throws IOException {
        Book book = new Book();

        book.path = pathStr;
        book.titles = new LinkedHashSet<>();
        String baseName = FilenameUtils.getBaseName(file.getName());
        book.titles.add(baseName);

        book.coverPath = coverPath(file);
        
        book.parentPath = parentPath(path);
        book.sequenceInParent = sequenceInParent(file);
        
        if(book.parentPath==null) book.tags = tagDao.getOrGens(tagsFromFileBaseName(baseName));

        book.createTime = new Date();
        book.updateTime = new Date(file.lastModified());
        
        return book;
    }
    private boolean syncFileChange(File file, Book book) throws IOException {
        boolean changed = false;
        if(!fileService.pathExist(book.coverPath)) {
            String coverPath = coverPath(file);
            if(!coverPath.equals(book.coverPath)) {
                log.debug("封面图片不存在，更新：{}->{}", book.coverPath, coverPath);
                book.coverPath = coverPath;
                changed = true;
            }
        }
        Integer sequenceInParent = sequenceInParent(file);
        if(!Objects.equals(sequenceInParent, book.sequenceInParent)){
            log.debug("在父文件夹中的排序发生变化，更新：{}->{}", book.sequenceInParent, sequenceInParent);
            book.sequenceInParent = sequenceInParent;
            changed = true;
        }
        if(!Objects.equals(book.updateTime.getTime(), file.lastModified())){
            log.debug("文件最近修改时间发生变化，更新：{}->{}",
                DateFormatUtils.format(book.updateTime, "yyyy-MM-dd HH:mm:ss.SSS"), DateFormatUtils.format(file.lastModified(), "yyyy-MM-dd HH:mm:ss.SSS"));
            book.updateTime = new Date(file.lastModified());
            changed = true;
        }
        return changed;
    }

    private String coverPath(File file) throws IOException {
        String coverPathRelativeToFile = file.isDirectory()?extractCoverImgPathFromFolder(file):extractCoverImgPathFromCompressFile(file);
        if(coverPathRelativeToFile == null) {
            if(!file.isDirectory()) return FileService.DefaultCoverPath;
            //文件夹类型时，尝试读取子book的封面
            String path = FilenameUtils.separatorsToUnix(bookFolder.toPath().relativize(file.toPath()).toString());
            String coverPath = bookDao.search(Book.Query.builder().parentPath(path).build(),
                Book.Query.SequenceInParentAsc, 1, 1).getRight().stream().map(b->b.coverPath).findFirst().orElse(null);
            if(coverPath!=null) return coverPath;
            return FileService.DefaultCoverPath;
        }
        return FilenameUtils.separatorsToUnix(bookFolder.toPath().relativize(file.toPath()).toString()) + '/' + coverPathRelativeToFile;
    }
    /**
     * 从压缩包文件中提取封面图片的相对路径(相对于file)
     * 优先提取{@link #isCoverImgFile}==true的图片
     * 如果没有，则提取第一个图片
     * 如果没有图片文件，返回null
     * @param file
     * @return
     */
    static String extractCoverImgPathFromCompressFile(File file) throws IOException{
        String baseName = FilenameUtils.getBaseName(file.getName());
        
        List<NumberPath> imgPaths = new LinkedList<>();
        
        @Cleanup FileInputStream fis = new FileInputStream(file);
        @Cleanup ZipInputStream zis = new ZipInputStream(fis);
        ZipEntry ze;
        while((ze = zis.getNextEntry()) != null){
            String name = ze.getName();
            if(!isImgFile(name)) continue;
            if(isCoverImgFile(name, baseName)) return name;
            imgPaths.add(new NumberPath(name));
        }
        
        Collections.sort(imgPaths);
        
        return imgPaths.isEmpty()?null:imgPaths.get(0).path;
    }
    
    /**
     * 从文件夹下面的图片文件中 提取封面图片的相对路径(相对于file)
     * 优先提取{@link #isCoverImgFile}==true的图片
     * 如果没有，则提取第一个图片
     * 如果没有仍没有，返回null
     * @param file
     * @return
     * @throws IOException
     */
    private String extractCoverImgPathFromFolder(File file) throws IOException{
        String baseName = FilenameUtils.getBaseName(file.getName());
        
        List<NumberPath> imgPaths = new LinkedList<>();
        for(File subFile: file.listFiles()){
            if(!isImgFile(subFile)) continue;
            if(isCoverImgFile(subFile.getName(), baseName)) return subFile.getName();
            imgPaths.add(new NumberPath(subFile.getName()));
        }
        
        Collections.sort(imgPaths);
        
        return imgPaths.isEmpty()?null:imgPaths.get(0).path;
    }
    
    
    private static final Map<Character, Character> BracketPairs = ImmutableMap.of(
        '[', ']', '(', ')', '（', '）', '【', '】', '{', '}');
    /**
     * 新添加的本子，从其文件名中提取标签
     * 被 [] () 【】（） {} 包裹的内容即为标签
     * @param baseName
     * @return
     */
    static LinkedHashSet<String> tagsFromFileBaseName(String baseName){
        Map<String, Integer> tagAndPositions = new HashMap<>();
        
        //左括号及其已收集的字符串
        Stack<MutableTriple<Character, StringBuilder, Integer>> brackets = new Stack<>();
        
        char[] chars = baseName.toCharArray();
        for(int i = 0; i < chars.length; i++){
            char c = chars[i];
            if(BracketPairs.containsKey(c)){ //是左括弧
                brackets.push(MutableTriple.of(c, new StringBuilder(), i));
            }else if(BracketPairs.containsValue(c)){ //是右括弧
                Character leftChar = BracketPairs.entrySet().stream().filter(e->e.getValue()==c).map(Map.Entry::getKey).findFirst().get();
                
                while(!brackets.isEmpty()){
                    Triple<Character, StringBuilder, Integer> bracket = brackets.pop();
                    String tag = bracket.getMiddle().toString().trim();
                    if(StringUtils.isNotBlank(tag)){
                        int position = bracket.getRight();
                        if(tagAndPositions.containsKey(tag)){
                            if(tagAndPositions.get(tag)>position) tagAndPositions.put(tag, position);
                        }else {
                            tagAndPositions.put(tag, position);
                        }
                    }
                    if(leftChar.equals(bracket.getLeft())) break; //是当前括弧对应的左括弧，不再继续弹栈
                }
            }else{ //是普通字符
                if(brackets.isEmpty()) continue;
                MutableTriple<Character, StringBuilder, Integer> curBracket = brackets.peek();
                curBracket.getMiddle().append(c);
                if(curBracket.getMiddle().length()==1) curBracket.setRight(i);
            }
        }
        
        while(!brackets.isEmpty()){
            Triple<Character, StringBuilder, Integer> bracket = brackets.pop();
            String tag = bracket.getMiddle().toString().trim();
            if(StringUtils.isNotBlank(tag)){
                int position = bracket.getRight();
                if(tagAndPositions.containsKey(tag)){
                    if(tagAndPositions.get(tag)>position) tagAndPositions.put(tag, position);
                }else {
                    tagAndPositions.put(tag, position);
                }
            }
        }
        
        return tagAndPositions.keySet().stream().sorted((t1,t2)->tagAndPositions.get(t1)-tagAndPositions.get(t2)).collect(toCollection(LinkedHashSet::new));
    }
    
}