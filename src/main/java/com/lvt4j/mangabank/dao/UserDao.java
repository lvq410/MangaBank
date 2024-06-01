package com.lvt4j.mangabank.dao;

import static org.springframework.http.HttpStatus.CONFLICT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.lvt4j.mangabank.po.User;

import lombok.Getter;

/**
 *
 * @author LV on 2023年11月21日
 */
@Component
@ManagedResource(objectName="!Dao:type=UserDao")
public class UserDao extends AbstractLuceneDao {

    @Getter
    @Value("${lucene.user.dir}")
    private String luceneFolder;

    @Getter
    @Value("${lucene.user.merge.min:128}")
    private double luceneMergeMin;
    @Getter
    @Value("${lucene.user.merge.max:2048}")
    private double luceneMergeMax;
    
    @Autowired@Lazy
    private BookDao bookDao;
    
    private LoadingCache<String, Optional<User>> cache = CacheBuilder.newBuilder()
        .build(new CacheLoader<String, Optional<User>>(){
            @Override
            public Optional<User> load(String id) throws Exception{
                return Optional.ofNullable(get(id));
            }
    });
    
    public User get(String id) throws IOException {
        if(StringUtils.isBlank(id)) return null;
        readerLock.readLock().lock();
        try{
            TopDocs topDocs = searcher.search(User.Query.builder().id(id).build().toLuceneQuery(), 1);
            if(topDocs.totalHits.value==0) return null;
            return User.fromDoc(searcher.doc(topDocs.scoreDocs[0].doc));
        }finally{
            readerLock.readLock().unlock();
        }
    }

    public User getByCache(String id) throws Exception {
        if(StringUtils.isBlank(id)) return null;
        return cache.get(id).orElse(null);
    }
    public User register(String id, String pwdMd5) throws IOException {
        readerLock.writeLock().lock();
        try{
            registerLimitCheck();
            
            TopDocs topDocs = searcher.search(User.Query.builder().id(id).build().toLuceneQuery(), 1);
            if(topDocs.totalHits.value>0) throw new ResponseStatusException(CONFLICT, "用户["+id+"]已注册");
            
            User user = new User();
            user.id = id;
            user.pwdMd5 = pwdMd5;
            
            user.createTime = new Date();
            
            writer.addDocument(user.toDoc());
            writer.commit();
            lucene_refresh();
            
            cache.invalidate(id);
            
            return user;
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
    private void registerLimitCheck() throws IOException {
        //一天内最多注册10个用户
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date createTimeFloor = cal.getTime();
        cal.add(Calendar.DATE, 1);
        Date createTimeCeil = cal.getTime();
        
        int count = searcher.count(User.Query.builder().createTimeFloor(createTimeFloor).createTimeCeil(createTimeCeil).build().toLuceneQuery());
        if(count>=10) throw new ResponseStatusException(CONFLICT, "今天注册用户数已达上限");
    }
    
    @ManagedOperation(description="设置/取消用户为管理员")
    public void setAdmin(String id, boolean admin) throws IOException {
        readerLock.writeLock().lock();
        try{
            User user = get(id);
            if(user==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户"+id+"不存在");
            
            user.admin = admin;
            
            writer.updateDocument(new Term("id", id), user.toDoc());
            writer.commit();
            lucene_refresh();
            
            cache.invalidate(id);
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
    public Pair<Long, List<User>> search(User.Query query, Sort sort, int pageNo, int pageSize) throws IOException {
        readerLock.readLock().lock();
        try{
            TopDocs topDocs = searcher.search(query.toLuceneQuery(), pageNo*pageSize, sort);
            
            int fromIndex = (pageNo-1)*pageSize;
            if(fromIndex>=topDocs.totalHits.value) return Pair.of(0L, Collections.emptyList());
            
            int toIndex = Math.min(fromIndex + pageSize, (int)topDocs.totalHits.value);
            
            List<User> users = new ArrayList<>(toIndex-fromIndex);
            for(; fromIndex<toIndex; fromIndex++){
                users.add(User.fromDoc(searcher.doc(topDocs.scoreDocs[fromIndex].doc)));
            }
            
            return Pair.of(topDocs.totalHits.value, users);
        }finally{
            readerLock.readLock().unlock();
        }
    }

    public int count(User.Query query) throws IOException{
        readerLock.readLock().lock();
        try{
            return searcher.count(query.toLuceneQuery());
        }finally{
            readerLock.readLock().unlock();
        }
    }
    
    /**
     * 
     * @param userId
     * @param bookPath
     * @param favor
     * @return 有改动时返回true
     * @throws IOException
     */
    public boolean toggleBookFavor(String userId, String bookPath, boolean favor) throws IOException {
        readerLock.writeLock().lock();
        try{
            User user = get(userId);
            if(user==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户"+userId+"不存在");
            if(user.favorBooks==null) user.favorBooks = new HashMap<>();
            if(favor) {
                if(user.favorBooks.containsKey(bookPath)) return false;
                user.favorBooks.put(bookPath, new Date());
            }else{
                if(!user.favorBooks.containsKey(bookPath)) return false;
                user.favorBooks.remove(bookPath);
            }
            
            writer.updateDocument(new Term("id", userId), user.toDoc());
            writer.commit();
            lucene_refresh();
            
            cache.invalidate(userId);
            
            return true;
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
    public void delete(String id) throws IOException {
        User user;
        readerLock.writeLock().lock();
        try{
            user = get(id);
            if(user==null) return;
            
            writer.deleteDocuments(new Term("id", id));
            writer.commit();
            lucene_refresh();

            cache.invalidate(id);
        }finally{
            readerLock.writeLock().unlock();
        }
        if(user.favorBooks!=null) bookDao.correctFavor(user.favorBooks.keySet());
    }
    
    public void onBookDelete(String path) throws IOException {
        readerLock.writeLock().lock();
        try{
            TopDocs topDocs = searcher.search(User.Query.builder().favorBookPaths(ImmutableSet.of(path)).build().toLuceneQuery(), Integer.MAX_VALUE);
            if(topDocs.totalHits.value==0) return;
            
            Set<String> changedIds = new HashSet<>();
            for(int i=0; i<topDocs.totalHits.value; i++){
                User user = User.fromDoc(searcher.doc(topDocs.scoreDocs[i].doc));
                if(user.favorBooks==null || !user.favorBooks.containsKey(path)) continue;
                user.favorBooks.remove(path);
                
                writer.deleteDocuments(User.Query.builder().id(user.id).build().toLuceneQuery());
                writer.addDocument(user.toDoc());
                changedIds.add(user.id);
            }
            writer.commit();
            lucene_refresh();
            
            cache.invalidateAll(changedIds);
        }finally{
            readerLock.writeLock().unlock();
        }
    }
    
}