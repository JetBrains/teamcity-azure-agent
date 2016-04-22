# TeamCity Azure Cloud Plugins

Enables TeamCity cloud integration with Microsoft Azure and allows virtual machines usage to scale pool of build agents.

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

You can download last successful plugin build and install it as [additional TeamCity plugin](https://confluence.jetbrains.com/display/TCDL/Installing+Additional+Plugins).

| Plugin | Status | Download | TeamCity |
|--------|--------|----------|----------|
| [Classic](#classic-plugin) | <a href="https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamcityAzurePlugin_Build&guest=1"><img src="https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamcityAzurePlugin_Build)/statusIcon" alt=""/></a> | [Download](https://teamcity.jetbrains.com/repository/download/TeamcityAzurePlugin_Build/.lastSuccessful/azure-cloud.zip?guest=1) | 8.1.x+ |
| [Resource Manager](#resource-manager-plugin) | <a href="https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamcityAzurePlugin_BuildResourceManager&guest=1"><img src="https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamcityAzurePlugin_BuildResourceManager)/statusIcon" alt=""/></a> | [Download](https://teamcity.jetbrains.com/repository/download/TeamcityAzurePlugin_BuildResourceManager/.lastSuccessful/cloud-azure-arm.zip?guest=1)| 9.1.x+ |

## Classic Plugin

This plugin supports both Windows and Linux virtual machines and can operate in the following ways.

#### Fresh clone

When TeamCity realizes that it needs more agents, it starts a new instance from the image and deletes it
after it becomes unnecessary (a defined timeout elapsed). Depending on the image details, the behaviour would vary:

* **Deprovisioned image** - in this case TeamCity will bypass server URL directly into the created image, so you can use the same image with several TC servers. To create image please follow gudelines for [Linux](https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-linux-classic-capture-image/) and [Windows](https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-windows-classic-capture-image/) virtual machines. You also need to provide a username/password to let Azure create a new user on newly created VM. The credentials must match the Azure credentials requirements: username should contains at least 5 symbols and password show match [passwords policy](http://msdn.microsoft.com/en-us/library/ms161959.aspx).
* **Specialized image** in this case user didn't prepared image before capturing. It requires the `serverUrl`
  property in the `buildAgent.properties` to point to the TC server. If the server address was changed, the image becomes invalid and you need to create a new one.

#### Start/Stop

It works with currently existing Virtual Machine instances. TeamCity starts the instance before the build and stops after a build or idle timeout (depending on profile settings). Machine state is saved. When TeamCity server url changes, you need to change the `serverUrl` parameter in the `buildAgent.properties` to point to the new server.

### Virtual Machines Preparation

Before you can start using integration, you need to create a new classic Virtual Machine instance. Teamcity Build Agent must be installed and set to start automatically. Also, you need to manually point the agent to existing TC server with Azure plugin installed to let the build agent download the plugins.

If you'd like to use _Fresh Clone_ behaviour, you need to create an image from this instance (depending on whether you checked
"I have run the Windows Azure Linux Agent on the virtual machine"/" I have run Sysprep on the virtual machine" checkbox) the image will be Generalized (checked) or Specialized (unchecked) and behaviour would slightly differ).

If you'd like to use _Start/Stop_ behaviour, you just need to stop the Virtual Machine Instance before the configuring it in TC.

## Resource Manager Plugin

It supports generalized virtual machine images to start TeamCity build agents. You must use [Azure AD application](https://azure.microsoft.com/en-us/documentation/articles/resource-group-create-service-principal-portal/#create-application) and [authentication key](https://azure.microsoft.com/en-us/documentation/articles/resource-group-create-service-principal-portal/#create-an-authentication-key) to enable cloud integration and assign the [_Contributor_ role](https://azure.microsoft.com/en-us/documentation/articles/resource-group-create-service-principal-portal/#assign-application-to-role) for it in your subscription on the [new portal](https://portal.azure.com/).

### Virtual Machines Preparation

Before you can start using integration, you need to create a new Virtual Machine instance via Resource Manager. Teamcity Build Agent must be installed and set to start automatically. Also, you need to manually point the agent to existing TC server with Azure plugin installed to let the build agent download the plugins.

Then you should [remove temporary files](https://confluence.jetbrains.com/display/TCD9/TeamCity+Integration+with+Cloud+Solutions#TeamCityIntegrationwithCloudSolutions-Capturinganimagefromavirtualmachine) and perform capture by following guidelines for [Linux](https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-linux-capture-image/) and [Windows](https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-windows-capture-image/) virtual machines. As result you will receive a VHD image in your storage account which URL can be used to create  build agents.

## License

Apache 2.0

## Feedback

Please feel free to post an issue in the [TeamCity issue tracker](https://youtrack.jetbrains.com/issues/TW).
