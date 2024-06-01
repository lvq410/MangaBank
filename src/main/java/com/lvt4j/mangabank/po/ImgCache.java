package com.lvt4j.mangabank.po;

import static com.lvt4j.mangabank.MangaBankAPP.ObjectMapper;

import java.util.Date;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lvt4j.mangabank.MangaBankAPP;
import com.lvt4j.mangabank.LuceneUtils;
import com.lvt4j.mangabank.Resolution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

/**
 * 图片缩放后缓存数据
 * @author LV on 2023年11月15日
 */
public class ImgCache {

    public String path;
    
    public Resolution resolution;
    
    public Date createTime;
    
    public byte[] data;
    
    /**
     * 没有转化数据(data)时的原因
     */
    public String misReason;
    
    @SneakyThrows
    public Document toDoc() {
        Document doc = new Document();
        ObjectNode json = ObjectMapper.valueToTree(this);
        json.remove("data");
        doc.add(new StoredField("_all", MangaBankAPP.ObjectMapper.writeValueAsBytes(json)));
        
        LuceneUtils.keywordField(doc, true, false, "path", path);
        LuceneUtils.intField(doc, true, true, false, "resolution", resolution.value);
        
        LuceneUtils.dateField(doc, true, false, false, "createTime", createTime);
        
        if(data!=null) {
            LuceneUtils.intField(doc, true, true, false, "size", data.length);
            doc.add(new StoredField("data", data));
        }
        if(StringUtils.isNotBlank(misReason)) doc.add(new TextField("misReason", misReason, Store.NO));
        
        return doc;
    }
    
    @SneakyThrows
    public static ImgCache fromDoc(Document doc) {
        ImgCache img = MangaBankAPP.ObjectMapper.readValue(doc.getBinaryValue("_all").bytes, ImgCache.class);
        img.data = Optional.ofNullable(doc.getBinaryValue("data")).map(bs->bs.bytes).orElse(null);
        return img;
    }
    
    @Data@Builder
    @NoArgsConstructor@AllArgsConstructor
    public static class Query {
        
        public static final Sort PathAscResolutionAsc = new Sort(new SortField("path", SortField.Type.STRING, false), new SortField("resolution", SortField.Type.INT, false));
        
        public String path;
        public String pathPrefix; public Set<String> pathPrefixes;
        public Resolution resolution;
        
        public Date createTimeFloor, createTimeCeil;
        
        public BooleanQuery toLuceneQuery() {
            BooleanQuery.Builder query = new BooleanQuery.Builder();
            query.add(new MatchAllDocsQuery(), Occur.MUST);
            
            if(StringUtils.isNotBlank(path)) query.add(new TermQuery(new Term("path", path)), Occur.MUST);
            if(StringUtils.isNotBlank(pathPrefix)) query.add(new PrefixQuery(new Term("path", pathPrefix)), Occur.MUST);
            if(CollectionUtils.isNotEmpty(pathPrefixes)) {
                BooleanQuery.Builder pathPrefixesQuery = new BooleanQuery.Builder();
                pathPrefixes.forEach(pathPrefix->pathPrefixesQuery.add(new PrefixQuery(new Term("path", pathPrefix)), Occur.SHOULD));
                pathPrefixesQuery.setMinimumNumberShouldMatch(1);
                query.add(pathPrefixesQuery.build(), Occur.MUST);
            }
            
            if(resolution!=null) query.add(IntPoint.newExactQuery("resolution", resolution.value), Occur.MUST);
            
            if(createTimeFloor!=null || createTimeCeil!=null) query.add(LongPoint.newRangeQuery("createTime",
                createTimeFloor==null?Long.MIN_VALUE:createTimeFloor.getTime(),
                createTimeCeil==null?Long.MAX_VALUE:createTimeCeil.getTime()), Occur.MUST);
            
            return query.build();
        }
    }
    
}
