<%@ page contentType="text/javascript" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/functions.tld" prefix="imf" %>

jQuery(document).ready(function() {
  jQuery("p#contactUsLink").toggle();
});

(function() {
    if ((typeof intermine != 'undefined') && (intermine.Service != null)) {
        // Set up the service, if required.
        var root = window.location.protocol + "//" + window.location.host + "/${WEB_PROPERTIES['webapp.path']}";
        $SERVICE = new intermine.Service({
            "root": root,
            "token": "${PROFILE.dayToken}",
            "help": "${WEB_PROPERTIES['feedback.destination']}"
        });
        var notification = new FailureNotification({message: $SERVICE.root + " is incorrect"});
        $SERVICE.fetchVersion().fail(notification.render).done(function(v) {
            console.log("Webservice is at version " + v);
        });
        if (intermine.widgets != null) {
            // Make sure we have all deps required in `global.web.properties`, otherwise we fail!!!
            var opts = { 'root': $SERVICE.root, 'token': $SERVICE.token, 'skipDeps': true };
            window.widgets = new intermine.widgets($SERVICE.root, $SERVICE.token, opts);
        }
        var ua = jQuery.browser; // kinda evil, but best way to do this for now
        if (ua.msie && parseInt(ua.version, 10) < 9) {
            new Notification({message: '<fmt:message key="old.browser"/>'}).render();
        }
    }
})();

$MODEL_TRANSLATION_TABLE = {
    <c:forEach var="cd" items="${INTERMINE_API.model.classDescriptors}" varStatus="cdStat">
        "${cd.unqualifiedName}": {
            displayName: "${imf:formatPathStr(cd.unqualifiedName, INTERMINE_API, WEBCONFIG)}",
            fields: {
                <c:forEach var="fd" items="${cd.allFieldDescriptors}" varStatus="fdStat">
                    <c:set var="fdPath" value="${cd.unqualifiedName}.${fd.name}"/>
                    "${fd.name}": "${imf:formatFieldStr(fdPath, INTERMINE_API, WEBCONFIG)}"<c:if test="${!fdStat.last}">,</c:if>
                </c:forEach>
            }
        }<c:if test="${!cdStat.last}">,</c:if>
    </c:forEach>
};

<c:if test="${! empty WEB_PROPERTIES['constraint.default.value']}">
if (typeof intermine != 'undefined') {
    intermine.scope('intermine.conbuilder.messages', {
        "ValuePlaceholder": "${WEB_PROPERTIES['constraint.default.value']}",
        "ExtraPlaceholder": "${WEB_PROPERTIES['constraint.default.extra-value']}"
    }, true);
}
</c:if>
