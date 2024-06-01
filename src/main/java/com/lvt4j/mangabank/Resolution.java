package com.lvt4j.mangabank;

import lombok.AllArgsConstructor;

/**
 * 分辨率标准
 *
 * @author chanceylee on 2024年5月22日
 */
@AllArgsConstructor
public enum Resolution{

    /** 缩略图 */
    Thumbnail(0, 100)
    
    /** 标清 */
    ,SD(1, 720)
    
    /** 高清 */
    ,HD(2, 1080)
    
    /** 超清 */
    ,UHD(3, 2160)
    
    /** 原画（不是纯原画，而是经由webp处理过保持分辨率不变的图） */
    ,Orig(4, 0)
    
    /** 纯原画 */
    ,OrigRaw(-1, -1)
    ;
    
    public final int value;
    public final int width;
    
}
