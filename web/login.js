var UserIdPwd = Tjson(localStorage.getItem('UserIdPwd'));
function Switch(selector){
    var ele = $(selector);
    this.firstCheck = false;
    this.checked = function(){
        return ele.hasClass("mui-active");
    };
    this.check = function(){
        if(this.checked()) return;
        if(!this.firstCheck){
            ele.addClass('mui-active');
            this.firstCheck = true;
        }else mui(selector)['switch']().toggle();
    };
    this.uncheck = function(){
        if(!this.checked()) return;
        mui(selector)['switch']().toggle();
    }
}
var rememberUserId = new Switch('#rememberUserId');
if(UserIdPwd) {
    rememberUserId.check();
    $('#userId').val(UserIdPwd.id);
    $('#pwd').val(UserIdPwd.pwd);
}

function login(){
    var userId = $('#userId').val().trim();
    if(!userId) return mui.alert('请输入账号');
    var pwd = $('#pwd').val().trim();
    if(!pwd) return mui.alert('请输入密码');
    if(pwd.length<5 || pwd.length>18) return mui.alert('密码长度需在5~18位之间');
    var param = {id:userId,pwd:pwd};
    rpost("login", param, null, function(rst){
        if(!rst) return mui.alert('账号或密码不正确！');
        if (rememberUserId.checked()) localStorage.setItem('UserIdPwd', JSON.stringify(param));
        else localStorage.removeItem('UserIdPwd');
        
        var redirect = Tparams().redirect||'index.html';
        mui.openWindow({ url: redirect });
    }, '登录中');
}

function toRegister(){
    mui.openWindow({
        url: Url('register.html', Tparams())
    });
}