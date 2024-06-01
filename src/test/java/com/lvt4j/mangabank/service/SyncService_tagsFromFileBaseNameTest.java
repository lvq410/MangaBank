package com.lvt4j.mangabank.service;

import java.util.LinkedHashSet;

import org.apache.commons.collections4.IterableUtils;
import org.junit.Test;

public class SyncService_tagsFromFileBaseNameTest{

    @Test
    public void test(){
        String baseName = "";
        LinkedHashSet<String> tags = SyncService.tagsFromFileBaseName(baseName);
        assert tags.isEmpty();
        
        
        
        baseName = "name";
        tags = SyncService.tagsFromFileBaseName(baseName);
        assert tags.isEmpty();
        
        baseName = "[tag";
        tags = SyncService.tagsFromFileBaseName(baseName);
        assert tags.size()==1;
        assert IterableUtils.get(tags, 0).equals("tag");
        
        baseName = "[tag]";
        tags = SyncService.tagsFromFileBaseName(baseName);
        assert tags.size()==1;
        assert IterableUtils.get(tags, 0).equals("tag");
        
        baseName = "[tag1](tag2)";
        tags = SyncService.tagsFromFileBaseName(baseName);
        assert tags.size()==2;
        assert IterableUtils.get(tags, 0).equals("tag1");
        assert IterableUtils.get(tags, 1).equals("tag2");
        
        baseName = "[tag1 (tag2)]name";
        tags = SyncService.tagsFromFileBaseName(baseName);
        assert tags.size()==2;
        assert IterableUtils.get(tags, 0).equals("tag1");
        assert IterableUtils.get(tags, 1).equals("tag2");
        
        baseName = "[tag1 ({tag3}tag2)]name【tag4】";
        tags = SyncService.tagsFromFileBaseName(baseName);
        assert tags.size()==4;
        assert IterableUtils.get(tags, 0).equals("tag1");
        assert IterableUtils.get(tags, 1).equals("tag3");
        assert IterableUtils.get(tags, 2).equals("tag2");
        assert IterableUtils.get(tags, 3).equals("tag4");
        
        baseName = "name tag5]";
        tags = SyncService.tagsFromFileBaseName(baseName);
        assert tags.isEmpty();
        
    }
    
    
    
}
