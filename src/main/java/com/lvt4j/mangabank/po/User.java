package com.lvt4j.mangabank.po;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;

import com.lvt4j.mangabank.MangaBankAPP;
import com.lvt4j.mangabank.LuceneUtils;

import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
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
 * @author LV on 2023年11月21日
 */
@Data
public class User {

    public String id;
    public String pwdMd5;
    
    public boolean admin;
    
    public Date createTime;
    
    /**
     * 收藏本子的path 及 收藏时间
     */
    public Map<String, Date> favorBooks;
    
    @SneakyThrows
    public Document toDoc() {
        Document doc = new Document();
        doc.add(new StoredField("_all", MangaBankAPP.ObjectMapper.writeValueAsBytes(this)));
        
        LuceneUtils.keywordField(doc, true, false, "id", id);
        LuceneUtils.keywordField(doc, false, false, "pwdMd5", pwdMd5);
        
        LuceneUtils.bitField(doc, false, "admin", admin);
        
        LuceneUtils.dateField(doc, true, true, false, "createTime", createTime);
        
        if(favorBooks != null) LuceneUtils.keywordsField(doc, false, false, "favorBookPaths", favorBooks.keySet());
        
        return doc;
    }
    
    @SneakyThrows
    public static User fromDoc(Document doc) {
        return MangaBankAPP.ObjectMapper.readValue(doc.getBinaryValue("_all").bytes, User.class);
    }
    
    @Data@Builder
    @NoArgsConstructor@AllArgsConstructor
    public static class Query {
        public static final Sort CreateTimeAsc = new Sort(new SortField("createTime", SortField.Type.LONG, false));
        
        public String id;
        
        public Set<String> favorBookPaths;
        
        public Date createTimeFloor, createTimeCeil;
        
        public BooleanQuery toLuceneQuery() {
            BooleanQuery.Builder query = new BooleanQuery.Builder();
            query.add(new MatchAllDocsQuery(), Occur.MUST);
            
            if(StringUtils.isNotBlank(id)) query.add(new TermQuery(new Term("id", id)), Occur.MUST);
            
            if(CollectionUtils.isNotEmpty(favorBookPaths)) {
                for(String favorBookPath: favorBookPaths){
                    query.add(new TermQuery(new Term("favorBookPaths", favorBookPath)), Occur.MUST);
                }
            }
            
            if(createTimeFloor!=null || createTimeCeil!=null) query.add(LongPoint.newRangeQuery("createTime",
                createTimeFloor==null?Long.MIN_VALUE:createTimeFloor.getTime(),
                createTimeCeil==null?Long.MAX_VALUE:createTimeCeil.getTime()), Occur.MUST);
            
            return query.build();
        }
    }
}