package com.lvt4j.mangabank.po;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;

import com.lvt4j.mangabank.MangaBankAPP;
import com.google.common.collect.ImmutableMap;
import com.lvt4j.mangabank.LuceneUtils;

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
public class Tag {

    public static final int RootId = 0;
    
    public int id;
    public String tag;
    
    /**
     * 类同标签id
     * 意外情况可能会有值不同，但是意思相同的标签，比如'18x' 和 '18R'，这种情况下可以将其设为类同标签
     */
    public Set<Integer> alikeIds;
    
    /**
     * 打上该标签的book的数量
     */
    public int tagedCount;
    
    public Date createTime;
    public Date updateTime;
    
    @SneakyThrows
    public Document toDoc() {
        Document doc = new Document();
        doc.add(new StoredField("_all", new BytesRef(MangaBankAPP.ObjectMapper.writeValueAsBytes(this))));
        
        LuceneUtils.intField(doc, true, true, false, "id", id);
        
        doc.add(new TextField("tag", tag, Store.NO));
        LuceneUtils.keywordField(doc, true, false, "tag.keyword", tag);
        
        LuceneUtils.intsField(doc, true, false, "alikeIds", alikeIds);
        
        LuceneUtils.intField(doc, true, true, false, "tagedCount", tagedCount);
        
        LuceneUtils.longField(doc, true, true, false, "createTime", createTime.getTime());
        LuceneUtils.longField(doc, true, true, false, "updateTime", updateTime.getTime());
        
        return doc;
    }
    
    @SneakyThrows
    public static Tag fromDoc(Document doc) {
        return MangaBankAPP.ObjectMapper.readValue(doc.getBinaryValue("_all").bytes, Tag.class);
    }
    
    @Data@Builder
    @NoArgsConstructor@AllArgsConstructor
    public static class Query {
        
        public static final Sort CreateTimeAsc = new Sort(new SortField("createTime", SortField.Type.LONG, false));
        public static final Sort TagedCountDescUpdateTimeDesc = new Sort(new SortField("tagedCount", SortField.Type.INT, true), new SortField("updateTime", SortField.Type.LONG, true));
        
        public static final Map<String, SortField.Type> SortFieldTypes = ImmutableMap.<String, SortField.Type>builder()
            .put("id", SortField.Type.INT)
            .put("tag.keyword", SortField.Type.STRING)
            .put("tagedCount", SortField.Type.INT)
            .put("createTime", SortField.Type.LONG)
            .put("updateTime", SortField.Type.LONG)
        .build();
        
        public Integer id;
        public Integer idNot;
        public String tag;
        public String tagPhrase;
        
        public Collection<Integer> alikeIdsAny;
        
        public BooleanQuery toLuceneQuery(IndexReader reader, Analyzer analyzer) {
            BooleanQuery.Builder query = new BooleanQuery.Builder();
            query.add(new MatchAllDocsQuery(), Occur.MUST);
            
            if(id!=null) query.add(IntPoint.newExactQuery("id", id), Occur.MUST);
            if(idNot!=null) query.add(IntPoint.newExactQuery("id", idNot), Occur.MUST_NOT);
            
            if(StringUtils.isNotBlank(tag)) query.add(new TermQuery(new Term("tag.keyword", tag)), Occur.MUST);
            
            if(StringUtils.isNotBlank(tagPhrase)){
                query.add(LuceneUtils.phraseQuery(reader, analyzer, "tag", tagPhrase), Occur.MUST);
            }
            
            if(CollectionUtils.isNotEmpty(alikeIdsAny)){
                BooleanQuery.Builder alikeIdsQuery = new BooleanQuery.Builder();
                for(Integer alikeId : alikeIdsAny){
                    alikeIdsQuery.add(IntPoint.newExactQuery("alikeIds", alikeId), Occur.SHOULD);
                }
                alikeIdsQuery.setMinimumNumberShouldMatch(1);
                query.add(alikeIdsQuery.build(), Occur.MUST);
            }
            
            return query.build();
        }
    }
    
    
    
}