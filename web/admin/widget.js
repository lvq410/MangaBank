/**
 * 翻页器初始化<br>
 * 必须是div元素,以属性widget="pager"标识<br>
 * 必须的html属性:<br>
 * ⊙onpage:翻页后的回调脚本<br>
 * 可配置的html属性:<br>
 * ⊙show-page-count:是否显示总页数,默认false<br>
 * ⊙show-count:是否显示总条数,默认false<br>
 * ⊙show-go:是否显示跳转按钮,默认false<br>
 * ⊙show-page-size:是否显示每页个数,默认false<br>
 * ⊙page-size:每页个数,默认10<br>
 */
function widget_pager_init() {
    $('div[widget=pager]').each(function () {
        var widget = $(this);
        if (widget.data('widget-init')) return;
        var showPageCount = widget.attr('show-page-count') == 'true';
        var showCount = widget.attr('show-count') == 'true';
        var showGo = widget.attr('show-go') == 'true';
        var showPageSize = widget.attr('show-page-size') == 'true';
        var pageSize = LVT.int(widget.attr('page-size'), 10);
        var pagerDiv = $('<div widget="pager">' +
            '<div class="pager-ele pager-btn pager-prev">←</div>' +
            '<div class="pager-ele">第<span class="pager-page-no input" contenteditable="true">1</span>'
            + '<span' + (showPageCount ? '' : ' style="display:none;"') + ' class="pager-page-count-box">'
            + '/<span class="pager-page-count">1</span></span>'
            + '页'
            + '<span' + (showCount ? '' : ' style="display:none;"') + ' class="pager-count-box">'
            + '，共<span class="pager-count">0</span>条</span>'
            + '</div>' +
            '<div' + (showGo ? '' : ' style="display:none;"') + ' class="pager-ele pager-btn pager-go">go</div>' +
            '<div' + (showPageSize ? '' : ' style="display:none;"') + ' class="pager-ele">'
            + '每页<span class="pager-page-size input" contenteditable="true">' + pageSize + '</span>个</div>' +
            '<div class="pager-ele pager-btn pager-next">→</div>' +
            '</div>');
        pagerDiv.attr('id', widget.attr('id'));
        pagerDiv.attr('onpage', widget.attr('onpage'));
        pagerDiv.attr('class', (widget.attr('class') || '') + ' pager');
        pagerDiv.attr('style', widget.attr('style'));
        pagerDiv.find('.pager-prev').click(function () {
            var pagerDiv = $(this).closest('[widget=pager]');
            pagerDiv.pagerPageNo(pagerDiv.pagerPageNo() - 1);
            var onpage = pagerDiv.attr('onpage');
            if (onpage) eval(onpage);
        });
        pagerDiv.find('.pager-go').click(function () {
            var pagerDiv = $(this).closest('[widget=pager]');
            var onpage = pagerDiv.attr('onpage');
            if (onpage) eval(onpage);
        });
        pagerDiv.find('.pager-next').click(function () {
            var pagerDiv = $(this).closest('[widget=pager]');
            pagerDiv.pagerPageNo(pagerDiv.pagerPageNo() + 1);
            var onpage = pagerDiv.attr('onpage');
            if (onpage) eval(onpage);
        });
        widget.replaceWith(pagerDiv);
        pagerDiv.data('widget-init', true);
    });
}

/** 翻页器扩展：设置或获取当前页数 */
jQuery.fn.pagerPageNo = function (pageNo) {
    var pagerDiv = this.closest('[widget=pager]');
    var pageNoEle = pagerDiv.find('.pager-page-no');
    pageNo = pageNo == null ?
        LVT.int(pageNoEle.text(), 1)
        : LVT.int(pageNo, 1);
    if (pageNo < 1) pageNo = 1;
    pageNoEle.text(pageNo);
    return pageNo;
};
/** 翻页器扩展：设置或获取每页大小 */
jQuery.fn.pagerPageSize = function (pageSize) {
    var pagerDiv = this.closest('[widget=pager]');
    var pageSizeEle = pagerDiv.find('.pager-page-size');
    var defPageSize = LVT.int(pagerDiv.attr('page-size'), 10);
    pageSize = pageSize == null ?
        LVT.int(pageSizeEle.text(), defPageSize)
        : LVT.int(pageSize, defPageSize);
    if (pageSize < 1) pageSize = 10;
    pageSizeEle.text(pageSize);
    return pageSize;
};
/** 翻页器扩展：页数及每页大小序列化为json对象 */
jQuery.fn.pagerSerialize = function () {
    return {
        pageNo: this.pagerPageNo(),
        pageSize: this.pagerPageSize()
    }
}
/** 翻页器扩展：获取总页数 */
jQuery.fn.pagerPageCount = function () {
    var pagerDiv = this.closest('[widget=pager]');
    pageCountEle = pagerDiv.find('.pager-page-count');
    return LVT.int(pageCountEle.text());
}
/** 翻页器扩展：设置与获取总条数 */
jQuery.fn.pagerCount = function (count) {
    var pagerDiv = this.closest('[widget=pager]');
    countEle = pagerDiv.find('.pager-count');
    if (count == null) return LVT.int(countEle.text());
    count = LVT.int(count);
    countEle.text(count);
    var pagerPageCount = Math.max(1, Math.ceil(count / this.pagerPageSize()));
    pageCountEle = pagerDiv.find('.pager-page-count');
    pageCountEle.text(pagerPageCount);
}

/**
 * 标签选择器<br>
 * 必须是select元素,以属性widget="tag-select"标识
 */
function widget_tagSelect_init() {
    function select2Result(tag) {
        return {
            id: tag.id,
            text: tag.id + ':' + tag.tag,
            data: tag
        };
    };
    $('select[widget=tag-select]').each(function () {
        var widget = $(this);
        if (widget.data('widget-init')) return;
        widget.data('widget-init', true);
        widget.data('select2Result', select2Result);
        widget.select2({
            placeholder: '请选择标签',
            allowClear: true,
            multiple: widget.attr('select2multiple') == 'true',
            templateResult: (rst)=>rst.html?$(rst.html):rst.text,
            ajax: {
                url: 'tag/list',
                dataType: 'json',
                delay: 500,
                type: 'post',
                data: function (params) {
                    return JSON.stringify({
                        tagPhrase: params.term,
                        pageNo: params.page
                    });
                },
                contentType: 'application/json;charset=utf-8',
                processResults: function (rst, params) {
                    console.info('标签选择器查询结果', rst);
                    params.page = params.page || 1;
                    var tags = rst.list;
                    var results = [];
                    for (var i = 0; i < tags.length; i++)
                        results.push(select2Result(tags[i]));
                    return {
                        results: results,
                        pagination: {
                            more: results.length == 10
                        }
                    };
                },
            }
        });
        widget.on('change', function () {
            var results = widget.data('select2').data();
            widget.data('select2:data', results.length ? results[0].data : null);
        });
    });
};

//select2相关jquery扩展
jQuery.fn.select2Clear = function () {
    this.empty();
    this.val(null);
    this.data('select2:data', null);
    this.trigger('change');
    return this;
};
jQuery.fn.select2Deselect = function () {
    this.val(null);
    this.data('select2:data', null);
    this.trigger('change');
    return this;
};
jQuery.fn.select2Set = function (data) {
    this.empty();
    var result = this.data('select2Result')(data);
    var option = $('<option/>');
    option.attr('value', result.id);
    option.text(result.text);
    this.append(option);
    this.removeAttr("title");
    this.val(result.id);
    this.data('select2:data', data);
    var dataMap = {};
    dataMap[result.id] = data;
    this.data('select2:dataMap', dataMap);
    this.trigger('change');
    return this;
};

jQuery.fn.select2Add = function (id, text) {
    var data = {id: id, text: text};
    var newOption = new Option(data.text, data.id, false, true);
    this.append(newOption).trigger('change');
};