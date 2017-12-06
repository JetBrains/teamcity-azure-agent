# TeamCity Azure Cloud Plugins  [![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

Enables TeamCity cloud integration with Microsoft Azure and allows virtual machines usage to scale the pool of build agents.

## Table Of Contents

* [Overview](#overview)
* [Installation](#installation)
* [Classic Plugin](#classic-plugin)
* [Resource Manager Plugin](#resource-manager-plugin)
* [License](#license)
* [Feedback](#feedback)

## Overview

This repository contains plugins for Classic (obsolete) and Resource Manager deployment models.
You can select an appropriate plugin according to the [Microsoft deployment guidelines](https://azure.microsoft.com/en-us/documentation/articles/azure-classic-rm/).

## Installation

You can download the last successful plugin build and install it as an [additional TeamCity plugin](https://confluence.jetbrains.com/display/TCDL/Installing+Additional+Plugins).

### [Resource Manager](#resource-manager-plugin)
[Blog post](https://blog.jetbrains.com/teamcity/2016/04/teamcity-azure-resource-manager/) about the resource manager plugin.

| TeamCity | Status | Download |
|----------|--------|----------|
| 10.x+ | <a href="https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamcityAzurePlugin_BuildResourceManager&branch_TeamcityAzurePlugin=%3Cdefault%3E&guest=1"><img src="https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamcityAzurePlugin_BuildResourceManager),branch:(name:master)/statusIcon.svg" alt=""/></a> | [Download](https://plugins.jetbrains.com/plugin/9260-azure-resource-manager-cloud-support) |

### [Classic](#classic-plugin) (obsolete)
[Blog post](https://blog.jetbrains.com/teamcity/2014/11/introducing-teamcity-azure-plugin-run-builds-in-the-cloud/) about classic plugin.

| TeamCity | Status | Download |
|----------|--------|----------|
| 10.x+ | <a href="https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamcityAzurePlugin_Build&branch_TeamcityAzurePlugin=%3Cdefault%3E&guest=1"><img src="https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamcityAzurePlugin_Build),branch:(name:master)/statusIcon.svg" alt=""/></a> | [Download](https://plugins.jetbrains.com/plugin/9080-azure-classic-cloud-support) |

## Resource Manager Plugin

The plugin supports generalized virtual machine images to start TeamCity build agents. You must use the [Azure AD application](https://azure.microsoft.com/en-us/documentation/articles/resource-group-create-service-principal-portal/#create-application) and the [authentication key](https://azure.microsoft.com/en-us/documentation/articles/resource-group-create-service-principal-portal/#create-an-authentication-key) to enable cloud integration and assign the [_Contributor_ role](https://azure.microsoft.com/en-us/documentation/articles/resource-group-create-service-principal-portal/#assign-application-to-role) for it in your subscription on the [new portal](https://portal.azure.com/).

### Virtual Machine Image Preparation

The plugin supports cloud agents creation from managed images and VHD images. Before you can start using integration, you need to create a new Virtual Machine instance via [Azure portal](https://portal.azure.com). The TeamCity Build Agent [must be installed](https://confluence.jetbrains.com/display/TCDL/TeamCity+Integration+with+Cloud+Solutions#TeamCityIntegrationwithCloudSolutions-PreparingavirtualmachinewithaninstalledTeamCityagent) and set to start automatically. Also, you need to manually point the agent to an existing TeamCity server with the Azure plugin installed to let the build agent download the plugins. Then you should [remove temporary files](https://confluence.jetbrains.com/display/TCDL/TeamCity+Integration+with+Cloud+Solutions#TeamCityIntegrationwithCloudSolutions-Capturinganimagefromavirtualmachine) and perform capture using the following guidelines.

To create a **managed image**, follow the instructions for capturing [generalized Windows](https://docs.microsoft.com/en-us/azure/virtual-machines/windows/capture-image-resource) and [deprovisioned Linux](https://docs.microsoft.com/en-us/azure/virtual-machines/linux/capture-image) virtual machines. As a result, you will get a custom managed image.

To create a **VHD image**, follow the instructions for capturing [Linux](https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-linux-capture-image/) and [Windows](https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-windows-capture-image/) virtual machines. As a result, you will receive a VHD image in your storage account whose URL can be used to create build agents.

Use **ARM template** for fully customizable build agent deployments. To make and set unique ids for created resources, reference the `[parameters('vmName')]` parameter in your template, which will be filled by the generated name on the build agent start.

## Classic Plugin (obsolete)

This plugin supports both Windows and Linux virtual machines and can operate in the following ways.

#### Fresh clone

When TeamCity realizes that it needs more agents, it starts a new instance from the image and deletes it
after it becomes unnecessary (when a defined timeout elapsed). Depending on the image details, the behaviour would vary:

* **Deprovisioned image**: in this case TeamCity will bypass the server URL directly into the created image, so you can use the same image with several TeamCity servers. To create an image, please follow gudelines for [Linux](https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-linux-classic-capture-image/) and [Windows](https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-windows-classic-capture-image/) virtual machines. You also need to provide a username/password to let Azure create a new user on the newly created VM. The credentials must match the Azure credentials requirements: the username should contain at least 5 symbols and the password should match the [passwords policy](http://msdn.microsoft.com/en-us/library/ms161959.aspx).
* **Specialized image**:in this case the user does not prepare an image before capturing. This type of image requires the `serverUrl`
  property in the `buildAgent.properties` to point to the TeamCity server. If the server address changes, the image becomes invalid and you need to create a new one.

#### Start/Stop

It works with the currently existing Virtual Machine instances. TeamCity starts the instance before a build and stops after the build or idle timeout (depending on the profile settings). The machine state is saved. When the TeamCity server url changes, you need to change the `serverUrl` parameter in the `buildAgent.properties` to point to the new server.

### Virtual Machines Preparation

Before you can start using integration, you need to create a new classic Virtual Machine instance. The Teamcity Build Agent must be installed and set to start automatically. Also, you need to manually point the agent to an existing TeamCity server with the Azure plugin installed to let the build agent download the plugins.

To use the _Fresh Clone_ behaviour, you need to create an image from this instance (depending on whether you checked
"I have run the Windows Azure Linux Agent on the virtual machine"/" I have run Sysprep on the virtual machine" checkbox, the image will be Generalized (checked) or Specialized (unchecked) and behaviour will slightly differ).

To use the _Start/Stop_ behaviour, you just need to stop the Virtual Machine Instance before configuring it in TeamCity.

## Common problems

### TeamCity starts virtual machine but agent is not connected

To retrieve configuration details about TeamCity server build agent needs to read the following files:

* `%SYSTEMDRIVE%\AzureData\CustomData.bin` on Windows
* `/var/lib/waagent/ovf-env.xml` in Linux

Please check that user under which TeamCity build agent is running has read access permissions to the mentioned files.

### Problems investigation

To investigate the problems it worth look at the `teamcity-agent.log` [agent log file](https://confluence.jetbrains.com/display/TCDL/Viewing+Build+Agent+Logs) and `teamcity-clouds.log` [server log file](https://confluence.jetbrains.com/display/TCDL/TeamCity+Server+Logs).
You could file an issue in the [TeamCity tracker](#feedback) and privately attach these file for investigation by TeamCity team. 

## License

Apache 2.0

## Feedback

Please feel free to send a PR or file an issue in the [TeamCity issue tracker](https://youtrack.jetbrains.com/newIssue?project=TW&clearDraft=true&summary=Azure+Cloud%3A&c=Assignee+Dmitry.Tretyakov&c=Subsystem+plugins%3A+other&c=tag+Azure+Resource+Manager).
