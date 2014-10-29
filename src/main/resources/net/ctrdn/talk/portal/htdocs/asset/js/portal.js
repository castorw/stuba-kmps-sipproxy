function call_swswitch_api(call_name, callback) {
    $.post("/api/" + call_name, null, function(data) {
        if (data.Status !== true) {
            alert("ApiError: " + data.Error);
        } else {
            if (callback !== null) {
                callback(data);
            }
        }
    }, 'json');
}

function call_swswitch_api_params(call_name, post_data, callback) {
    $.post("/api/" + call_name, post_data, function(data) {
        if (data.Status !== true) {
            alert("ApiError: " + data.Error);
        } else {
            if (callback !== null) {
                callback(data);
            }
        }
    }, 'json');
}

function get_form_data(form_object) {
    var data = {};
    $("input[data-form-id]", form_object).each(function() {
        var element_id = $(this).attr("data-form-id");
        var element_type = $(this).attr("type");
        if (element_id.trim() === "") {
            return;
        }
        if (element_type === "checkbox") {
            data[element_id] = $(this).prop("checked") ? "true" : "false";
        } else {
            data[element_id] = $(this).val();
        }
    });
    return data;
}

function clear_form(form_object) {
    $("input[data-form-id]", form_object).each(function() {
        if ($(this).attr("type") === "checkbox") {
            bootstrap_switch_set($(this), false);
        } else {
            $(this).val("");
            $(this).prop("disabled", false);
        }
    });
    $(".form-error-placeholder", form_object).html("");
}

function bootstrap_switch_set(object, value) {
    object.prop("checked", value);
    if (value === true) {
        object.parent().removeClass("switch-off");
        object.parent().addClass("switch-on");
    } else {
        object.parent().removeClass("switch-on");
        object.parent().addClass("switch-off");
    }
}

function attach_form_alert(form_object, style_class, message_html) {
    $(".form-error-placeholder", form_object).hide();
    $(".form-error-placeholder", form_object).html(create_alert_html(style_class, message_html));
    $(".form-error-placeholder", form_object).fadeIn(500);
}

function create_alert_html(style_class, message_html) {
    var html = "<div class=\"alert " + style_class + " alert-white rounded\">";
    html += "<button type=\"button\" class=\"close\" data-dismiss=\"alert\" aria-hidden=\"true\">×</button>";
    html += "<div class=\"icon\"><i class=\"fa fa-times-circle\"></i></div>";
    html += "<span>" + message_html + "</span>";
    html += "</div>";
    return html;
}

function create_boolean_label_html(value, true_text, false_text, true_color, false_color) {
    if (true_color === null || true_color === undefined) {
        true_color = "success";
    }
    if (false_color === null || false_color === undefined) {
        false_color = "danger";
    }
    return value ? "<span class=\"label label-" + true_color + "\">" + true_text + "</span>" : "<span class=\"label label-" + false_color + "\">" + false_text + "</span>";
}

function create_moment_html(timestamp) {
    if (timestamp > 0) {
        return "<div data-toggle=\"tooltip\" data-placement=\"bottom\" title=\"" + moment(timestamp / 1000, "X").format("YYYY-MM-DD hh:mm:ss") + "\">" + moment(timestamp / 1000, "X").fromNow() + "</div>";
    }
    return "-";
}

function modal_confirm(header, text, color, callback) {
    $("#confirm-md").removeClass("primary");
    $("#confirm-md").removeClass("success");
    $("#confirm-md").removeClass("info");
    $("#confirm-md").removeClass("warning");
    $("#confirm-md").removeClass("danger");
    $("#confirm-md").addClass(color);

    $("#confirm-md .btn").removeClass("btn-primary");
    $("#confirm-md .btn").removeClass("btn-success");
    $("#confirm-md .btn").removeClass("btn-info");
    $("#confirm-md .btn").removeClass("btn-warning");
    $("#confirm-md .btn").removeClass("btn-danger");
    $("#confirm-md .btn").addClass("btn-" + color);

    $("#confirm-md .confirm-md-header").html(header);
    $("#confirm-md .confirm-md-content").html(text);
    $("#confirm-md-proceed-btn").unbind("click");
    $("#confirm-md-proceed-btn").click(function() {
        $("#confirm-md").removeClass("md-show");
        callback();
    });
    $("#confirm-md").modalEffectsDirect();
}

function dump_object(obj) {
    var out = '';
    for (var i in obj) {
        out += i + ": " + obj[i] + "\n";
    }

    alert(out);
}

function format_octet_size(bytes, precision, bps)
{
    var kilobyte = 1024;
    var megabyte = kilobyte * 1024;
    var gigabyte = megabyte * 1024;
    var terabyte = gigabyte * 1024

    if ((bytes >= 0) && (bytes < kilobyte)) {
        return bytes + ((bps === true) ? " b/s" : " B");
    } else if ((bytes >= kilobyte) && (bytes < megabyte)) {
        return (bytes / kilobyte).toFixed(precision) + ((bps === true) ? " Kb/s" : " KB");
    } else if ((bytes >= megabyte) && (bytes < gigabyte)) {
        return (bytes / megabyte).toFixed(precision) + ((bps === true) ? " Mb/s" : " MB");
    } else if ((bytes >= gigabyte) && (bytes < terabyte)) {
        return (bytes / gigabyte).toFixed(precision) + ((bps === true) ? " Gb/s" : " GB");
    } else if (bytes >= terabyte) {
        return (bytes / terabyte).toFixed(precision) + ((bps === true) ? " Tb/s" : " TB");
    } else {
        return bytes + ' B';
    }
}

function format_date(timestamp, fmt) {
    var date = new Date(timestamp);
    function pad(value) {
        return (value.toString().length < 2) ? '0' + value : value;
    }
    return fmt.replace(/%([a-zA-Z])/g, function(_, fmtCode) {
        switch (fmtCode) {
            case 'Y':
                return date.getUTCFullYear();
            case 'M':
                return pad(date.getUTCMonth() + 1);
            case 'd':
                return pad(date.getUTCDate());
            case 'H':
                return pad(date.getUTCHours());
            case 'm':
                return pad(date.getUTCMinutes());
            case 's':
                return pad(date.getUTCSeconds());
            default:
                throw new Error('Unsupported format code: ' + fmtCode);
        }
    });
}

$(document).ready(function() {
    $("a[data-call-api]").click(function() {
        var call_name = $(this).attr("data-call-api");
        if ($(this).attr("data-call-confirm") !== undefined) {
            var text = $(this).attr("data-call-confirm");
            if (!confirm(text)) {
                return;
            }
        }
        call_swswitch_api(call_name, null);
    });

    $("[data-toggle='tooltip']").tooltip();
    $("a[href='#logout']").click(function() {
        modal_confirm("Logout", "Are you sure you want to logout?", "primary", function() {
            call_swswitch_api("system.user.logout", function(data) {
                if (data.Response.Successful === true) {
                    location.href = data.Response.TargetUri;
                } else {
                    alert("Something crappy happened. The logout api method responded with unknown failure. Please try again later... or whatever...");
                }
            });
        });
    });
});