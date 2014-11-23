var webrtcConstraints = {
    video: {
        mandatory: {
            minWidth: 1280,
            minHeight: 720
        }
    },
    audio: true
};
var present_user_cache = null;
var current_session = null;
var local_ice_candidate = null;
var local_media_stream = null;
var peer_connection = null;
$(document).ready(function() {
    initialize_local_media();
    acknowledge_presence();
    initialize_user_media_position();
    initialize_gui();
});
function acknowledge_presence() {
    var presence_params = {};
    if (current_session !== null) {
        presence_params["ack-session-uuid"] = current_session.SessionInfo.SessionUuid;
    }

    call_talk_api_params("webrtc.present", presence_params, function(data) {
        if (data.Response.Successful === true) {
            setTimeout("acknowledge_presence();", data.Response.Timeout / 2);
            present_user_cache = data.Response.UserList;
            if (data.Response.SessionInfo !== undefined && data.Response.SessionInfo !== null) {
                process_session_info(data.Response);
            } else {
                current_session = null;
                gui_render_user_list();
            }
        } else {
            alert("Presence error");
        }
    });
}

function initialize_gui() {
    $(window).resize(function() {
        var wHeight = $(window).height();
        $("#gui-control-bar").offset({top: 0, left: 0});
        $("#gui-control-bar").css("height", wHeight + "px");
    });
    $(window).resize();
    $(document).on("click", "a[data-call-object-id]", function() {
        var userObjectId = $(this).attr("data-call-object-id");
        if (current_session === null) {
            webrtc_invite(userObjectId);
        } else if (current_session.SessionInfo.CallerObjectId === userObjectId) {
            webrtc_answer();
        } else {
            alert("You cannot open a new session until there is an ongoing session.");
        }
    });
}

function gui_render_user_list() {
    var userListHtml = "";
    for (var i in present_user_cache) {
        var userObj = present_user_cache[i];
        if (userObj.IsSelf === true) {
            $(".gui-cb-username").html(userObj.DisplayName);
        } else {
            if (current_session !== null && current_session.SessionInfo !== undefined && ((current_session.SessionInfo.Direction === "OUTGOING" && current_session.SessionInfo.CalleeObjectId === userObj.ObjectId) || (current_session.SessionInfo.Direction === "INCOMING" && current_session.SessionInfo.CallerObjectId === userObj.ObjectId))) {
                userListHtml += "<a href=\"#\" class=\"list-group-item active session-" + current_session.StatusColor + "\" data-call-object-id=\"" + userObj.ObjectId + "\">";
                userListHtml += "<h5 class=\"list-group-item-heading\"><span class=\"fa fa-phone\"></span> " + userObj.DisplayName + "</h5>";
                userListHtml += "<div><i class=\"fa fa-" + current_session.StatusIcon + "\"></i> " + current_session.StatusText + "</div>";
                userListHtml += "</a>";
            } else {
                userListHtml += "<a href=\"#\" class=\"list-group-item\" data-call-object-id=\"" + userObj.ObjectId + "\">";
                userListHtml += "<h5 class=\"list-group-item-heading\"><span class=\"fa fa-phone\"></span> " + userObj.DisplayName + "</h5>";
                userListHtml += "<div><i class=\"fa fa-check\"></i> Available</div>";
                userListHtml += "</a>";
            }
        }
    }
    if (userListHtml !== "") {
        $("#gui-nua-message").hide();
        $("#gui-user-list").html(userListHtml);
        $("#gui-user-list").show();
    } else {
        $("#gui-nua-message").show();
        $("#gui-user-list").hide();
    }
}

function position_user_media() {
    var wWidth = $(window).width();
    var wHeight = $(window).height();
    $("#small-video").offset({top: wHeight - $("#small-video").height() - 25, left: wWidth - $("#small-video").width() - 25});
}

function initialize_user_media_position() {
    $(window).resize(function() {
        position_user_media();
    });
    $("#small-video").resize(function() {
        position_user_media();
    });
    position_user_media();
}

function initialize_local_media() {
    if (Modernizr.getusermedia) {
        var getUserMedia = Modernizr.prefixed('getUserMedia', navigator);
        var errorCallback = function(e) {
            alert("Failed to acquire user media: " + e);
            location.href = "/";
        };
        getUserMedia(webrtcConstraints, function(stream) {
            local_media_stream = stream;
            var localVideoObject = document.querySelector('#small-video');
            localVideoObject.src = window.URL.createObjectURL(local_media_stream);
        }, errorCallback);
    } else {
        alert("Your browser does not support WebRTC.");
        location.href = "/";
    }
}

function webrtc_error(err) {
    alert("WebRTC error: " + err);
}

function webrtc_invite(user_obejct_id) {
    webrtc_create_peer_connection();
    peer_connection.createOffer(function(description) {
        peer_connection.setLocalDescription(description);
        var invite_request_data = {
            "offer": btoa(JSON.stringify(description)),
            "callee-object-id": user_obejct_id
        };
        call_talk_api_params("webrtc.offer", invite_request_data, function(data) {
            if (data.UserError !== undefined) {
                alert(data.UserError);
            } else {
                process_session_info(data.Response);
            }
        });
    }, webrtc_error);
}

function webrtc_answer() {
    var offer_text = atob(current_session.OfferBase64);
    var offer_object = jQuery.parseJSON(offer_text);
    var offer_description = webrtc_create_rtc_session_description(offer_object);
    webrtc_create_peer_connection();
    peer_connection.setRemoteDescription(offer_description);
    peer_connection.createAnswer(function(description) {
        peer_connection.setLocalDescription(description);
        var answer_request_data = {
            "session-uuid": current_session.SessionInfo.SessionUuid,
            "answer": btoa(JSON.stringify(description))
        };
        call_talk_api_params("webrtc.answer", answer_request_data, function(data) {
            if (data.UserError !== undefined) {
                alert(data.UserError);
            } else {
                process_session_info(data.Response);
            }
        });
    }, webrtc_error);
}

function webrtc_established() {
    call_talk_api_params("webrtc.established", {"session-uuid": current_session.SessionInfo.SessionUuid}, function(data) {
        if (data.UserError !== undefined) {
            alert(data.UserError);
        } else {
            process_session_info(data.Response);
        }
    });
}

function webrtc_process_remote_answer() {
    var answer_text = atob(current_session.AnswerBase64);
    var answer_object = jQuery.parseJSON(answer_text);
    var answer_description = webrtc_create_rtc_session_description(answer_object);
    peer_connection.setRemoteDescription(answer_description);
    var candidate_request_data = {
        "session-uuid": current_session.SessionInfo.SessionUuid,
        "candidate": btoa(JSON.stringify(local_ice_candidate))
    };
    call_talk_api_params("webrtc.candidate", candidate_request_data, function(data) {
        if (data.UserError !== undefined) {
            alert(data.UserError);
        } else {
            process_session_info(data.Response);
        }
    });
}

function webrtc_process_remote_candidate() {
    var candidate_list_text = atob(current_session.CandidateBase64);
    var candidate_array = jQuery.parseJSON(candidate_list_text);
    for (var i in candidate_array) {
        peer_connection.addIceCandidate(webrtc_create_ice_candidate(candidate_array[i]));
    }
    webrtc_established();
    if (current_session.SessionInfo.Direction === "INCOMING") {
        var candidate_request_data = {
            "session-uuid": current_session.SessionInfo.SessionUuid,
            "candidate": btoa(JSON.stringify(local_ice_candidate))
        };
        call_talk_api_params("webrtc.candidate", candidate_request_data, function(data) {
            if (data.UserError !== undefined) {
                alert(data.UserError);
            } else {
                process_session_info(data.Response);
            }
        });
    }
}

function webrtc_create_rtc_session_description(init) {
    return (typeof RTCSessionDescription === "undefined") ? ((typeof webkitRTCSessionDescription === "undefined") ? new mozRTCSessionDescription(init) : new webkitRTCSessionDescription(init)) : new RTCSessionDescription(init);
}

function webrtc_create_ice_candidate(init) {
    return (typeof RTCIceCandidate === "undefined") ? ((typeof webkitRTCIceCandidate === "undefined") ? new mozRTCIceCandidate(init) : new webkitRTCIceCandidate(init)) : new RTCIceCandidate(init);
}

function webrtc_create_peer_connection() {
    local_ice_candidate = null;
    peer_connection = (typeof webkitRTCPeerConnection === "undefined") ? new mozRTCPeerConnection(null) : new webkitRTCPeerConnection(null);
    peer_connection.addStream(local_media_stream);
    peer_connection.onicecandidate = function(event) {
        if (local_ice_candidate === null) {
            local_ice_candidate = [];
        }
        if (event.candidate !== null) {
            local_ice_candidate.push(event.candidate);
        }
    };
    peer_connection.onaddstream = function(event) {
        $("#large-video").attr("src", URL.createObjectURL(event.stream));
        $("#gui-camera-icon").fadeOut(500);
        setTimeout(function() {
            $("#large-video").fadeIn(500);
        }, 500);
    };
}

function process_session_info(response) {
    if (current_session === null) {
        current_session = {};
    } else if (response.SessionInfo === undefined) {
        current_session = null;
        return;
    }
    var session_info = response.SessionInfo;
    if (session_info.OfferBase64 !== undefined) {
        current_session.OfferBase64 = session_info.OfferBase64;
    }
    if (session_info.AnswerBase64 !== undefined) {
        current_session.AnswerBase64 = session_info.AnswerBase64;
        webrtc_process_remote_answer();
    }
    if (session_info.CandidateBase64 !== undefined) {
        current_session.CandidateBase64 = session_info.CandidateBase64;
        webrtc_process_remote_candidate();
    }
    current_session.SessionInfo = session_info;
    if (current_session.SessionInfo.Direction === "OUTGOING") {
        switch (current_session.SessionInfo.State) {
            case "OFFERED":
                current_session.StatusText = "Calling...";
                current_session.StatusIcon = "comments";
                current_session.StatusColor = "info";
                break;
            case "OFFER_DELIVERED":
                current_session.StatusText = "Ringing...";
                current_session.StatusIcon = "bell";
                current_session.StatusColor = "info";
                break;
            case "ESTABLISHED":
                current_session.StatusText = "Established";
                current_session.StatusIcon = "check";
                current_session.StatusColor = "success";
                break;
            default:
                current_session.StatusText = "Connecting...";
                current_session.StatusIcon = "cloud";
                current_session.StatusColor = "primary";
                break;
        }
    } else {
        switch (current_session.SessionInfo.State) {
            case "OFFERED":
            case "OFFER_DELIVERED":
                current_session.StatusText = "Incoming call...";
                current_session.StatusIcon = "bell";
                current_session.StatusColor = "danger";
                break;
            case "ESTABLISHED":
                current_session.StatusText = "Established";
                current_session.StatusIcon = "check";
                current_session.StatusColor = "success";
                break;
            default:
                current_session.StatusText = "Connecting...";
                current_session.StatusIcon = "cloud";
                current_session.StatusColor = "primary";
                break;
        }
    }
    gui_render_user_list();
}