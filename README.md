# TeamCity Azure Cloud Plugin

[![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
<a href="https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamcityAzurePlugin_BuildResourceManager&branch_TeamcityAzurePlugin=%3Cdefault%3E&guest=1"><img src="https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamcityAzurePlugin_BuildResourceManager),branch:(name:master)/statusIcon.svg" alt=""/></a>

Enables TeamCity cloud integration with Microsoft Azure and allows virtual machines usage to scale the pool of build agents.

## Table Of Contents

* [Overview](#overview)
* [Installation](#installation)
* [Usage](#usage)
* [License](#license)
* [Feedback](#feedback)

## Overview

More details about TeamCity Azure Resource Manager plugin could be found in the following [blog post](https://blog.jetbrains.com/teamcity/2016/04/teamcity-azure-resource-manager/).

Plugin supports following types of images:
* Managed images
* VHD images
* ARM templates
* [Docker images](https://hub.docker.com/r/jetbrains/teamcity-agent/)

## Installation

You can [download](https://plugins.jetbrains.com/plugin/9260-azure-resource-manager-cloud-support) the plugin build and install it as an [additional plugin](https://confluence.jetbrains.com/display/TCDL/Installing+Additional+Plugins) for TeamCity 10.x+.

## Usage

The plugin supports generalized virtual machine images to start TeamCity build agents. You must use the [Azure AD application](https://azure.microsoft.com/en-us/documentation/articles/resource-group-create-service-principal-portal/#create-application) and the [authentication key](https://azure.microsoft.com/en-us/documentation/articles/resource-group-create-service-principal-portal/#create-an-authentication-key) to enable cloud integration and assign the [_Contributor_ role](https://azure.microsoft.com/en-us/documentation/articles/resource-group-create-service-principal-portal/#assign-application-to-role) for it in your subscription on the [new portal](https://portal.azure.com/).

### Virtual Machine Image Preparation

The plugin supports cloud agents creation from managed images and VHD images. Before you can start using integration, you need to create a new Virtual Machine instance via [Azure portal](https://portal.azure.com). The TeamCity Build Agent [must be installed](https://confluence.jetbrains.com/display/TCDL/TeamCity+Integration+with+Cloud+Solutions#TeamCityIntegrationwithCloudSolutions-PreparingavirtualmachinewithaninstalledTeamCityagent) and set to start automatically. Also, you need to manually point the agent to an existing TeamCity server with the Azure plugin installed to let the build agent download the plugins. Then you should [remove temporary files](https://confluence.jetbrains.com/display/TCDL/TeamCity+Integration+with+Cloud+Solutions#TeamCityIntegrationwithCloudSolutions-Capturinganimagefromavirtualmachine) and perform capture using the following guidelines.

To create a **managed image**, follow the instructions for capturing [generalized Windows](https://docs.microsoft.com/en-us/azure/virtual-machines/windows/capture-image-resource) and [deprovisioned Linux](https://docs.microsoft.com/en-us/azure/virtual-machines/linux/capture-image) virtual machines. As a result, you will get a custom managed image.

To create a **VHD image**, follow the instructions for capturing [Linux](https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-linux-capture-image/) and [Windows](https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-windows-capture-image/) virtual machines. As a result, you will receive a VHD image in your storage account whose URL can be used to create build agents.

Use **ARM template** for fully customizable build agent deployments. To make and set unique ids for created resources, reference the `[parameters('vmName')]` parameter in your template, which will be filled by the generated name on the build agent start.

## Common problems

### TeamCity starts virtual machine but agent is not connected

To retrieve server configuration details TeamCity build agent needs to read the following files:

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
