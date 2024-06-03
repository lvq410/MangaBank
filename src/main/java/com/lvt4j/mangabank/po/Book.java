package com.lvt4j.mangabank.po;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;

import com.lvt4j.mangabank.MangaBankAPP;
import com.google.common.collect.ImmutableMap;
import com.lvt4j.mangabank.LuceneUtils;

import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

/**
 *
 * @author LV on 2023年9月10日
 */
@Data
public class Book {

    /**
     * 文件路径
     * 相对于{@link Config#dataDir}的相对路径
     * 不能以/开头及结尾
     */
    public String path;
    
    /**
     * 标题
     * 第一个元素为文件名（无扩展名）
     * 其他为备用名、曾用名，其他语言的标题等
     */
    public LinkedHashSet<String> titles;
    /**
     * 封面图片
     * 相对于{@link Config#dataDir}的相对路径
     * 可能指向压缩包内部文件
     * 如 mycomic/vol1.zip/0001.jpg
     * 不能以/开头
     */
    public String coverPath;
    
    /** 是否是个Collection */
    public boolean collection;
    
    /**
     * book对象的创建时间
     */
    public Date createTime;
    
    /**
     * {@link #path}对应文件的最近修改时间
     */
    public Date updateTime;
    
    //================================================================根book才有的字段
    /** 标签 */
    public LinkedHashSet<Integer> tags;
    /** 收藏数量 */
    public int favor;
    
    //================================================================非根book字段
    /**
     * 父文件夹路径
     */
    public String parentPath;
    /**
     * 在父文件夹中的排序
     */
    public Integer sequenceInParent;
    
    
    @SneakyThrows
    public Document toDoc() {
        Document doc = new Document();
        doc.add(new StoredField("_all", MangaBankAPP.ObjectMapper.writeValueAsBytes(this)));
        
        LuceneUtils.keywordField(doc, true, false, "path", path);
        
        LuceneUtils.textsField(doc, false, "titles", titles);
        
        LuceneUtils.keywordField(doc, false, false, "coverPath", coverPath);
        
        LuceneUtils.intsField(doc, true, false, "tags", tags);
        LuceneUtils.intField(doc, true, true, false, "tags.size", CollectionUtils.size(tags));
        
        LuceneUtils.bitField(doc, false,  "collection", collection);
        
        LuceneUtils.keywordField(doc, false, false, "parentPath", parentPath);
        LuceneUtils.bitField(doc, false,  "parentPath.exist", StringUtils.isNotBlank(parentPath));
        LuceneUtils.intField(doc, false, true, false, "sequenceInParent", sequenceInParent);
        
        LuceneUtils.dateField(doc, true, true, false, "createTime", createTime);
        LuceneUtils.dateField(doc, true, true, false, "updateTime", updateTime);
        
        LuceneUtils.intField(doc, true, true, false, "favor", favor);
        
        return doc;
    }
    
    @SneakyThrows
    public static Book fromDoc(Document doc) {
        return MangaBankAPP.ObjectMapper.readValue(doc.getBinaryValue("_all").bytes, Book.class);
    }
    
    @Data@Builder
    @NoArgsConstructor@AllArgsConstructor
    public static class Query {
        
        public static final Sort SequenceInParentAsc = new Sort(new SortField("sequenceInParent", SortField.Type.INT, false));
        public static final Sort PathAsc = new Sort(new SortField("path", SortField.Type.STRING, false));
        
        public static final Sort FavorDescUpdateDesc = new Sort(new SortField("favor", SortField.Type.INT, true), new SortField("updateTime", SortField.Type.LONG, true));
        
        public static final Map<String, SortField.Type> SortFieldTypes = ImmutableMap.<String, SortField.Type>builder()
            .put("sequenceInParent", SortField.Type.INT)
            .put("path", SortField.Type.STRING)
            .put("favor", SortField.Type.INT)
            .put("createTime", SortField.Type.LONG)
            .put("updateTime", SortField.Type.LONG)
            .put("tags.size", SortField.Type.INT)
        .build();
        
        public String path;
        public String pathPrefix; public Collection<String> pathPrefixes;
        public Collection<String> pathContains;
        
        public String titlePhrase;
        
        public String coverPath;
        
        public Integer tagSizeFloor, tagSizeCeil;
        public Collection<Integer> tags;
        
        public Boolean root;
        public String parentPath;
        
        public BooleanQuery toLuceneQuery(IndexReader reader, Analyzer analyzer) {
            BooleanQuery.Builder query = new BooleanQuery.Builder();
            query.add(new MatchAllDocsQuery(), Occur.MUST);
            
            if(StringUtils.isNotBlank(path)) query.add(new TermQuery(new Term("path", path)), Occur.MUST);
            if(StringUtils.isNotBlank(pathPrefix)) query.add(new PrefixQuery(new Term("path", pathPrefix)), Occur.MUST);
            if(CollectionUtils.isNotEmpty(pathPrefixes)){
                BooleanQuery.Builder pathPrefixQuery = new BooleanQuery.Builder();
                for(String pathPrefix: pathPrefixes) pathPrefixQuery.add(new PrefixQuery(new Term("path", pathPrefix)), Occur.SHOULD);
                pathPrefixQuery.setMinimumNumberShouldMatch(1);
                query.add(pathPrefixQuery.build(), Occur.MUST);
            }
            if(pathContains!=null) {
                BooleanQuery.Builder pathContainQuery = new BooleanQuery.Builder();
                for(String pathContain : pathContains) pathContainQuery.add(new TermQuery(new Term("path", pathContain)), Occur.SHOULD);
                pathContainQuery.setMinimumNumberShouldMatch(1);
                query.add(pathContainQuery.build(), Occur.MUST);
            }
            
            if(StringUtils.isNotBlank(titlePhrase)) query.add(LuceneUtils.phraseQuery(reader, analyzer, "titles", titlePhrase), Occur.MUST);
            
            if(StringUtils.isNotBlank(coverPath)) query.add(new TermQuery(new Term("coverPath", coverPath)), Occur.MUST);
            
            if(tagSizeFloor!=null || tagSizeCeil!=null) query.add(IntPoint.newRangeQuery("tags.size",
                tagSizeFloor==null?Integer.MIN_VALUE:tagSizeFloor,
                tagSizeCeil==null?Integer.MAX_VALUE:tagSizeCeil), Occur.MUST);
            if(CollectionUtils.isNotEmpty(tags)) for(Integer tag : tags) query.add(IntPoint.newExactQuery("tags", tag), Occur.MUST);
            
            if(root!=null) query.add(IntPoint.newExactQuery("parentPath.exist", root?0:1), Occur.MUST);
            if(StringUtils.isNotBlank(parentPath)) query.add(new TermQuery(new Term("parentPath", parentPath)), Occur.MUST);
            
            return query.build();
        }
    }
    
}