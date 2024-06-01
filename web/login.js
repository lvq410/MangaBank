
function login(){
    var userId = $('#userId').val().trim();
    if(!userId) return mui.alert('请输入账号');
    var pwd = $('#pwd').val().trim();
    if(!pwd) return mui.alert('请输入密码');
    if(pwd.length<5 || pwd.length>18) return mui.alert('密码长度需在5~18位之间');
    rpost("login", {id:userId,pwd:pwd}, null, function(rst){
        if(!rst) return mui.alert('账号或密码不正确！');
        var redirect = Tparams().redirect||'index.html';
        mui.openWindow({
            url: redirect
        });
    }, '登录中');
}

function toRegister(){
    mui.openWindow({
        url: Url('register.html', Tparams())
    });
}