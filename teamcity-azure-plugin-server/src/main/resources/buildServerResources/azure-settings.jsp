<%--
  ~ /*
  ~  * Copyright 2000-2014 JetBrains s.r.o.
  ~  *
  ~  * Licensed under the Apache License, Version 2.0 (the "License");
  ~  * you may not use this file except in compliance with the License.
  ~  * You may obtain a copy of the License at
  ~  *
  ~  * http://www.apache.org/licenses/LICENSE-2.0
  ~  *
  ~  * Unless required by applicable law or agreed to in writing, software
  ~  * distributed under the License is distributed on an "AS IS" BASIS,
  ~  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~  * See the License for the specific language governing permissions and
  ~  * limitations under the License.
  ~  */
  --%>

<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/include.jsp" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%--@elvariable id="resPath" type="java.lang.String"--%>
</table>
<jsp:useBean id="cons" class="jetbrains.buildServer.clouds.azure.web.AzureWebConstants"/>
<jsp:useBean id="refreshablePath" class="java.lang.String" scope="request"/>

<h2 class="noBorder section-header">Cloud Access Information</h2>
<c:set var="azureLink"><a href="https://manage.windowsazure.com/publishsettings" target="_blank">file</a></c:set>

<script type="text/javascript">
    BS.LoadStyleSheetDynamically("<c:url value='${resPath}azure-settings.css'/>");
</script>

<table class="runnerFormTable">
  <tr>
    <th><label for="secure:${cons.managementCertificate}">Management certificate: <l:star/></label></th>
    <td>
        <props:textProperty name="secure:${cons.managementCertificate}" className="longField"/>
        <a href="#" class="toggle-certificate">edit</a>
        <span class="smallNote">Your Azure account management certificate</span>
    </td>
  </tr>

  <tr>
    <th><label for="${cons.subscriptionId}">Subscription ID: <l:star/></label></th>
    <td>
        <props:textProperty name="${cons.subscriptionId}" className="longField"/>
        <span class="smallNote">Your Azure account subscription ID</span>
        <span class="grayNote">Download your subscription ${azureLink} to obtain management certificate and subscription ID</span>
    </td>
  </tr>

  <tr>
    <td colspan="2" class="loader-wrapper">
      <span id="error_fetch_options" class="error"></span>
      <div class="options-loader invisible"><i class="icon-refresh icon-spin"></i>&nbsp;Fetching options...</div>
    </td>
  </tr>
</table>
<bs:dialog dialogId="AzureImageDialog" title="Edit Image" closeCommand="BS.AzureImageDialog.close()"
           dialogClass="AzureImageDialog" titleId="AzureDialogTitle"
        ><!-- These fake fields are a workaround to disable chrome autofill -->
    <input style="display:none" type="text" name="fakeusernameremembered"/>
    <input style="display:none" type="password" name="fakepasswordremembered"/>
    <table class="runnerFormTable">
    <tr>
        <th>
            <span id="label_${cons.sourceName}">Source:</span> <l:star/>
        </th>
        <td>
            <div>
                <select class="inline-block longField" name="_${cons.sourceName}" id="${cons.sourceName}" data-err-id="${cons.sourceName}"></select>
                <span id="${cons.osType}">
                    <bs:osIcon osName="windows" small="true"/>
                    <bs:osIcon osName="linux" small="true"/>
                </span>
            </div>
            <span class="smallNote">Image or Machine. The greyed out options are already added</span>
            <span class="error option-error option-error_${cons.sourceName}"></span>
        </td>
    </tr>
    <tr class="hidden">
      <th>Behaviour: <l:star/></th>
      <td>
        <input type="radio" id="cloneBehaviour_FRESH_CLONE" name="behaviour" value="FRESH_CLONE" checked="true" class="behaviourRadio"/>
        <label for="cloneBehaviour_FRESH_CLONE">Fresh clone</label>
        <br/>
        <input type="radio" id="cloneBehaviour_START_STOP" name="behaviour" value="START_STOP" class="behaviourRadio"/>
        <label for="cloneBehaviour_START_STOP">Start/Stop machine</label>
        <br/>
    <%--
        <input type="radio" id="cloneBehaviour_ON_DEMAND_CLONE" name="behaviour" value="ON_DEMAND_CLONE" class="behaviourRadio"/>
        <label for="cloneBehaviour_ON_DEMAND_CLONE">On demand clone</label>
        <br/>
    --%>
      </td>
    </tr>
    </table>
    <table class="runnerFormTable innerTable">
        <tr class="clone hidden">
          <th>Service: <l:star/></th>
          <td>
            <div>
              <select name="_${cons.serviceName}" id="${cons.serviceName}" data-err-id="${cons.serviceName}" class="longField"></select>
            </div>
            <span class="error option-error option-error_${cons.serviceName}"></span>
          </td>
        </tr>
        <tr class="clone hidden">
            <th>Max # of instances: <l:star/></th>
            <td>
                <div>
                    <input value="1" type="text" name="_${cons.maxInstancesCount}" id="${cons.maxInstancesCount}" data-err-id="${cons.maxInstancesCount}" class="longField"/>
                </div>
                <span class="error option-error option-error_${cons.maxInstancesCount}"></span>
            </td>
        </tr>
        <tr class="clone hidden">
          <th>Name prefix: <l:star/></th>
          <td>
              <props:textProperty name="${cons.namePrefix}" className="longField"/>
              <span class="smallNote">Max length is 6</span>
              <span class="error option-error option-error_${cons.namePrefix}"></span>
          </td>
        </tr>
        <tr class="clone hidden">
          <th>VM Size: <l:star/></th>
          <td>
            <div>
              <select name="_${cons.vmSize}" id="${cons.vmSize}" data-err-id="${cons.vmSize}" class="longField"></select>
            </div>
            <span class="error option-error option-error_${cons.vmSize}"></span>
          </td>
        </tr>
        <tr class="provision hidden">
          <th></th><td></td>
        </tr>
        <tr class="provision hidden">
          <th>Provision username: <l:star/></th>
          <td>
              <input type="text" id="${cons.provisionUsername}" class="longField"/>
              <span class="error option-error option-error_${cons.provisionUsername}"></span>
          </td>
        </tr>
        <tr class="provision hidden">
          <th>Provision password:  <l:star/></th>
          <td>
              <input type="password" id="${cons.provisionPassword}" class="longField"/>
              <span class="error option-error option-error_${cons.provisionPassword}"></span>
          </td>
        </tr>
    </table>
    <div class="popupSaveButtonsBlock">
        <forms:submit label="Add" id="addImageButton"/>
        <forms:button title="Cancel" id="azureCancelDialogButton">Cancel</forms:button>
    </div>
</bs:dialog>

<h2 class="noBorder section-header">Agent Images</h2>
<div class="imagesOuterWrapper">
    <div class="imagesTableWrapper hidden">
        <span class="emptyImagesListMessage hidden">You haven't added any images yet.</span>
        <table id="azureImagesTable" class="settings imagesTable hidden">
            <tbody>
            <tr>
                <th class="name">Image or Machine</th>
                <th class="name">Service</th>
                <th class="name hidden">Name prefix</th>
                <th class="name hidden">Start behaviour</th>
                <th class="name maxInstances">Max # of instances</th>
                <th class="name" colspan="2"></th>
            </tr>
            </tbody>
        </table>
        <%--<props:hiddenProperty name="images_data"/>--%>
        <jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
        <c:set var="imagesData" value="${propertiesBean.properties['images_data']}"/>
        <input type="hidden" name="prop:images_data" id="images_data" value="<c:out value="${imagesData}"/>"/>
        <c:set var="passwordsValue" value="${propertiesBean.properties['secure:passwords_data']}"/>
        <input type="hidden" name="prop:secure:passwords_data" id="passwords_data" value="<c:out value="${passwordsValue}"/>"/>
    </div>
    <forms:addButton title="Add image" id="azureShowDialogButton">Add image</forms:addButton>
</div>

<script type="text/javascript">
  $j.ajax({
      url: "<c:url value="${resPath}azure-settings.js"/>",
    dataType: "script",
    success: function() {
      BS.Clouds.Azure.init('<c:url value="${refreshablePath}"/>');
    },
    cache: true
  });
</script>
<table class="runnerFormTable">