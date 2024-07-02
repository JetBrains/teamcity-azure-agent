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

<c:set var="AZURE_PASSWORD_STUB" value="****************************************" />

<script type="text/javascript">
    BS.LoadStyleSheetDynamically("<c:url value='${resPath}settings.css'/>");
</script>

<div id="arm-setting">

    <table class="runnerFormTable" data-bind="with: credentials()">
        <l:settingsGroup title="Cloud Configuration">
        <tr>
            <th><label for="${cons.environment}">Environment: <l:star/></label></th>
            <td>
                <select name="prop:${cons.environment}" class="longField ignoreModified"
                        data-bind="options: $parent.environments, optionsText: 'text', optionsValue: 'id',
                        value: environment"></select>
                <span class="smallNote">Azure data center environment <bs:help
                        urlPrefix="https://azure.microsoft.com/en-us/global-infrastructure/geographies/" file=""/>
                </span>
                <input type="hidden" class="longField"
                       value="<c:out value="${propertiesBean.properties[cons.environment]}"/>"
                       data-bind="initializeValue: environment"/>
            </td>
        </tr>
        </l:settingsGroup>
        <l:settingsGroup title="Security Credentials">
        <c:set var="credentialsValue" value="${propertiesBean.properties[cons.credentialsType]}"/>
        <c:set var="credentialsType" value="${empty credentialsValue ? cons.credentialsService : credentialsValue}"/>
        <c:set var="azureLink">https://docs.microsoft.com/en-us/azure/azure-resource-manager/resource-group-create-service-principal-portal</c:set>
          <c:set var="wikiLink">https://github.com/JetBrains/teamcity-azure-agent/wiki</c:set>
        <tr>
            <th><label for="${cons.credentialsType}">Credentials type: <l:star/></label></th>
            <td>
                <input type="radio" value="${cons.credentialsMsi}" data-bind="checked: type"/>
                <label for="${cons.credentialsMsi}">
                    Managed Service Identity <bs:help urlPrefix="https://docs.microsoft.com/en-us/azure/active-directory/managed-service-identity/overview" file=""/>
                </label>
                <span class="smallNote">Use credentials associated with virtual machine</span>
                <input type="radio" value="${cons.credentialsService}" data-bind="checked: type"/>
                <label for="${cons.credentialsService}">
                    Service Principal <bs:help urlPrefix="${azureLink}" file=""/>
                </label>
                <span class="smallNote">Use credentials from Azure Active Directory application</span>
                <br/>
                <a href="https://portal.azure.com" target="_blank" rel="noopener noreferrer">Open Azure Portal</a>
                <input type="hidden" name="prop:${cons.credentialsType}" value="<c:out value="${credentialsType}"/>"
                       data-bind="initializeValue: type, value: type"/>
            </td>
        </tr>
        <tr data-bind="if: type() === '${cons.credentialsService}'">
            <th><label for="${cons.tenantId}">Directory ID: <l:star/></label></th>
            <td>
                <input type="text" name="prop:${cons.tenantId}" class="longField ignoreModified"
                       value="<c:out value="${propertiesBean.properties[cons.tenantId]}"/>"
                       data-bind="initializeValue: tenantId, textInput: tenantId"/>
                <span class="smallNote">Azure Active Directory ID or Tenant ID <bs:help
                        urlPrefix="${azureLink}#get-tenant-id" file=""/></span>
                <span class="error option-error" data-bind="validationMessage: tenantId"></span>
            </td>
        </tr>
        <tr data-bind="if: type() === '${cons.credentialsService}'">
            <th class="noBorder"><label for="prop:${cons.clientId}">Application ID: <l:star/></label></th>
            <td>
                <input type="text" name="prop:${cons.clientId}" class="longField ignoreModified"
                       value="<c:out value="${propertiesBean.properties[cons.clientId]}"/>"
                       data-bind="initializeValue: clientId, textInput: clientId"/>
                <span class="smallNote">Azure AD application ID <bs:help
                        urlPrefix="${azureLink}#get-application-id-and-authentication-key" file=""/></span>
                <span class="error option-error" data-bind="validationMessage: clientId"></span>
            </td>
        </tr>
        <tr data-bind="if: type() === '${cons.credentialsService}'">
            <th class="noBorder"><label for="${cons.clientSecret}">Application Key: <l:star/></label></th>
            <td>
                <input name="prop:${cons.clientSecret}" id="${cons.clientSecret}" type="password" class="longField ignoreModified"
                       data-bind="textInput: displayPassword" value="${AZURE_PASSWORD_STUB}"/>
                <input type="hidden" name="prop:encrypted:${cons.clientSecret}" id="prop:encrypted:${cons.clientSecret}"
                       data-bind="value: clientSecret"/>
                <span class="smallNote">Azure AD application key <bs:help
                        urlPrefix="${azureLink}#get-application-id-and-authentication-key" file=""/></span>
                <span class="error option-error" data-bind="validationMessage: clientSecret"></span>
            </td>
        </tr>
        </l:settingsGroup>
        <tr>
            <th><label for="${cons.subscriptionId}">Subscription: <l:star/></label></th>
            <td>
                <select name="prop:${cons.subscriptionId}" class="longField ignoreModified"
                        data-bind="options: $parent.subscriptions, optionsText: 'text', optionsValue: 'id',
                        value: subscriptionId, css: {hidden: $parent.subscriptions().length == 0}"></select>
                <div class="longField inline-block hidden"
                     data-bind="css: {hidden: $parent.subscriptions().length > 0}">
                    <span class="error option-error">
                        No subscriptions, please <a href="${azureLink}#assign-application-to-role" target="_blank" rel="noopener noreferrer">grant
                        Contributor role</a> for your application
                    </span>
                </div>
                <a href="#" title="Reload subscriptions"
                   data-bind="click: $parent.loadSubscriptions,
                   css: {invisible: !$parent.isValidClientData()}">
                    <i data-bind="css: {'icon-spin': $parent.loadingSubscriptions}" class="icon-refresh"></i>
                </a>
                <span class="error option-error" data-bind="validationMessage: subscriptionId"></span>
                <input type="hidden" class="longField"
                       value="<c:out value="${propertiesBean.properties[cons.subscriptionId]}"/>"
                       data-bind="initializeValue: subscriptionId"/>
                <input type="hidden" class="longField"
                       value="<c:out value="${propertiesBean.properties['location']}"/>"
                       data-bind="initializeValue: region"/>
            </td>
        </tr>
    </table>

    <bs:dialog dialogId="ArmImageDialog" title="Add Image" closeCommand="BS.ArmImageDialog.close()"
               dialogClass="AzureImageDialog" titleId="ArmImageDialogTitle">
        <table class="runnerFormTable">
            <tr>
                <th><label for="${cons.deployTarget}">Deployment: <l:star/></label></th>
                <td>
                    <select name="${cons.deployTarget}" class="longField ignoreModified"
                            data-bind="options: deployTargets, optionsText: 'text', optionsValue: 'id',
                            value: image().deployTarget"></select>
                    <bs:help urlPrefix="${wikiLink}#deployment-types" file=""/>
                    <span class="error option-error" data-bind="validationMessage: image().deployTarget"></span>
                </td>
            </tr>
            <tr data-bind="css: {hidden: image().deployTarget() != 'NewGroup'}">
                <th class="noBorder"><label for="${cons.region}">Region: <l:star/></label></th>
                <td>
                    <select name="${cons.region}" class="longField ignoreModified"
                            data-bind="options: regions, optionsText: 'text', optionsValue: 'id',
                            optionsCaption: '<Select>', value: image().region, enable: regions().length > 0"></select>
                    <a href="#" title="Reload regions" data-bind="click: loadRegions.bind($data, ['regions'])">
                        <i data-bind="css: {'icon-spin': loadingRegions}" class="icon-refresh"></i>
                    </a>
                    <span class="error option-error" data-bind="validationMessage: image().region"></span>
                </td>
            </tr>
            <tr data-bind="if: image().deployTarget() == 'SpecificGroup'">
                <th class="noBorder"><label for="${cons.groupId}">Resource Group: <l:star/></label></th>
                <td>
                    <div data-bind="if: resourceGroups().length > 0">
                        <select name="${cons.groupId}" class="longField ignoreModified"
                                data-bind="options: resourceGroups, optionsText: 'text', optionsValue: 'text',
                                optionsCaption: '<Select>', value: image().groupId"></select>
                        <a href="#" title="Reload resource groups"
                           data-bind="click: loadRegions.bind($data, ['resourceGroups'])">
                            <i data-bind="css: {'icon-spin': loadingRegions}" class="icon-refresh"></i>
                        </a>
                        <span class="error option-error" data-bind="validationMessage: image().groupId"></span>
                    </div>
                    <div data-bind="if: resourceGroups().length == 0">
                        <span class="error option-error">No available resource groups in subscription</span>
                    </div>
                </td>
            </tr>
            <!-- ko if: image().deployTarget() !== 'Instance' -->
            <tr>
                <th><label for="${cons.imageType}">Image Type: <l:star/></label></th>
                <td>
                    <select name="${cons.imageType}" class="longField ignoreModified"
                            data-bind="options: imageTypes, optionsText: 'text', optionsValue: 'id',
                            value: image().imageType"></select>
                    <bs:help urlPrefix="${wikiLink}#image-types" file=""/>
                    <span class="error option-error" data-bind="validationMessage: image().imageType"></span>
                    <span class="error option-error" data-bind="text: errorResources"></span>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() == 'Vhd'">
                <th class="noBorder"><label for="${cons.imageUrl}">Source Image: <l:star/></label></th>
                <td>
                    <input type="text" name="${cons.imageUrl}" class="longField ignoreModified"
                           placeholder="Example: http://storage.blob.core.windows.net/vhds/image.vhd"
                           data-bind="textInput: image().imageUrl"/>
                    <span class="osIcon osIconSmall"
                          data-bind="attr: {title: image().osType}, css: {invisible: !image().osType()},
                          style: {backgroundImage: getOsImage(image())}"/>
                    </span>
                    <span class="smallNote">URL of generalized VHD image placed in the <a
                            href="https://azure.microsoft.com/en-us/documentation/articles/resource-group-portal/"
                            target="_blank"
                            rel="noopener noreferrer">new storage account</a> <bs:help
                            file="TeamCity+Integration+with+Cloud+Solutions"
                            anchor="TeamCitySetupforCloudIntegration"/>
                    </span>
                    <span class="error option-error" data-bind="validationMessage: image().imageUrl"></span>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() == 'Image' || image().imageType() == 'GalleryImage'">
                <th class="noBorder"><label for="${cons.imageId}">Source Image: <l:star/></label></th>
                <td>
                    <div data-bind="if: filteredSourceImages().length > 0">
                        <select name="${cons.imageId}" id="${cons.imageId}" class="longField ignoreModified"
                                data-bind="options: filteredSourceImages, optionsText: 'text', optionsValue: 'id',
                                    optionsCaption: '<Select>', value: image().imageId"></select>
                        <!-- ko if: loadingResources -->
                        <i class="icon-refresh icon-spin"></i>
                        <!-- /ko -->
                        <span class="osIcon osIconSmall"
                              data-bind="attr: {title: image().osType}, css: {invisible: !image().osType()},
                            style: {backgroundImage: getOsImage(image())}"/>
                        </span>
                        <span class="error option-error" data-bind="validationMessage: image().imageId"></span>
                    </div>
                    <div data-bind="if: filteredSourceImages().length == 0">
                      <span class="error option-error">
                          No images found in <span data-bind="text: regionName"></span> region
                      </span>
                    </div>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() == 'Container'">
                <th class="noBorder"><label for="${cons.imageId}">Docker Image: <l:star/></label></th>
                <td>
                    <input type="text" name="${cons.imageId}" class="longField ignoreModified"
                           data-bind="textInput: image().imageId"/>
                    <span class="error option-error" data-bind="validationMessage: image().imageId"></span>
                </td>
            </tr>
            <tr data-bind="if: !osType() && image().imageType() != 'Template' || image().imageType() != 'Container'">
                <th class="noBorder"><label for="${cons.osType}">OS Type: <l:star/></label></th>
                <td>
                    <select name="${cons.osType}" class="longField ignoreModified"
                            data-bind="options: osTypes, optionsCaption: '<Select>', value: image().osType"></select>
                    <!-- ko if: loadingOsType -->
                    <i class="icon-refresh icon-spin"></i>
                    <!-- /ko -->
                    <span class="error option-error" data-bind="validationMessage: image().osType"></span>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() == 'Container'" class="advancedSetting">
                <th class="noBorder"><label for="${cons.registryUsername}">Registry username:</label></th>
                <td>
                    <div>
                        <input type="text" name="${cons.registryUsername}" class="longField ignoreModified"
                               data-bind="textInput: image().registryUsername"/>
                        <span class="error option-error" data-bind="validationMessage: image().registryUsername"></span>
                    </div>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() == 'Container'" class="advancedSetting">
                <th class="noBorder"><label for="${cons.registryPassword}">Registry password:</label></th>
                <td>
                    <div>
                        <input type="password" class="longField ignoreModified"
                               data-bind="textInput: image().registryPassword"/>
                        <span class="error option-error" data-bind="validationMessage: image().registryPassword"></span>
                    </div>
                </td>
            </tr>
            <tr>
                <th><label for="${cons.vmNamePrefix}">Name Prefix: <l:star/></label></th>
                <td>
                    <input type="text" name="${cons.vmNamePrefix}" class="longField ignoreModified"
                           data-bind="textInput: image().vmNamePrefix"/>
                    <span class="smallNote">Unique name prefix to create resources</span>
                    <span class="error option-error" data-bind="validationMessage: image().vmNamePrefix"></span>
                </td>
            </tr>
            <tr>
                <th><label for="${cons.maxInstancesCount}">Instances Limit: <l:star/></label></th>
                <td>
                    <div>
                        <input type="text" name="${cons.maxInstancesCount}" class="longField ignoreModified"
                               data-bind="textInput: image().maxInstances"/>
                        <span class="smallNote">Maximum number of instances which can be started</span>
                        <span class="error option-error" data-bind="validationMessage: image().maxInstances"></span>
                    </div>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() != 'Container'">
                <th class="noBorder"></th>
                <td>
                    <input type="checkbox" name="${cons.reuseVm}" data-bind="checked: image().reuseVm"/>
                    <label for="${cons.reuseVm}">Reuse allocated virtual machines</label>
                    <span class="smallNote">Allows reusing terminated virtual machines after shutdown</span>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() != 'Template' && image().imageType() != 'Container'">
                <th><label for="${cons.vmSize}">Virtual Machine Size: <l:star/></label></th>
                <td>
                    <select name="${cons.vmSize}" class="longField ignoreModified"
                            data-bind="options: vmSizes, optionsText: function (item) {
                                return item.replace(/_/g, ' ');
                            }, value: image().vmSize, css: {hidden: vmSizes().length == 0}"></select>
                    <div class="longField inline-block" data-bind="css: {hidden: vmSizes().length > 0}">
                        <span class="error option-error">
                            No machine sizes found in <span data-bind="text: regionName"></span> region
                        </span>
                    </div>
                    <!-- ko if: loadingResources -->
                    <i class="icon-refresh icon-spin"></i>
                    <!-- /ko -->
                </td>
            </tr>
            <tr data-bind="if: (image().imageType() == 'Image' || image().imageType() == 'GalleryImage') && image().imageType() != 'Container'">
                <th class="noBorder"><label for="${cons.storageAccountType}">Disk Type: <l:star/></label></th>
                <td>
                    <select name="${cons.storageAccountType}" class="longField ignoreModified"
                            data-bind="options: storageAccountTypes, optionsText: 'text', optionsValue: 'id',
                            optionsCaption: '<Not specified>', value: image().storageAccountType"></select>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() == 'Container'">
                <th><label for="${cons.numberCores}">Number of Cores: <l:star/></label></th>
                <td>
                    <div>
                        <input type="text" name="${cons.numberCores}" class="longField ignoreModified"
                               data-bind="textInput: image().numberCores"/>
                        <span class="error option-error" data-bind="validationMessage: image().numberCores"></span>
                    </div>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() == 'Container'">
                <th class="noBorder"><label for="${cons.memory}">Memory (GB): <l:star/></label></th>
                <td>
                    <div>
                        <input type="text" name="${cons.memory}" class="longField ignoreModified"
                               data-bind="textInput: image().memory"/>
                        <span class="error option-error" data-bind="validationMessage: image().memory"></span>
                    </div>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() == 'Image' || image().imageType() == 'GalleryImage'">
                <th><label for="${cons.spotVm}">Spot instance:</label></th>
                <td>
                    <input type="checkbox" name="${cons.spotVm}" data-bind="checked: image().spotVm"/>
                    <span class="smallNote">
                        Use Azure capacity at a discounted rate.
                        <bs:help
                            urlPrefix="https://docs.microsoft.com/en-us/azure/virtual-machines/spot-vms"
                            file=""/>
                    </span>
                </td>
            </tr>
            <tr data-bind="if: (image().imageType() == 'Image' || image().imageType() == 'GalleryImage') && image().spotVm()">
                <th class="noBorder"></th>
                <td>
                    <input type="checkbox" name="${cons.enableSpotPrice}" data-bind="checked: image().enableSpotPrice"/>
                    <label for="${cons.spotVm}">Evict by price</label>
                    <span class="smallNote">
                        Allow evicting virtual machine when the cost of the instance is greater than your max price.
                        <bs:help
                            urlPrefix="https://docs.microsoft.com/en-us/azure/virtual-machines/spot-vms#eviction-policy"
                            file=""/>
                    </span>
                </td>
            </tr>
            <tr data-bind="if: (image().imageType() == 'Image' || image().imageType() == 'GalleryImage') && image().spotVm() && image().enableSpotPrice()">
                <th class="noBorder"><label for="${cons.spotPrice}">Max price: <l:star/></label></th>
                <td>
                    <div>
                        <input type="text" name="${cons.spotPrice}" class="longField ignoreModified"
                               data-bind="textInput: image().spotPrice"/>
                        <span class="smallNote">
                            Choose the maximum price per hour you are willing to pay for a single instance of the selected virtual machine size. Prices are in USD ($).
                            <bs:help
                                urlPrefix="https://docs.microsoft.com/en-us/azure/virtual-machines/spot-vms#pricing"
                                file=""/>
                        </span>
                        <span class="error option-error" data-bind="validationMessage: image().spotPrice"></span>
                    </div>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() != 'Template' && image().imageType() != 'Container'">
                <th><label for="${cons.networkId}">Virtual Network: <l:star/></label></th>
                <td>
                    <select name="${cons.networkId}" class="longField ignoreModified"
                            data-bind="options: networks, optionsText: function (item) {
                                return item.substring(item.lastIndexOf('/') + 1);
                            }, value: image().networkId, css: {hidden: networks().length == 0}"></select>
                    <div class="longField inline-block" data-bind="css: {hidden: networks().length > 0}">
                        <span class="error option-error">
                            No virtual networks found in <span data-bind="text: regionName"></span> region
                        </span>
                    </div>
                    <!-- ko if: loadingResources -->
                    <i class="icon-refresh icon-spin"></i>
                    <!-- /ko -->
                </td>
            </tr>
            <tr data-bind="if: image().imageType() === 'Container'">
                <th><label for="${cons.networkId}">Virtual Network: </label></th>
                <td>
                    <select name="${cons.networkId}" class="longField ignoreModified"
                            data-bind="options: networks, optionsText: function (item) {
                                return item.substring(item.lastIndexOf('/') + 1);
                            }, optionsCaption: '<Not specified>', value: image().networkId, css: {hidden: networks().length == 0}"></select>
                    <div class="longField inline-block" data-bind="css: {hidden: networks().length > 0}">
                        No virtual networks found in <span data-bind="text: regionName"></span> region
                    </div>
                    <!-- ko if: loadingResources -->
                    <i class="icon-refresh icon-spin"></i>
                    <!-- /ko -->
                </td>
            </tr>
            <tr data-bind="if: image().imageType() === 'Container'">
                <th class="noBorder"><label for="${cons.subnetId}">Sub Network: </label></th>
                <td>
                    <select name="${cons.subnetId}" class="longField ignoreModified"
                            data-bind="options: subNetworks, value: image().subnetId, css: {hidden: subNetworks().length == 0}"></select>
                    <div class="longField inline-block" data-bind="css: {hidden: subNetworks().length > 0 || !image().networkId() }">
                        <span class="error option-error">
                            No sub networks found in <span data-bind="text: regionName"></span> region
                        </span>
                    </div>
                    <div class="longField inline-block" data-bind="css: {hidden: image().networkId()}">
                        Not specified
                    </div>
                    <!-- ko if: loadingResources -->
                    <i class="icon-refresh icon-spin"></i>
                    <!-- /ko -->
                    <span class="smallNote" data-bind="css: {smallNote_hidden: !image().networkId()}">Specify subnet which can be used by the dedicated service Microsoft.ContainerInstance/containerGroups.
                        <bs:help
                            urlPrefix="https://docs.microsoft.com/en-us/azure/virtual-network/manage-subnet-delegation#delegate-a-subnet-to-an-azure-service"
                            file=""/>
                    </span>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() != 'Template' && image().imageType() != 'Container'">
                <th class="noBorder"><label for="${cons.subnetId}">Sub Network: <l:star/></label></th>
                <td>
                    <select name="${cons.subnetId}" class="longField ignoreModified"
                            data-bind="options: subNetworks, value: image().subnetId, css: {hidden: subNetworks().length == 0}"></select>
                    <div class="longField inline-block" data-bind="css: {hidden: subNetworks().length > 0}">
                        <span class="error option-error">
                            No sub networks found in <span data-bind="text: regionName"></span> region
                        </span>
                    </div>
                    <!-- ko if: loadingResources -->
                    <i class="icon-refresh icon-spin"></i>
                    <!-- /ko -->
                </td>
            </tr>
            <tr data-bind="if: image().imageType() != 'Template' && image().imageType() != 'Container'">
                <th class="noBorder"></th>
                <td>
                    <input type="checkbox" name="${cons.vmPublicIp}" data-bind="checked: image().vmPublicIp"/>
                    <label for="${cons.vmPublicIp}">Create public IP address</label>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() != 'Template' && image().imageType() != 'Container'">
                <th class="noBorder"></th>
                <td>
                  <input type="checkbox" name="${cons.enableAcceleratedNetworking}" data-bind="checked: image().enableAcceleratedNetworking"/>
                  <label for="${cons.enableAcceleratedNetworking}">Enable accelerated networking</label>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() != 'Template' && image().imageType() != 'Container'">
                <th><label for="${cons.vmUsername}">Provision Username: <l:star/></label></th>
                <td>
                    <input type="text" id="${cons.vmUsername}" class="longField ignoreModified"
                           data-bind="textInput: image().vmUsername"/>
                    <span class="error option-error" data-bind="validationMessage: image().vmUsername"></span>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() != 'Template' && image().imageType() != 'Container'">
                <th class="noBorder"><label for="${cons.vmPassword}">Provision Password: <l:star/></label></th>
                <td>
                    <input type="password" id="${cons.vmPassword}" class="longField ignoreModified"
                           data-bind="textInput: image().vmPassword"/>
                    <span class="smallNote">Choose value according to the <a
                            href="https://msdn.microsoft.com/en-us/library/azure/jj943764.aspx#Anchor_1"
                            target="_blank"
                            rel="noopener noreferrer">password policies</a></span>
                    <span class="error option-error" data-bind="validationMessage: image().vmPassword"></span>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() == 'Template'">
                <th class="noBorder"><label for="${cons.template}">ARM Template: <l:star/></label></th>
                <td>
                    <div class="vmTemplateSection">
                      <textarea name="${cons.template}" class="longField ignoreModified vmTemplate"
                                rows="12" cols="49"
                                data-bind="textInput: image().template"></textarea>
                      <div class="vmParamsTitle">
                        Template parameters:
                        <a href="#" title="Set default template" data-bind="click: setDefaultTemplate">
                          <i class="icon-file-alt"></i>
                        </a>
                      </div>
                      <div class="vmParamsContainer" data-bind="foreach: templateParameters">
                        <div class="vmParam" data-bind="css: { vmParamRed: !$data.isMatched && ($data.isProvided || !$data.hasValue), vmParamGreen: $data.isMatched }, attr: {title: $parent.getTemplatePartameterTooltipText($data)}">
                          <div class="vmParamText" data-bind="text: name"></div>
                        </div>
                      </div>
                    </div>
                    <span class="smallNote">Specify the ARM template.
                        <bs:help
                            urlPrefix="https://docs.microsoft.com/en-us/azure/azure-resource-manager/resource-group-authoring-templates"
                            file=""/>
                    </span>
                    <span class="error option-error" data-bind="validationMessage: image().template"></span>
                    <span class="error option-error" data-bind="validationMessage: image().templateParameters"></span>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() == 'Template'">
              <th class="noBorder"></th>
              <td>
                <input type="checkbox" name="${cons.disableTemplateModification}" data-bind="checked: image().disableTemplateModification"/>
                <label for="${cons.disableTemplateModification}">Disable template modification</label>
                <span class="smallNote">When checked TeamCity uses parameters instead of modifying the template.
                  <bs:help
                      urlPrefix="https://github.com/JetBrains/teamcity-azure-agent/wiki/Template-Image"
                      file=""/>
                </span>
              </td>
            </tr>
            <tr data-bind="if: image().imageType() == 'Container' && image().osType() == 'Linux'">
                <th><label for="${cons.storageAccount}">Storage account: </label></th>
                <td>
                    <select name="${cons.storageAccount}" class="longField ignoreModified"
                            data-bind="options: storageAccounts, value: image().storageAccount,
                            optionsCaption: '<Not specified>', css: {hidden: storageAccounts().length == 0}"></select>
                    <div class="longField inline-block" data-bind="css: {hidden: storageAccounts().length > 0}">
                        <span class="error option-error">
                            No storage accounts found in <span data-bind="text: regionName"></span> region
                        </span>
                    </div>
                    <!-- ko if: loadingResources -->
                    <i class="icon-refresh icon-spin"></i>
                    <!-- /ko -->
                    <span class="smallNote">
                        Specify account to persist container volumes in Azure Files. It prevents build agent upgrade on each start
                    </span>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() != 'Template'" class="advancedSetting">
                <th><label for="${cons.customTags}">Tags:</label></th>
                <td>
                    <div>
                        <textarea name="${cons.customTags}" class="longField ignoreModified"
                                  data-bind="textInput: image().customTags">
                        </textarea>
                        <span class="smallNote">Newline separated tags in the form of <kbd>KEY=VALUE</kbd> to add to the Azure resources
                        </span>
                        <span class="error option-error" data-bind="validationMessage: image().customTags"></span>
                    </div>
                </td>
            </tr>
            <tr data-bind="if: image().imageType() == 'Container'" class="advancedSetting">
                <th><label for="${cons.customEnvironmentVariables}">Environment Variables:</label></th>
                <td>
                    <div>
                        <textarea name="${cons.customEnvironmentVariables}" class="longField ignoreModified"
                               data-bind="textInput: image().customEnvironmentVariables">
                        </textarea>
                        <span class="smallNote">Newline separated environment variables in the form of <kbd>KEY=VALUE</kbd> to pass to the running container
                            <bs:help
                                urlPrefix="https://docs.microsoft.com/en-us/azure/container-instances/container-instances-environment-variables#azure-portal-example"
                                file=""/>
                        </span>
                        <span class="error option-error" data-bind="validationMessage: image().customEnvironmentVariables"></span>
                    </div>
                </td>
            </tr>
            <!-- /ko -->
            <tr data-bind="if: image().deployTarget() === 'Instance'">
                <th><label for="${cons.instanceId}">Virtual Machine: <l:star/></label></th>
                <td>
                    <div data-bind="if: instances().length > 0">
                        <select name="${cons.instanceId}" class="longField ignoreModified"
                                data-bind="options: instances, optionsText: 'text', optionsValue: 'id',
                                    optionsCaption: '<Select>', value: image().instanceId"></select>
                    </div>
                    <div data-bind="if: instances().length == 0">
                      <span class="error option-error">
                          No instance found in subscription
                      </span>
                    </div>
                </td>
            </tr>
            <tr class="advancedSetting">
                <th><label for="${cons.agentPoolId}">Agent Pool:</label></th>
                <td>
                    <select name="prop:${cons.agentPoolId}" class="longField ignoreModified"
                            data-bind="options: agentPools, optionsText: 'text', optionsValue: 'id',
                        value: image().agentPoolId"></select>
                    <span id="error_${cons.agentPoolId}" class="error"></span>
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
            <table class="settings arm-settings imagesTable hidden"
                   data-bind="css: { hidden: images().length == 0 }">
                <thead>
                <tr>
                    <th class="name">Name Prefix</th>
                    <th class="name">Source Image</th>
                    <th class="name center" title="Maximum number of instances">Limit</th>
                    <th class="name center" colspan="2">Actions</th>
                </tr>
                </thead>
                <tbody data-bind="foreach: images">
                <tr>
                    <td class="nowrap" data-bind="text: vmNamePrefix"></td>
                    <td class="nowrap">
                        <span class="osIcon osIconSmall"
                              data-bind="attr: {title: $data.osType || null},
                                      style: {backgroundImage: $parent.getOsImage($data)}"/>
                        </span>

                        <!-- ko if: deployTarget !== 'Instance' -->
                            <!-- ko if: imageType === 'Vhd' -->
                            <span data-bind="text: imageUrl.slice(-80), attr: {title: imageUrl}"></span>
                            <!-- /ko -->
                            <!-- ko if: imageType === 'Image' -->
                            <span data-bind="text: $parent.getFileName(imageId)"></span>
                            <!-- /ko -->
                            <!-- ko if: imageType === 'GalleryImage' -->
                            <span data-bind="text: $parent.getGalleryImageName(imageId)"></span>
                            <!-- /ko -->
                            <!-- ko if: imageType === 'Container' -->
                            <span data-bind="text: imageId"></span>
                            <!-- /ko -->
                            <!-- ko if: imageType === 'Template' -->
                            <span>Custom Template</span>
                            <!-- /ko -->
                        <!-- /ko -->

                        <!-- ko if: deployTarget === 'Instance' -->
                        <span data-bind="text: $parent.getFileName(instanceId)"></span>
                        <!-- /ko -->
                    </td>
                    <td class="center" data-bind="text: maxInstances"></td>
                    <td class="edit">
                        <a href="#" data-bind="click: $parent.showDialog,
                        css: {hidden: !$parent.isValidCredentials() || $parent.loadingSubscriptions ||
                        $parent.loadingRegions}">Edit</a>
                    </td>
                    <td class="remove"><a href="#" data-bind="click: $parent.deleteImage">Delete</a></td>
                </tr>
                </tbody>
            </table>

            <c:set var="sourceImagesData" value="${propertiesBean.properties[cons.imagesData]}"/>
            <c:set var="imagesData" value="${propertiesBean.properties['images_data']}"/>
            <input type="hidden" name="prop:${cons.imagesData}"
                   value="<c:out value="${empty sourceImagesData || sourceImagesData == '[]' ? imagesData : sourceImagesData}"/>"
                   data-bind="initializeValue: images_data, value: images_data"/>
            <c:set var="passwordsValue" value="${propertiesBean.properties['secure:passwords_data']}"/>
            <input type="hidden" name="prop:secure:passwords_data" value="<c:out value="${passwordsValue}"/>"
                   data-bind="initializeValue: passwords_data, value: passwords_data"/>
        </div>

        <a class="btn" href="#" disabled="disabled"
           data-bind="click: showDialog.bind($data, null),
           attr: {disabled: (!isValidCredentials() || loadingSubscriptions() || loadingRegions()) ? 'disabled' : null}">
            <span class="addNew">Add Image</span>
        </a>
        <span data-bind="css: {invisible: !loadingRegions()}">
            <i class="icon-spin icon-refresh"></i> Loading regions...
        </span>
    </div>

</div>

<script type="text/javascript">
    $j(function () {
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
          ko.validation.init({insertMessages: false});
          ko.applyBindings(new ArmImagesViewModel($j, ko, BS.ArmImageDialog, {
              baseUrl: "<c:url value='${basePath}'/>",
              projectId: "${projectId}",
              contextPath: "${contextPath}",
              imageListControlId: "${cons.imageId}",
              publicKey: "${publicKey}",
              clientSecret: "${empty propertiesBean.properties[cons.clientSecret] ? "" : propertiesBean.getEncryptedPropertyValue(cons.clientSecret)}",
              azurePassStub: "${AZURE_PASSWORD_STUB}"
          }), dialog);
      });
    });
</script>
<table class="runnerFormTable">
