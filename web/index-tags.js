var QueryTagIds = Tjson(localStorage.getItem('QueryTagIds') || '[]');
var PageNo=1, PageSize=10;

$(function() {
    PageSize = Math.max(PageSize, Math.ceil(window.innerHeight/42));
    mui.init({
        pullRefresh : {
            container:'#tagsContainer',
            up : {
                auto: true,
                height: 10,
                contentrefresh : "正在加载...",
                contentnomore:'没有更多数据了',
                callback : tagQuery
            }
        }
    });
    
    mui('.mui-search').on('tap', '#tagQClearBtn', onTagQClear);
    
    mui('.mui-scroll-wrapper').scroll();
});


function onTagQInput(){
    delayCall(()=>tagQuery(1), 500);
}

function onTagQClear(){
    $('#tagQ').val('');
    tagQuery(1);
}

function tagQuery(pageNo) {
    if(pageNo) PageNo = pageNo;
    if(PageNo == 1) {
        $('#tagsUl').empty();
        mui('#tagsContainer').pullRefresh().refresh(true);
    }
    rpost('tag/list', null, {
        tagPhrase:$('#tagQ').val(),
        pageNo:PageNo,
        pageSize:PageSize,
    }, function(rst){
        PageNo += 1;
        $('#tagsUl').append(tpl_tags(rst.list));
        mui('#tagsContainer').pullRefresh().endPullupToRefresh(rst.list.length!=PageSize);
    });
}

function onTagSelect(cbx) {
    var tagId = $(cbx).formData();
    var checked = $(cbx).prop('checked');
    if (checked) {
        QueryTagIds.push(tagId);
    } else {
        QueryTagIds = QueryTagIds.filter((id) => id != tagId);
    }
    localStorage.setItem('QueryTagIds', JSON.stringify(QueryTagIds));
    if(window.parent){
        window.parent.postMessage('QueryTagIdsChange');
    }
}

var tpl_tags = $tpl(function(tags){
    (tags||[]).forEach((tag)=>{
        /*<li class="mui-table-view-cell mui-checkbox mui-left">
            <input onchange="onTagSelect(this)" name="checkbox" data-type="int" value="{tag.id}" type="checkbox" {QueryTagIds.includes(tag.id)?'checked':''}>
            <div class="mui-media-body"><span class="mui-ellipsis">{tag.tag}</span></div>
            <span class="mui-pull-right mui-badge mui-badge-success" style="margin-right:5px;">{tag.tagedCount}</span>
        </li>*/
    });
});