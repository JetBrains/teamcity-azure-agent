<%@ include file="/include-internal.jsp"%>
<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>

<div class="suggestionItem">
    TeamCity server is running in Azure cloud. You could add Azure cloud profile to start build agents on demand.
    <div class="suggestionAction">
        <c:set var="projectId" value="${healthStatusItem.additionalData['projectId']}"/>
        <c:set var="type" value="${healthStatusItem.additionalData['type']}"/>
        <c:url var="url" value="/admin/editProject.html?init=1&projectId=${projectId}&tab=clouds&action=new&showEditor=true&cloudType=${type}"/>
        <forms:addLink href="${url}">Add Azure cloud profile</forms:addLink>
    </div>
</div>
