# TeamCity Azure Cloud Plugins

Enables TeamCity cloud integration with Microsoft Azure and allows virtual machines usage to scale the pool of build agents.

## Table Of Contents

* [Overview](#overview)
* [Installation](#installation)
* [Classic Plugin](#classic-plugin)
* [Resource Manager Plugin](#resource-manager-plugin)
* [License](#license)
* [Feedback](#feedback)

## Overview

This repository contains plugins for Classic and Resource Manager deployment models.
You can select an approriate plugin according to the [Microsoft deployment guidelines](https://azure.microsoft.com/en-us/documentation/articles/azure-classic-rm/).

## Installation

You can download the last successful plugin build and install it as an [additional TeamCity plugin](https://confluence.jetbrains.com/display/TCDL/Installing+Additional+Plugins).

### [Resource Manager](#resource-manager-plugin)
[Blog post](https://blog.jetbrains.com/teamcity/2016/04/teamcity-azure-resource-manager/) about resource manager plugin.

| TeamCity | Status | Download |
|----------|--------|----------|
| 10.x+ | <a href="https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamcityAzurePlugin_BuildResourceManager&branch_TeamcityAzurePlugin=%3Cdefault%3E&guest=1"><img src="https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamcityAzurePlugin_BuildResourceManager),branch:(name:master)/statusIcon.svg" alt=""/></a> | [Download](https://teamcity.jetbrains.com/app/rest/builds/buildType:TeamcityAzurePlugin_BuildResourceManager,branch:default:any,tag:release/artifacts/content/cloud-azure-arm.zip?guest=1) |
| 9.1.4+ | <a href="https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamcityAzurePlugin_BuildResourceManager&branch_TeamcityAzurePlugin=9.1.x&guest=1"><img src="https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamcityAzurePlugin_BuildResourceManager),branch:(name:9.1.x)/statusIcon.svg" alt=""/></a> | [Download](https://teamcity.jetbrains.com/repository/download/TeamcityAzurePlugin_BuildResourceManager/.lastSuccessful/cloud-azure-arm.zip?branch=9.1.x&guest=1) |

### [Classic](#classic-plugin) 
[Blog post](https://blog.jetbrains.com/teamcity/2014/11/introducing-teamcity-azure-plugin-run-builds-in-the-cloud/) about classic plugin.

| TeamCity | Status | Download |
|----------|--------|----------|
| 10.x+ | <a href="https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamcityAzurePlugin_Build&branch_TeamcityAzurePlugin=%3Cdefault%3E&guest=1"><img src="https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamcityAzurePlugin_Build),branch:(name:master)/statusIcon.svg" alt=""/></a> | [Download](https://teamcity.jetbrains.com/app/rest/builds/buildType:TeamcityAzurePlugin_Build,branch:default:any,tag:release/artifacts/content/azure-cloud.zip?guest=1) |
| 9.1.4+ | <a href="https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamcityAzurePlugin_Build&branch_TeamcityAzurePlugin=9.1.x&guest=1"><img src="https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamcityAzurePlugin_Build),branch:(name:9.1.x)/statusIcon.svg" alt=""/></a> | [Download](https://teamcity.jetbrains.com/repository/download/TeamcityAzurePlugin_Build/.lastSuccessful/azure-cloud.zip?branch=9.1.x&guest=1) |

## Resource Manager Plugin

The plugin supports generalized virtual machine images to start TeamCity build agents. You must use the [Azure AD application](https://azure.microsoft.com/en-us/documentation/articles/resource-group-create-service-principal-portal/#create-application) and the [authentication key](https://azure.microsoft.com/en-us/documentation/articles/resource-group-create-service-principal-portal/#create-an-authentication-key) to enable cloud integration and assign the [_Contributor_ role](https://azure.microsoft.com/en-us/documentation/articles/resource-group-create-service-principal-portal/#assign-application-to-role) for it in your subscription on the [new portal](https://portal.azure.com/).

### Virtual Machines Preparation

Before you can start using integration, you need to create a new Virtual Machine instance via Resource Manager. The TeamCity Build Agent [must be installed](https://confluence.jetbrains.com/display/TCD9/TeamCity+Integration+with+Cloud+Solutions#TeamCityIntegrationwithCloudSolutions-PreparingavirtualmachinewithaninstalledTeamCityagent) and set to start automatically. Also, you need to manually point the agent to the existing TeamCity server with the Azure plugin installed to let the build agent download the plugins.

> :grey_exclamation: If you plan to start agent as a Windows service under SYSTEM use `Automatic (Delayed Start)` startup type.

Then you should [remove temporary files](https://confluence.jetbrains.com/display/TCD9/TeamCity+Integration+with+Cloud+Solutions#TeamCityIntegrationwithCloudSolutions-Capturinganimagefromavirtualmachine) and perform capture using the following guidelines for [Linux](https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-linux-capture-image/) and [Windows](https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-windows-capture-image/) virtual machines. As a result, you will receive a VHD image in your storage account whose URL can be used to create build agents.

## Classic Plugin

This plugin supports both Windows and Linux virtual machines and can operate in the following ways.

#### Fresh clone

When TeamCity realizes that it needs more agents, it starts a new instance from the image and deletes it
after it becomes unnecessary (when a defined timeout elapsed). Depending on the image details, the behaviour would vary:

* **Deprovisioned image** - in this case TeamCity will bypass the server URL directly into the created image, so you can use the same image with several TeamCity servers. To create an image, please follow gudelines for [Linux](https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-linux-classic-capture-image/) and [Windows](https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-windows-classic-capture-image/) virtual machines. You also need to provide a username/password to let Azure create a new user on the newly created VM. The credentials must match the Azure credentials requirements: the username should contain at least 5 symbols and the password should match the [passwords policy](http://msdn.microsoft.com/en-us/library/ms161959.aspx).
* **Specialized image** in this case the user does not prepare an image before capturing. This type of image requires the `serverUrl`
  property in the `buildAgent.properties` to point to the TeamCity server. If the server address changes, the image becomes invalid and you need to create a new one.

#### Start/Stop

It works with the currently existing Virtual Machine instances. TeamCity starts the instance before the build and stops after a build or idle timeout (depending on the profile settings). The machine state is saved. When the TeamCity server url changes, you need to change the `serverUrl` parameter in the `buildAgent.properties` to point to the new server.

### Virtual Machines Preparation

Before you can start using integration, you need to create a new classic Virtual Machine instance. The Teamcity Build Agent must be installed and set to start automatically. Also, you need to manually point the agent to the existing TeamCity server with the Azure plugin installed to let the build agent download the plugins.

To use the _Fresh Clone_ behaviour, you need to create an image from this instance (depending on whether you checked
"I have run the Windows Azure Linux Agent on the virtual machine"/" I have run Sysprep on the virtual machine" checkbox) the image will be Generalized (checked) or Specialized (unchecked) and behaviour would slightly differ).

To use _Start/Stop_ behaviour, you just need to stop the Virtual Machine Instance before configuring it in TeamCity.

## License

Apache 2.0

## Feedback

Please feel free to post an issue in the [TeamCity issue tracker](https://youtrack.jetbrains.com/issues/TW).
