$(function(){
    widget_pager_init();
    loadImgCache();
});


function loadImgCache(){
    var query = $('#queryDiv').formData()||{};
    var pager = $('#pager').pagerSerialize();
    query.pageNo = pager.pageNo;
    query.pageSize = pager.pageSize;
    
    rpost('imgCache/list', null, query, function(data){
        $('#pager').pagerCount(data.count);
        $('#imgCachesTbl tbody').html(tpl_imgCaches(data.list));
    }, '查询标签数据中');
}

function clearImgCache(){
    if(!confirm('确定要清空当前条件的缓存图片吗？')) return;
    
    var query = $('#queryDiv').formData()||{};
    rpost('imgCache/clear', null, query, function(data) {
        alert('清空成功');
        loadImgCache();
    }, '清空缓存图片中');
}

var tpl_imgCaches = $tpl(function(imgCaches){
    imgCaches.forEach((imgCache)=>{
        /*<tr data="{Tigh(imgCache)}">
            <td>{Tigh(imgCache.path)}</td>
            <td>{Tigh(imgCache.resolution)}</td>
            <td style="text-align:center"><img style="max-width:100px;height:100px" onclick="window.open('img?resolution={imgCache.resolution}&path={encodeURIComponent(imgCache.path)}', '_blank')" src="img?resolution={imgCache.resolution}&path={encodeURIComponent(imgCache.path)}"></td>
            <td>{TtimestampFormat(imgCache.createTime)}</td>
        </tr>*/
    });
})