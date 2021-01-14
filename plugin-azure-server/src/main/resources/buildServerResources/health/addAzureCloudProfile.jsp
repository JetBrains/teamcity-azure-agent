<%@ include file="/include-internal.jsp"%>
<%--
  ~ Copyright 2000-2021 JetBrains s.r.o.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

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
