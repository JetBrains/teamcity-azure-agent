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
<jsp:useBean id="refreshablePath" class="java.lang.String" scope="request"/>

<bs:linkCSS>${resPath}azure-settings.css</bs:linkCSS>

<c:set var="azureLink"><a href="#" target="_blank">**put related link here**</a></c:set>
  <tr>
    <th><label for="${cons.managementCertificate}">Management certificate: <l:star/></label></th>
    <td>
        <props:textProperty name="${cons.managementCertificate}" className="longField"/>
        <span class="smallNote">Your Azure account management certificate. You may find the info at ${azureLink}</span>
    </td>
  </tr>

  <tr>
    <th><label for="${cons.subscriptionId}">Subscription ID: <l:star/></label></th>
    <td>
        <props:textProperty name="${cons.subscriptionId}" className="longField"/>
        <span class="smallNote">Your Azure account subscription ID. You may find the info at ${azureLink}</span>
    </td>
  </tr>

  <tr>
    <td colspan="2">
      <span id="error_fetch_options" class="error"></span>
      <%--<div>--%>
        <%--<forms:button id="azureFetchOptionsButton">Fetch options</forms:button>--%>
      <%--</div>--%>
      <div class="options-loader invisible"><i class="icon-refresh icon-spin"></i>&nbsp;Fetching options...</div>
    </td>
  </tr>
<tr>
    <th>
        <span id="label_${cons.imageName}">Source:</span> <l:star/>
    </th>
    <td>
        <div>
            <select class="inline-block longField" name="_${cons.imageName}" id="${cons.imageName}" data-err-id="${cons.imageName}"></select>
      <span id="${cons.osType}" class="provision hidden">
        <bs:osIcon osName="windows" small="true"/>
        <bs:osIcon osName="linux" small="true"/>
      </span>
        </div>
        <span class="smallNote">Image or Instance</span>
        <span class="error option-error option-error_${cons.imageName}"></span>
    </td>
</tr>
<tr>
  <th>Behaviour: <l:star/></th>
  <td>
    <input type="radio" id="cloneBehaviour_FRESH_CLONE" name="cloneBehaviour" value="FRESH_CLONE" checked="true" class="cloneBehaviourRadio"/>
    <label for="cloneBehaviour_FRESH_CLONE">Fresh clone</label>
    <br/>
    <input type="radio" id="cloneBehaviour_START_STOP" name="cloneBehaviour" value="START_STOP" class="cloneBehaviourRadio"/>
    <label for="cloneBehaviour_START_STOP">Start/Stop instance</label>
    <br/>
<%--
    <input type="radio" id="cloneBehaviour_ON_DEMAND_CLONE" name="cloneBehaviour" value="ON_DEMAND_CLONE" class="cloneBehaviourRadio"/>
    <label for="cloneBehaviour_ON_DEMAND_CLONE">On demand clone</label>
    <br/>
--%>
  </td>
</tr>

<tr>
    <td colspan="2">
    <table class="innerTable">
        <tr>
          <th>Service: <l:star/></th>
          <td>
            <div>
              <select name="_${cons.serviceName}" id="${cons.serviceName}" data-err-id="${cons.serviceName}" class="longField"></select>
            </div>
            <span class="error option-error option-error_${cons.serviceName}"></span>
          </td>
        </tr>
        <tr>
          <th>Deployment: <l:star/></th>
          <td>
            <div>
              <select name="_${cons.deploymentName}" id="${cons.deploymentName}" data-err-id="${cons.deploymentName}" class="longField"></select>
            </div>
            <span class="error option-error option-error_${cons.deploymentName}"></span>
          </td>
        </tr>
        <tr class="clone hidden">
            <th>Max # of instances: <l:star/></th>
            <td>
                <div>
                    <input type="text" name="_${cons.maxInstancesCount}" id="${cons.maxInstancesCount}" data-err-id="${cons.maxInstancesCount}" class="longField"/>
                </div>
                <span class="error option-error option-error_${cons.maxInstancesCount}"></span>
            </td>
        </tr>
        <tr class="clone hidden">
          <th>Name prefix: <l:star/></th>
          <td><props:textProperty name="${cons.namePrefix}" className="longField"/></td>
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
          <td><input type="text" id="${cons.provisionUsername}" className="longField"/></td>
        </tr>
        <tr class="provision hidden">
          <th>Provision password:  <l:star/></th>
          <td><input type="password" id="${cons.provisionPassword}" className="longField"/></td>
        </tr>
        <tr>
          <td colspan="2">
            <forms:addButton title="Add Image" id="addImageButton">Add image</forms:addButton>
          </td>
        </tr>
        <tr>
          <td colspan="2">
            <props:multilineProperty name="images_data" linkTitle="Images" cols="50" rows="5"/>
          </td>
        </tr>
    </table>
    </td>
</tr>
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

