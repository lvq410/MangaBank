var Resolution = localStorage.getItem('Resolution') || 'SD';
if(Resolution) $('[name=resolution][value='+Resolution+']').prop('checked', true);

var path = Tparams()['path'];
if(!path){
    mui.openWindow({url:'index.html'});
}

var Book;

rget('login', null, null, function(isLogin){
    if(isLogin){
        rget('book', {path:path}, null, function(book){
            if(!book){
                alert('本子不存在！')
                mui.openWindow({url:'index.html'});
                return;
            }
            Book = book;
            init();
        },'加载本子中')
        return;
    }
    mui.openWindow({url: 'login.html'});
}, '检查登录中')

var Confirm,Alert;
var imgLoadOffset = 10;
var curImg;

function init(){
    $('title').html(Book.titles[0]);
    $('#title').html(Book.titles[0]);
    $('#titles').html(tpl_bookTitles(Book.titles));
    $('#cover').attr('src','img?resolution=Thumbnail&path='+encodeURIComponent(Book.coverPath));
    $('#tags').html($tpl(tpl_bookTags)(Book.tags));
    $('#imgs').html(tpl_imgs(Book.imgPaths));
    if(Book.collection){
        $('#child').html(tpl_childBooks(Book.children));
    }
    
    mui('#popMenu').on('change', 'input', function() {
        var orig = Resolution;
        Resolution = $('[name=resolution]:checked').val()
        if(!Resolution) $(this).prop('checked', true);
        Resolution = $(this).val();
        localStorage.setItem('Resolution', Resolution);
        if(orig != Resolution) {
            $('#imgs').html(tpl_imgs(Book.imgPaths));
        }
    });
}

var tpl_bookTitles = $tpl(function(titles) {
    titles.forEach((title)=>{
        /*<li class="mui-table-view-cell" style="padding:0"><h4>{Tigh(title)}</h4></li>*/
    })
});
var tpl_bookTags = $tpl(function(tags){
    (tags||[]).forEach((tag)=>{
        /*<span class="mui-badge mui-badge-{BadgeClss[Tobj.hashCode(tag)%BadgeClss.length]}" style="margin-right:5px;">{tag}</span>*/
    });
});
var tpl_childBooks = $tpl(function(books) {
    books.forEach((book) => {
        /*<li class="mui-table-view-cell mui-media">
            <a href="book.html?path={encodeURIComponent(book.path)}">
                <img class="mui-media-object mui-pull-left" src="img?resolution=Thumbnail&path={encodeURIComponent(book.coverPath)}">
                <div class="mui-media-body">{book.titles[0]}</div>
            </a>
        </li>*/
    })
});

var tpl_imgs = $tpl(function(imgPaths) {
    imgPaths.forEach((imgPath)=>{
        /*<img src="img?resolution={Resolution}&path={encodeURIComponent(imgPath)}" style="width:100%"></img>*/
    })
});

