var path = Tparams()['path'];
if(!path){
    location.href = 'books.html';
}

var Book; var DelImgPaths = [];

rget('book', {path:path}, null, function(book){
    if(!book){
        alert('本子不存在！')
        location.href = 'books.html';
        return;
    }
    Book = book;
    $('title').html(Book.titles[0]);
    $('#path').html(tpl_path(Book.path));
    $('#titles').html(tpl_bookTitles(Book.titles));
    
    $('#titles').sortable({
        handle: '.input-group-addon'
    });
    
    $('#cover').attr('src','img?resolution=SD&path='+encodeURIComponent(Book.coverPath));
    
    if(Book.path.includes('/')){ //非根book，无标签属性
        $('#tags').closest('.profile-info-row').hide();
    }else{
        $('#tags').html(tpl_tags(Book.tags));
        widget_tagSelect_init();
        $('#tags').sortable({});
    }
    
    if(Book.imgPaths.length){
        $('#imgs').html(tpl_imgs(Book.imgPaths));
        $('#imgs').sortable({});
    }else{
        $('#imgs').closest('div.row').hide();
    }

    if(Book.collection){
        $('#childTbl tbody').html(tpl_childBooks(Book.children));
    } else {
        $('#childTbl').hide();
    }
},'加载本子中')

function addTitle(){
    $('#titles').append(tpl_bookTitles(['']));
}

function saveTitle(){
    var titles = $('#titles').formData();
    if(!titles) return alert('请填写标题');
    
    rpatch('book/titles', { path: path }, titles, function() {
        alert('保存成功');
        location.reload();
    }, '保存中')
}

function addTag(){
    $('#tagSelector').select2Clear();
    $('#tagSelectDialog').dialog({
        title: '添加标签',
        buttons: {
            '确定': function() {
                var tag = $('#tagSelector').data('select2:data')
                if(!tag) return;
                $('#tags').append(tpl_tags([tag]));
                $('#tagSelectDialog').dialog('close');
            }
        }
    });
}

function saveTag() {
    var tags = $('#tags').formData()||[];
    if (!tags) return alert('请填写标签');
    var tagIds = tags.map((tag) => tag.id);
    rpatch('book/tags', { path: path }, tagIds, function() {
        alert('保存成功');
        location.reload();
    }, '保存中')
}

function setCoverPath(coverPath){
    rpatch('book/coverPath', { path: path }, coverPath, function() {
        alert('保存成功');
        location.reload();
    }, '保存中')
}

function delImg(btn){
    var imgPath = $(btn).closest('li').find('input').val();
    DelImgPaths.push(imgPath);
    $(btn).closest('li').remove();
}

function saveImgs(){
    var imgPaths = $('#imgs').formData()||[];
    rpatch('book/imgs', { path: path }, { imgPaths: imgPaths, delImgPaths: DelImgPaths }, function() {
        alert('保存成功');
        location.reload();
    }, '保存中')
}

var tpl_path = $tpl(function(path) {
    var nodes = path.split('/');
    for(var i = 0; i < nodes.length; i++) {
        var node = nodes[i];
        if (i == nodes.length - 1) {
            /*<span>{Tigh(node)}</span>*/
        } else {
            /*<a href="?path={encodeURIComponent(nodes.slice(0, i + 1).join('/'))}">{Tigh(node)}</a> / */
        }
    }
});

var tpl_bookTitles = $tpl(function(titles) {
    titles.forEach((title)=>{
        /*<div class="input-group">
            <span class="input-group-addon" style="cursor:grab;">☩</span>
            <input name data-required value="{Tigh(title)}" type="text" class="form-control">
            <div class="input-group-btn">
                <button onclick="$(this).closest('.input-group').remove()" type="button" class="btn btn-xs btn-danger" style="padding-top:6px;">-</button>
            </div>
        </div>*/
    })
});

var tpl_tags = $tpl(function(tags) {
    (tags||[]).forEach((tag) => {
        /*<span name data-type="obj" data-required class="badge badge-{BadgeClss[Tobj.hashCode(tag.id)%BadgeClss.length]}">
            <span name="id" data-type="int" data-func="text" hidden>{tag.id}</span>
            <span name="tag" data-func="text"  style="cursor:grab;">{Tigh(tag.tag)}</span>
            <i onclick="$(this).closest('.badge').remove()" class="ace-icon fa fa-close" style="cursor:pointer;"></i>
        </span>*/
    })
});

var tpl_childBooks = $tpl(function(books) {
    (books||[]).forEach((book)=>{
        /*<tr data="{Tigh(book)}">
            <td style="text-align:center"><div style="position:relative">
                <img style="max-width:100px;max-height:100px" src="img?resolution=Thumbnail&path={encodeURIComponent(book.coverPath)}">
                <span onclick="setCoverPath('{Tigh(book.coverPath)}')" class="label-holder" style="position:absolute;right:0;bottom:0"><span class="label label-purple arrowed-in" style="cursor:pointer">设为封面</span></span>
            </div></td>
            <td><a href="book.html?path={encodeURIComponent(book.path)}">{Tigh(book.path)}</a></td>
            <td>*/
                book.titles.forEach((title)=>{
                    /*<div>{Tigh(title)}</div>*/
                })
            /*</td>
            <td>{TtimestampFormat(book.createTime)}</td>
            <td>{TtimestampFormat(book.updateTime)}</td>
        </tr>*/
    });
});

var tpl_imgs = $tpl(function(imgPaths) {
    imgPaths.forEach((imgPath)=>{
        var name = imgPath.substr(Book.path.length+1)
        /*<li>
            <input name type="hidden" value="{Tigh(imgPath)}"/>
            <img onclick="window.open('img?resolution=Orig&path={encodeURIComponent(imgPath)}','_blank')" src="img?resolution=Thumbnail&path={encodeURIComponent(imgPath)}" style="height:100px"/>
            <div class="tags">
                <span class="label-holder"><span class="label label-info arrowed-in">{Tigh(name)}</span></span>
                <span onclick="setCoverPath('{Tigh(imgPath)}')" class="label-holder"><span class="label label-purple" style="cursor:pointer">设为封面</span></span>
            </div>
            <div class="tools tools-top in" style="text-align:right;background: transparent;">
                <a onclick="delImg(this)" href="javascript:;" style="background-color: rgba(0, 0, 0, .55);"><i class="ace-icon fa fa-times red"></i></a>
            </div>
        </li>*/
    })
});


