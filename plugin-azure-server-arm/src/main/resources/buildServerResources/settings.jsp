<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/include.jsp" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
</table>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="cons" class="jetbrains.buildServer.clouds.azure.arm.AzureConstants"/>
<jsp:useBean id="basePath" class="java.lang.String" scope="request"/>

<h2 class="noBorder section-header">Credentials</h2>

<script type="text/javascript">
    BS.LoadStyleSheetDynamically("<c:url value='${resPath}settings.css'/>");
</script>

<div id="arm-setting" data-bind="validationOptions: {insertMessages: false}">

    <table class="runnerFormTable" data-bind="with: credentials()">
        <tr>
            <th><label for="${cons.tenantId}">Tenant ID: <l:star/></label></th>
            <td>
                <input type="text" name="prop:${cons.tenantId}" class="longField"
                       value="${propertiesBean.properties[cons.tenantId]}"
                       data-bind="initializeValue: tenantId, textInput: tenantId"/>
                <span class="smallNote">Azure Active Directory domain or tenant ID</span>
                <span class="error option-error" data-bind="validationMessage: tenantId"></span>
            </td>
        </tr>

        <tr>
            <th><label for="prop:${cons.clientId}">Client ID: <l:star/></label></th>
            <td>
                <input type="text" name="prop:${cons.clientId}" class="longField"
                       value="${propertiesBean.properties[cons.clientId]}"
                       data-bind="initializeValue: clientId, textInput: clientId"/>
                <span class="smallNote">Azure Active Directory application ID</span>
                <span class="error option-error" data-bind="validationMessage: clientId"></span>
            </td>
        </tr>

        <tr>
            <th><label for="${cons.clientSecret}">Client Secret: <l:star/></label></th>
            <td>
                <input type="password" name="${cons.clientSecret}" class="longField"
                       data-bind="textInput: clientSecret"/>
                <input type="hidden" name="prop:secure:${cons.clientSecret}"
                       value="${propertiesBean.properties[cons.clientSecret]}"
                       data-bind="initializeValue: clientSecret, value: clientSecret"/>
                <span class="smallNote">Azure Active Directory application secret</span>
                <span class="error option-error" data-bind="validationMessage: clientSecret"></span>
            </td>
        </tr>

        <tr>
            <th><label for="${cons.subscriptionId}">Subscription ID: <l:star/></label></th>
            <td>
                <input type="text" name="prop:${cons.subscriptionId}" class="longField"
                       value="${propertiesBean.properties[cons.subscriptionId]}"
                       data-bind="initializeValue: subscriptionId, textInput: subscriptionId"/>
                <span class="smallNote">Azure subscription ID</span>
                <span class="error option-error" data-bind="validationMessage: subscriptionId"></span>
            </td>
        </tr>
        <tr>
            <td colspan="2" data-bind="with: $parent">
                <span data-bind="css: {hidden: !loadingGroups()}"><i class="icon-refresh icon-spin"></i> Validating credentials...</span>
                <span class="error option-error"
                      data-bind="text: errorLoadingGroups, css: {hidden: loadingGroups}"></span>
            </td>
        </tr>
    </table>

    <bs:dialog dialogId="ArmImageDialog" title="Add Image" closeCommand="BS.ArmImageDialog.close()"
               dialogClass="AzureImageDialog" titleId="ArmImageDialogTitle">
        <table class="runnerFormTable">
            <tr>
                <th><label for="${cons.groupId}">Group: <l:star/></label></th>
                <td>
                    <select name="${cons.groupId}" class="longField"
                            data-bind="options: groups, value: image().groupId"></select>
                    <a href="#" title="Reload resources"
                       data-bind="click: reloadResources.bind($data, image().groupId()), css: {invisible: loadingResources()}">
                        <i class="icon-refresh"></i>
                    </a>
                    <span class="error option-error" data-bind="text: errorLoadingResources"></span>
                </td>
            </tr>
            <tr>
                <th><label for="${cons.storageId}">Storage account: <l:star/></label></th>
                <td>
                    <select name="${cons.storageId}" class="longField"
                            data-bind="options: storages, value: image().storageId, css: {hidden: storages().length == 0}"></select>
                    <div class="longField inline-block" data-bind="css: {hidden: storages().length > 0}">
                        <span class="error option-error">No storages found in the resource group</span>
                    </div>
                    <i class="icon-refresh icon-spin" data-bind="css: {invisible: !loadingResources()}"></i>
                </td>
            </tr>
            <tr>
                <th><label for="${cons.imagePath}">Source image: <l:star/></label></th>
                <td>
                    <input type="text" name="${cons.imagePath}" class="longField"
                           data-bind="textInput: image().imagePath"/>
                    <img src="/img/tree/popup-artifacts-tree.png" class="handle vcsTreeHandle" title="Choose VHD image">
                    <span class="smallNote">Generalized VHD image URL, e.g. vhds/generalized.vhd</span>
                    <span class="error option-error" data-bind="validationMessage: image().imagePath"></span>
                </td>
            </tr>
            <tr>
                <th><label for="${cons.osType}">OS type: <l:star/></label></th>
                <td>
                    <select name="${cons.osType}" class="longField"
                            data-bind="options: osTypes, value: image().osType"></select>
                    <i class="icon-refresh icon-spin" data-bind="css: {invisible: !loadingOsType()}"></i>
                </td>
            </tr>
            <tr>
                <th><label for="${cons.maxInstancesCount}">Max # of instances: <l:star/></label></th>
                <td>
                    <div>
                        <input type="text" name="${cons.maxInstancesCount}" class="longField"
                               data-bind="textInput: image().maxInstances"/>
                        <span class="error option-error" data-bind="validationMessage: image().maxInstances"></span>
                    </div>
                </td>
            </tr>
            <tr>
                <th><label for="${cons.vmNamePrefix}">Name prefix: <l:star/></label></th>
                <td>
                    <input type="text" name="${cons.vmNamePrefix}" class="longField"
                           data-bind="textInput: image().vmNamePrefix"/>
                    <span class="error option-error" data-bind="validationMessage: image().vmNamePrefix"></span>
                </td>
            </tr>
            <tr>
                <th><label for="${cons.vmSize}">VM Size: <l:star/></label></th>
                <td>
                    <select name="${cons.vmSize}" class="longField"
                            data-bind="options: vmSizes, value: image().vmSize"></select>
                    <i class="icon-refresh icon-spin" data-bind="css: {invisible: !loadingResources()}"></i>
                </td>
            </tr>
            <tr>
                <th>Network:</th>
                <td>
                    <input type="checkbox" name="${cons.vmPublicIp}" class="behaviourRadio"
                           data-bind="checked: image().vmPublicIp"/>
                    <label for="${cons.vmPublicIp}">Create public IP address</label>
                </td>
            </tr>
            <tr>
                <th></th>
                <td></td>
            </tr>
            <tr>
                <th><label for="${cons.vmUsername}">Provision username: <l:star/></label></th>
                <td>
                    <input type="text" id="${cons.vmUsername}" class="longField"
                           data-bind="textInput: image().vmUsername"/>
                    <span class="error option-error" data-bind="validationMessage: image().vmUsername"></span>
                </td>
            </tr>
            <tr>
                <th><label for="${cons.vmPassword}">Provision password: <l:star/></label></th>
                <td>
                    <input type="password" id="${cons.vmPassword}" class="longField"
                           data-bind="textInput: image().vmPassword"/>
                    <span class="error option-error" data-bind="validationMessage: image().vmPassword"></span>
                </td>
            </tr>
        </table>

        <div class="popupSaveButtonsBlock">
            <input type="submit" value="Save" class="btn btn_primary submitButton"
                   data-bind="click: saveImage, enable: image.isValid"/>
            <a class="btn" href="#" data-bind="click: closeDialog">Cancel</a>
        </div>
    </bs:dialog>

    <h2 class="noBorder section-header">Agent Images</h2>
    <div class="imagesOuterWrapper">
        <div class="imagesTableWrapper">
            <span class="emptyImagesListMessage hidden"
                  data-bind="css: { hidden: images().length > 0 }">You haven't added any images yet.</span>
            <table class="settings imagesTable hidden"
                   data-bind="css: { hidden: images().length == 0 }">
                <thead>
                <tr>
                    <th class="name">Group</th>
                    <th class="name">VHD image</th>
                    <th class="name">Name prefix</th>
                    <th class="name maxInstances center">Max # of instances</th>
                    <th class="name center" colspan="2">Actions</th>
                </tr>
                </thead>
                <tbody data-bind="foreach: images">
                <tr>
                    <td class="nowrap" data-bind="text: groupId"></td>
                    <td class="nowrap">
                        <span class="osIcon osIconSmall"
                              data-bind="attr: {title: osType},
                              style: {backgroundImage: 'url(\'/img/os/' + (osType == 'Linux' ? 'lin' : 'win') + '-small-bw.png\')'}"/>
                        </span>
                        <span data-bind="text: $parent.getSourceName(storageId, imagePath).slice(-80),
                        attr: {title: $parent.getSourceName(storageId, imagePath)}"></span>
                    </td>
                    <td class="nowrap" data-bind="text: vmNamePrefix"></td>
                    <td data-bind="text: maxInstances"></td>
                    <td class="edit">
                        <a href="#"
                           data-bind="click: $parent.showDialog, css: {hidden: !$parent.isValidCredentials() || $parent.loadingGroups()}">Edit</a>
                    </td>
                    <td class="remove"><a href="#" data-bind="click: $parent.deleteImage">Delete</a></td>
                </tr>
                </tbody>
            </table>

            <c:set var="imagesData" value="${propertiesBean.properties['images_data']}"/>
            <input type="hidden" name="prop:images_data" value="<c:out value="${imagesData}"/>"
                   data-bind="initializeValue: images_data, value: images_data"/>
            <c:set var="passwordsValue" value="${propertiesBean.properties['secure:passwords_data']}"/>
            <input type="hidden" name="prop:secure:passwords_data" value="<c:out value="${passwordsValue}"/>"
                   data-bind="initializeValue: passwords_data, value: passwords_data"/>
        </div>

        <a class="btn" href="#" disabled="disabled"
           data-bind="click: showDialog.bind($data, null), attr: {disabled: !isValidCredentials() || loadingGroups() ? 'disabled' : null}">
            <span class="addNew">Add image</span>
        </a>
    </div>

</div>

<script type="text/javascript">
    BS.ArmImageDialog = OO.extend(BS.AbstractModalDialog, {
        getContainer: function () {
            return $('ArmImageDialog');
        },
        showDialog: function (addImage) {
            var action = addImage ? "Add" : "Edit";
            $j("#ArmImageDialogTitle").text(action + " Image");
            this.showCentered();
        }
    });

    $j.when($j.getScript("<c:url value="${resPath}knockout-3.4.0.js"/>").then(function () {
                return $j.when($j.getScript("<c:url value="${resPath}knockout.validation-2.0.3.js"/>"),
                        $j.getScript("<c:url value="${resPath}knockout.extenders.js"/>"));
            }),
            $j.getScript("<c:url value="${resPath}images.vm.js"/>")
    ).then(function () {
        var dialog = document.getElementById("arm-setting");
        ko.applyBindings(new ArmImagesViewModel($j, ko, "<c:url value='${basePath}'/>", BS.ArmImageDialog), dialog);
    });
</script>
<table class="runnerFormTable">
