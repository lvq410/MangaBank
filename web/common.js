var BadgeClss = ['primary','success','warning','danger','purple'];

function delayCall(callback){
    var time = window.delayCallTime = $.now();
    setTimeout(function(){
        if(time!=window.delayCallTime) return;
        callback();
    }, 500);
}

function Url(uri, params){
    if(!params || Tobj.isEmpty(params)) return uri;
    var url = uri+'?';
    for(var k in params) url += encodeURIComponent(k)+'='+encodeURIComponent(Tfnn(params[k],''))+'&';
    url = url.substring(0, url.length-1);
    return url;
}

/**
 * 封装ajax请求，restful形式get方法
 * @param url
 * @param params 合并到url里的请求参数
 * @param data 请求体
 * @param callback
 * @param waitingMsg
 */
function rget(url, params, data, callback, waitingMsg) {
    rest('get', url, params, data, callback, waitingMsg);
}
/**
 * 封装ajax请求，restful形式put方法
 * @param url
 * @param params 合并到url里的请求参数
 * @param data 请求体
 * @param callback
 * @param waitingMsg
 */
function rput(url, params, data, callback, waitingMsg) {
    rest('put', url, params, data, callback, waitingMsg);
}
/**
 * 封装ajax请求，restful形式post方法
 * @param url
 * @param params 合并到url里的请求参数
 * @param data 请求体
 * @param callback
 * @param waitingMsg
 */
function rpost(url, params, data, callback, waitingMsg) {
    rest('post', url, params, data, callback, waitingMsg);
}
/**
 * 封装ajax请求，restful形式delete方法
 * @param url
 * @param params 合并到url里的请求参数
 * @param data 请求体
 * @param callback
 * @param waitingMsg
 */
function rdel(url, params, data, callback, waitingMsg) {
    rest('delete', url, params, data, callback, waitingMsg);
}
/**
 * 封装ajax请求，restful形式patch方法
 * @param url
 * @param params 合并到url里的请求参数
 * @param data 请求体
 * @param callback
 * @param waitingMsg
 */
function rpatch(url, params, data, callback, waitingMsg) {
    rest('patch', url, params, data, callback, waitingMsg);
}

/**
 * 封装ajax请求为restful形式方法调用<br>
 * 处理了loading态等<br>
 * 响应结果会直接丢给callback处理
 * @param method
 * @param url
 * @param params 合并到url里的请求参数
 * @param data 请求体
 * @param callback
 * @param waitingMsg
 */
function rest(method, url, params, data, callback, waitingMsg) {
    if(waitingMsg!=null) Tloader.show(waitingMsg);
    if(params) url = Url(url, params);
    var body = data;
    if(body!=null && 'string'!=typeof body) body = JSON.stringify(body);
    $.ajax({
        url:url,
        type:method,
        dataType:'json',
        data:body,
        contentType:'application/json;charset=utf-8',
        success:function(rst){
            if(waitingMsg!=null) Tloader.hide(waitingMsg);
            console.info(waitingMsg, rst);
            if(callback) callback(rst);
        },
        error:function(request, msg, obj){
            if(waitingMsg!=null) Tloader.hide(waitingMsg);
            if(request && request.status){
                if(request.status==0) return alert('与服务器连接断开');
                else if (request.status == 401){
                    alert('请先登录');
                    location.reload();
                    return;
                }else if(request.status == 403) return alert('没有权限');
                else if(request.responseText) alert(request.responseText);
            }
        }
    });
}