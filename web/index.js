var QueryOnlyFavor = localStorage.getItem('QueryOnlyFavor')=='true';
$('#qOnlyFavor').prop('checked', QueryOnlyFavor);

rget('login', null, null, function(isLogin){
    if(isLogin){
        init();
        return;
    }
    mui.openWindow({
        url: 'login.html'
    });
}, '检查登录中')

var PageNo=1, PageSize=10;

function init(){
    PageSize = Math.max(PageSize, Math.ceil(window.innerHeight/62));
    
    mui.init({
        pullRefresh : {
            container:'#bookListContainer',
            up : {
                auto: true,
                height: 10,
                contentrefresh : "正在加载...",
                contentnomore:'没有更多数据了',
                callback : bookQuery
            }
        }
    });
    
    mui('.mui-scroll-wrapper').scroll();
    
    mui('.mui-title').on('tap', '#qClearBtn', onQClear);
    
    mui('#bookList').off().on('tap', 'li, .mui-pull-right', function(event){
        if($(this).is('.mui-pull-right')) {
            event.stopPropagation();
            toggleBookFavor($(this).parent());
        }else{
            var book = $(this).attrData();
            openBook(book.path);
        }
    });
    
    // 标签选择器在子页面
    window.addEventListener('message', function(event) {
        if('QueryTagIdsChange' == event.data){
            bookQuery(1);
        }
    }, false);
}

function onQInput(){
    delayCall(()=>bookQuery(1), 500);
}

function onQClear(){
    $('#q').val('');
    bookQuery(1);
}

function onQOnlyFavor() {
    QueryOnlyFavor = $('#qOnlyFavor').prop('checked');
    localStorage.setItem('QueryOnlyFavor', JSON.stringify(QueryOnlyFavor));
    bookQuery(1);
}


function bookQuery(pageNo) {
    if(pageNo) PageNo = pageNo;
    if(PageNo == 1) {
        $('#bookList').empty();
        mui('#bookListContainer').pullRefresh().refresh(true);
    }
    rpost('book/list', null, {
        q:$('#q').val(),
        pageNo:PageNo,
        pageSize:PageSize,
        tags: Tjson(localStorage.getItem('QueryTagIds') || '[]'),
        onlyFavor: QueryOnlyFavor
    }, function(rst){
        PageNo += 1;
        $('#bookList').append(tpl_books(rst.list));
        mui('#bookListContainer').pullRefresh().endPullupToRefresh(rst.list.length!=PageSize);
    });
}

function openBook(path){
    mui.openWindow({
        url:'book.html?path='+encodeURIComponent(path)
    });
}

function toggleBookFavor(li){
    var book = li.attrData();
    var favor=!book.favor;
    rpost('book/favor', { path: book.path, favor:favor }, null, function(rst) {
        book.favor = favor;
        li.replaceWith(tpl_books([book]));
    });
}

var tpl_books = $tpl(function(books) {
    books.forEach((book)=>{
        /*<li class="mui-table-view-cell" data="{Tigh(book)}">
            <img class="mui-media-object mui-pull-left" src="img?resolution=Thumbnail&path={encodeURIComponent(book.coverPath)}">
            <div class="mui-media-object mui-pull-right" style="margin-top:-5px">
                <span class="iconfont" style="height:48px;width:48px;font-size:32px;color:#E69F27;">{book.favor?'':''}</span>
            </div>
            <div class="mui-media-body">
                <p class='mui-ellipsis' style="color:black;">{Tigh(book.titles[0])}</p>
                <p class='mui-ellipsis'>*/
                    (book.tags||[]).forEach((tag) => {
                        /*<span class="mui-badge mui-badge-{BadgeClss[Tobj.hashCode(tag)%BadgeClss.length]}" style="margin-right:5px;">{tag}</span>*/
                    });
                /*</p>
            </div>
        </li>*/
    })
});


function tpl_bookNewTag(tag) {
    /*<span class="mui-badge" style="margin-right:5px;">{tag}</span>*/
}
