$(function(){
    widget_pager_init();
    loadUser();
});


function loadUser(){
    var query = $('#queryDiv').formData()||{};
    rpost('user/list', $('#pager').pagerSerialize(), query, function(data){
        $('#pager').pagerCount(data.count);
        $('#usersTbl').html(tpl_users(data.list));
    }, '查询用户数据中');
}

function setAdmin(btn){
    var user = $(btn).closest('tr').attrData();
    $('#adminSetDialog').formData(user);
    $('#adminSetDialog').dialog({
        title: '设置管理员',
        width: 600,
        buttons: {
            '确定': function() {
                var data = $('#adminSetDialog').formData();
                rpatch('user/admin', data, null, function() {
                    alert('设置成功');
                    $('#adminSetDialog').dialog('close');
                    loadUser();
                }, '设置中');
            }
        }
    });
}

var tpl_users = $tpl(function(users){
    users.forEach((user)=>{
        var favorBooks = user.favorBooks||{};
        /*<tr data="{Tigh(user)}">
            <td>{user.id}</td>
            <td>{Tobj.size(favorBooks)}</td>
            <td>{TtimestampFormat(user.createTime)}</td>
            <td>
                {user.admin?'是':'否'}
                <button type="button" class="btn btn-primary btn-minier" onclick="setAdmin(this)">设置</button>
            </td>
        </tr>*/
    });
})