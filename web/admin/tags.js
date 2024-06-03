$(function(){
    widget_sortFields_init();
    widget_sortabler_init();
    widget_pager_init();
    loadTag();
});


function loadTag(){
    var query = $('#queryDiv').formData()||{};
    var pager = $('#pager').pagerSerialize();
    query.pageNo = pager.pageNo;
    query.pageSize = pager.pageSize;
    
    rpost('tag/list', null, query, function(data){
        $('#pager').pagerCount(data.count);
        $('#tagsTbl').html(tpl_tags(data.list));
    }, '查询标签数据中');
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

var tpl_tags = $tpl(function(tags){
    tags.forEach((tag)=>{
        /*<tr data="{Tigh(tag)}">
            <td>{tag.id}</td>
            <td>{Tigh(tag.tag)}</td>
            <td>{tag.tagedCount}</td>
            <td>{TtimestampFormat(tag.createTime)}</td>
            <td>{TtimestampFormat(tag.updateTime)}</td>
            <td>
                <button type="button" class="btn btn-primary btn-minier" onclick="editTag(this)">编辑</button>
                <button type="button" class="btn btn-danger btn-minier" onclick="delTag(this)">删除</button>
            </td>
        </tr>*/
    });
})