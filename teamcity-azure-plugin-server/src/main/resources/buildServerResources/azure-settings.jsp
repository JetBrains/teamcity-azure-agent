<%--
  ~ Copyright 2000-2012 JetBrains s.r.o.
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

<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/include.jsp" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%--@elvariable id="resPath" type="java.lang.String"--%>

<jsp:useBean id="cons" class="jetbrains.buildServer.clouds.azure.web.AzureWebConstants"/>

  <tr>
    <th><label for="${cons.managementCertificate}">Management certificate: <l:star/></label></th>
    <td><forms:button id="${cons.managementCertificate}" onclick="return BS.UploadManagementCertificate.show();">Upload management certificate</forms:button>
      <bs:dialog dialogId="addManagementCertificate"
                 dialogClass="uploadDialog"
                 title="Upload Management Certificate"
                 titleId="addCertificateTitle"
                 closeCommand="BS.UploadManagementCertificate.close()">
        <c:url var="actionUrl" value="${resPath}uploadManagementCertificate.html"/>
        <forms:multipartForm id="uploadCertificateForm" action="${actionUrl}"
                             onsubmit="return BS.UploadManagementCertificate.validate();"
                             targetIframe="hidden-iframe">
          <div>
            <table>
              <tr>
                <th><label for="fileName">Name</label></th>
                <td><input type="text" id="fileName" name="fileName" value=""/></td>
              </tr>
              <tr>
                <th>File</th>
                <td>
                  <forms:file name="fileToUpload" size="28"/>
                </td>
              </tr>
            </table>
            <span id="uploadError" class="error" style="display: none; margin-left: 0;"></span>
          </div>
          <div class="popupSaveButtonsBlock">
            <forms:submit label="Save"/>
            <forms:cancel onclick="if (this.getContainer()) {BS.Hider.hideDiv(this.getContainer().id);}" showdiscardchangesmessage="false"/>
            <input type="hidden" id="projectId" name="project" value="${project.externalId}"/>
            <forms:saving id="saving"/>
          </div>
        </forms:multipartForm>
      </bs:dialog>
    </td>
  </tr>

  <tr>
    <th><label for="${cons.subscriptionId}">Subscription Id: <l:star/></label></th>
    <td><props:textProperty name="${cons.subscriptionId}" className="longField"/></td>
  </tr>

  <tr>
    <td colspan="2">
      <span id="error_fetch_options" class="error"></span>
      <div>
        <forms:button id="azureFetchOptionsButton">Fetch options</forms:button>
      </div>
    </td>
  </tr>
<tr>
  <td colspan="2">
    <h3 class="title_underlined">Images</h3>
    <div class="imagesTableWrapper hidden">
      <span class="emptyImagesListMessage hidden">You haven't added any images yet.</span>
      <table id="azureImagesTable" class="settings imagesTable hidden">
        <tbody>
        <tr>
          <th class="name">Service name</th>
          <th class="name">Deployment name</th>
          <th class="name">Image name</th>
          <th class="name">Name prefix</th>
          <th class="name">Generalized</th>
          <th class="name">Max # of instances</th>
          <th class="name" colspan="2"></th>
        </tr>
        </tbody>
      </table>
      <props:hiddenProperty name="${cons.imagesData}"/>
    </div>
  </td>
</tr>

<tr>
  <th><label for="${cons.serviceName}">Service name: <l:star/></label></th>
  <td><props:textProperty name="${cons.serviceName}" className="longField"/></td>
</tr>
<tr>
  <th><label for="${cons.deploymentName}">Deployment name: <l:star/></label></th>
  <td><props:textProperty name="${cons.deploymentName}" className="longField"/></td>
</tr>
<tr>
  <th><label for="${cons.imageName}">Image name: <l:star/></label></th>
  <td><props:textProperty name="${cons.imageName}" className="longField"/></td>
</tr>
<tr>
  <th><label for="${cons.namePrefix}">Name prefix: <l:star/></label></th>
  <td><props:textProperty name="${cons.namePrefix}" className="longField"/></td>
</tr>
<tr>
  <th><label for="${cons.vmSize}">Virtual machine size: <l:star/></label></th>
  <td><props:textProperty name="${cons.vmSize}" className="longField"/></td>
</tr>
<tr>
  <th><label for="${cons.osType}">Operating System: <l:star/></label></th>
  <td><props:textProperty name="${cons.osType}" className="longField"/></td>
</tr>
<tr>
  <th><label for="${cons.provisionUsername}">: <l:star/></label></th>
  <td><props:textProperty name="${cons.provisionUsername}" className="longField"/></td>
</tr>
<tr>
  <th><label for="secure:${cons.provisionPassword}">: <l:star/></label></th>
  <td><props:passwordProperty name="secure:${cons.provisionPassword}" className="longField"/></td>
</tr>
<tr>
  <td colspan="2">
    <forms:button id="addImageButton">Add image</forms:button>
  </td>
</tr>
<script type="text/javascript">
  $j.ajax({
            url: "<c:url value="${resPath}azure-settings.js"/>",
            dataType: "script",
            cache: true
          });
</script>

