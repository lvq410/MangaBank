
function register(){
    var userId = $('#userId').val().trim();
    if(!userId) return mui.alert('请输入账号');
    var pwd = $('#pwd').val().trim();
    if(!pwd) return mui.alert('请输入密码');
    if(pwd.length<5 || pwd.length>18) return mui.alert('密码长度需在5~18位之间');
    var confirmPwd = $('#confirmPwd').val().trim();
    if(pwd!=confirmPwd) return mui.alert('两次输入的密码不一致');
    rpost("register", {id:userId,pwd:pwd}, null, function(){
        var redirect = Tparams().redirect||'index.html';
        mui.openWindow({
            url: redirect
        });
    }, '注册并登录中');
}

function toLogin(){
    mui.openWindow({
        url: Url('login.html', Tparams())
    });
}