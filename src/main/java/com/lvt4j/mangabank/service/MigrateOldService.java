package com.lvt4j.mangabank.service;

import static com.lvt4j.mangabank.MangaBankAPP.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Service;
import org.sqlite.SQLiteDataSource;

import com.google.common.collect.ImmutableMap;
import com.lvt4j.basic.TDB;
import com.lvt4j.basic.TDB.TDBTypeHandler;
import com.lvt4j.basic.TStream;
import com.lvt4j.mangabank.dao.BookDao;
import com.lvt4j.mangabank.dao.TagDao;
import com.lvt4j.mangabank.po.Book;

import lombok.AllArgsConstructor;
import lombok.Cleanup;
import lombok.Data;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@ManagedResource(objectName="!Service:type=MigrateOldService")
public class MigrateOldService{

    @Value("${book.dir}")
    private String bookDir;
    
    @Autowired
    private BookDao bookDao;
    @Autowired
    private TagDao tagDao;
    
    private File dbFolder = new File("F:\\db");
    
    private TDB bookDB;
    private Map<String, TDB> imgDBs;
    
    @PostConstruct
    private void init(){
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:"+new File(dbFolder, "book"));
        bookDB = new TDB(dataSource);
        TDB.registerTypeHandler(new TDBTypeHandler<Strs>(){
            @Override
            public Class<Strs> supportType(){
                return Strs.class;
            }
            @Override
            public int jdbcType(){
                return Types.VARCHAR;
            }
            @Override
            public String jdbcTypeName(){
                return "VARCHAR";
            }
            @Override@SneakyThrows
            public void setParameter(PreparedStatement prep, int parameterIndex, Strs value) throws SQLException{
                prep.setString(parameterIndex, ObjectMapper.writeValueAsString(value));
            }
            @Override@SneakyThrows
            public Strs getResult(ResultSet rs, int columnIndex) throws SQLException{
                String val = rs.getString(columnIndex);
                if(val==null) return null;
                return ObjectMapper.readValue(val, Strs.class);
            }
        });
        imgDBs = new HashMap<String, TDB>();
        File imgDBFolder = new File(dbFolder, "img");
        for(File imgDBFile: imgDBFolder.listFiles()){
            String dbKey = FilenameUtils.getBaseName(imgDBFile.getName());
            SQLiteDataSource imgDataSource = new SQLiteDataSource();
            imgDataSource.setUrl("jdbc:sqlite:" + imgDBFile);
            TDB imgDB = new TDB(imgDataSource);
            imgDBs.put(dbKey, imgDB);
        }
    }
    
    @ManagedOperation
    public void migrate() throws Exception {
        List<BookOld> books = bookDB.select("select * from book").execute2Model(BookOld.class);
        for(int i=0; i<books.size(); i++){
            BookOld bookOld = books.get(i);
            log.info("migrate {} /{} : {}", i, books.size(), bookOld.title.get(0));
            File bookFile = new File(bookDir, bookOld.title.get(0)+".zip");
            if(bookDao.get(bookFile.getName())!=null) continue;
            
            if(bookFile.exists()) bookFile.delete();
            @Cleanup FileOutputStream fos = new FileOutputStream(bookFile);
            @Cleanup ZipOutputStream zos = new ZipOutputStream(fos);
            
            String coverPath = null;
            Map<String, String> imgMD5ToPaths = new HashMap<String, String>();
            int no = 1; int numberLength = FileService.numberLength(bookOld.img.size());
            for(String imgKey: bookOld.img){
                Pair<ImgType, byte[]> img = loadImg(imgKey);
                if(img==null) continue;
                
                String ext = img.getLeft().name();
                String imgFileName = String.format("%0"+numberLength+"d.%s", no++, ext);
                
                zos.putNextEntry(new ZipEntry(imgFileName));
                IOUtils.copy(new ByteArrayInputStream(img.getRight()), zos);
                
                imgMD5ToPaths.put(imgKey, imgFileName);
                if(no==2) coverPath = imgFileName;
            }
            
            if(StringUtils.isNotBlank(bookOld.cover)) {
                if(!bookOld.img.contains(bookOld.cover)){
                    Pair<ImgType, byte[]> img = loadImg(bookOld.cover);
                    if(img!=null){
                        String ext = img.getLeft().name();
                        String imgFileName = String.format("cover.%s", ext);
    
                        zos.putNextEntry(new ZipEntry(imgFileName));
                        IOUtils.copy(new ByteArrayInputStream(img.getRight()), zos);
                        
                        imgMD5ToPaths.put(bookOld.cover, imgFileName);
                        
                        coverPath = imgFileName;
                    }
                }
            }
            
            zos.close(); fos.close();
            
            Book book = new Book();

            book.path = bookFile.getName();
            book.titles = bookOld.title;

            book.coverPath = coverPath==null?FileService.DefaultCoverPath:(book.path +"/"+ coverPath);
            
            book.tags = tagDao.getOrGens(bookOld.tag);

            book.createTime = new Date();
            book.updateTime = new Date(bookFile.lastModified());
            
            bookDao.set(book);
            tagDao.correctTagedCount(book.tags);
        }
    }
    
    private Pair<ImgType, byte[]> loadImg(String md5){
        String dbKey = md5.substring(0,2);
        String imgMD5 = md5.substring(2,32);
        
        TDB imgDB = imgDBs.get(dbKey);
        if(imgDB==null) return null;
        
        byte[] cnt = imgDB.select("select cnt from img where md5=?", imgMD5).execute2BasicOne(byte[].class);
        if(cnt==null) return null;
        
        ImgType type = ImgType.valueOf(cnt[0]);
        if(type==null) return null;
        
        byte[] compressed = new byte[cnt.length-1];
        System.arraycopy(cnt, 1, compressed, 0, compressed.length);
        byte[] original = TStream.unCompress(compressed);
        
        return Pair.of(type, original);
    }
    
    
    @Data
    public static class BookOld implements Serializable{

        private static final long serialVersionUID = 1L;
        
        public int id;
        public Strs title = new Strs();
        public Strs img = new Strs();
        public String cover;
        public Strs tag = new Strs();
        
        public boolean favor;
    }
    
    public static class Strs extends LinkedHashSet<String>{

        private static final long serialVersionUID = 1L;
        
        public Strs(){
            super();
        }

        public Strs(Collection<? extends String> c){
            super(c);
        }

        public Strs(int initialCapacity, float loadFactor){
            super(initialCapacity, loadFactor);
        }

        public Strs(int initialCapacity){
            super(initialCapacity);
        }

        @Override
        public boolean add(String e) {
            if (e==null)return false;
            if ("null".equals(e))return false;
            return super.add(e);
        }
        
        public String get(int idx) {
            if (idx>=size()) {
                return null;
            }
            Iterator<String> iterator = iterator();
            int i = 0;
            while (i!=idx) {
                iterator.next();
            }
            return iterator.next();
        }
    }
    
    @Data
    public static class TagOld implements Serializable{

        private static final long serialVersionUID = 1L;
        
        public int id;
        public String tag;
        public int pId;
    }

    @Getter
    @AllArgsConstructor
    public enum ImgType {

        png(0,"image/png")
        ,jpg(1,"image/jpeg")
        ,jpeg(2,"image/jpeg")
        ,gif(3,"image/gif")
        ,bmp(4,"image/bmp")
        ;
        
        public final int value;
        public final String contentType;
        
        private static final Map<Integer, ImgType> Types;
        
        static{
            ImmutableMap.Builder<Integer, ImgType> builder =ImmutableMap.builder();
            for(ImgType type : ImgType.values()){
                builder.put(type.value, type);
            }
            Types = builder.build();
        }
        
        public static ImgType valueOf(int b) {
            return Types.get(b);
        }
        
    }
    
}