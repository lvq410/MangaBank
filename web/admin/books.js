$(function(){
    widget_pager_init();
    widget_tagSelect_init();
    loadBook();
});


function loadBook(){
    var query = $('#queryDiv').formData()||{};
    rpost('book/list', $('#pager').pagerSerialize(), query, function(data){
        $('#pager').pagerCount(data.total);
        $('#booksTbl').html(tpl_books(data.list));
    }, '查询本子数据中');
}

function createTag(tag){
    setTag({})
}

function editTag(btn){
    var tag = $(btn).closest('tr').attrData();
    setTag(tag);
}

function setTag(tag){
    $('#editDialog').formData(tag);
    $('#editDialog').dialog({
        title: (tag.id?'编辑':'新增')+'标签',
        width: 600,
        buttons: {
            '确定': function() {
                var data = $('#editDialog').formData();
                rput('tag', {id:data.id}, data.tag, function() {
                    alert('保存成功');
                    $('#editDialog').dialog('close');
                    loadTag();
                }, '保存标签中');
            }
        }
    });
}

function delTag(btn){
    var tag = $(btn).closest('tr').attrData();
    if (!confirm('确定删除标签：' + tag.tag)) return;
    rdel('tag', { id: tag.id }, null, function() {
        alert('删除成功');
        loadTag();
    }, '删除标签中');
}

var tpl_books = $tpl(function(books){
    books.forEach((book)=>{
        /*<tr data="{Tigh(book)}">
            <td style="text-align:center"><img style="max-width:100px;max-height:100px" src="img?resolution=Thumbnail&path={encodeURIComponent(book.coverPath)}"></td>
            <td>
                <a href="book.html?path={encodeURIComponent(book.path)}">{Tigh(book.path)}</a><br>
                {tpl_tags(book.tags)}
            </td>
            <td>*/
                book.titles.forEach((title)=>{
                    /*<div>{Tigh(title)}</div>*/
                })
            /*</td>
            <td>{book.favor}</td>
            <td>{TtimestampFormat(book.createTime)}</td>
            <td>{TtimestampFormat(book.updateTime)}</td>
        </tr>*/
    });
})

var tpl_tags = $tpl(function(tags) {
    (tags||[]).forEach((tag) => {
        /*<span name data-type="obj" data-required class="badge badge-{BadgeClss[Tobj.hashCode(tag.id)%BadgeClss.length]}">
            {Tigh(tag.tag)}
        </span>*/
    })
});