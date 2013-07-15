<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="im"%>
<%@ taglib uri="http://jakarta.apache.org/taglibs/string-1.1" prefix="str" %>


<!-- userGroups.jsp -->
<html:xhtml/>

<im:body id="userGroups">

  <h1>
        <fmt:message key="groups.heading"/>
        <c:if test="${!PROFILE.loggedIn}">
        - <html:link action="/login?returnto=/mymine.do?subtab=lists">
            <fmt:message key="groups.login"/>
          </html:link>
          &nbsp;&nbsp;
        </c:if>
  </h1>

  <h2 id="no-groups"><fmt:message key="groups.none"/></h2>
  <table id="groups" class="bag-table sortable-onload-2 row-style-alt colstyle-alt no-arrow">
      <thead>
          <tr>
              <th></th>
              <th><fmt:message key="groups.name"/></th>
              <th><fmt:message key="groups.description"/></th>
              <th><fmt:message key="groups.details"/></th>
          </tr>
      </thead>
      <tbody></tbody>
  </table>

  <button id="add-group"><fmt:message key="groups.add"/></button>
  <div id="add-group-form" style="display:none">
      <form>
          <table>
              <tr>
                <td><fmt:message key="groups.name"/></td>
                <td><input type="text" class="group-name"/></td>
              </tr>
              <tr>
                <td><fmt:message key="groups.description"/></td>
                <td><input type="text" class="group-description"/></td>
              </tr>
          </table>
          <button class="confirm"><fmt:message key="confirm.ok"/></button>
          <button class="cancel"><fmt:message key="confirm.cancel"/></button>
     </form>
  </div>

  <script type="text/javascript" src="js/tablesort.js"></script>
  <link rel="stylesheet" type="text/css" href="css/sorting.css"/>
  <link rel="stylesheet" type="text/css" href="css/userGroups.css"/>
  <script type="text/javascript" src="js/userGroups.js"></script>

</im:body>

<!-- /userGroups.jsp -->


