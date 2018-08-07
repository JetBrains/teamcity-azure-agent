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

More details about TeamCity Azure Resource Manager plugin can be found in the following [blog post](https://blog.jetbrains.com/teamcity/2016/04/teamcity-azure-resource-manager/).

Plugin supports following types of images:
* Managed images
* VHD images
* ARM templates
* [Docker images](https://hub.docker.com/r/jetbrains/teamcity-agent/)

## Installation

You can [download](https://plugins.jetbrains.com/plugin/9260-azure-resource-manager-cloud-support) the plugin build and install it as an [additional plugin](https://confluence.jetbrains.com/display/TCDL/Installing+Additional+Plugins) for TeamCity 10.x+.

## Usage

To configure Azure Resource Manager cloud profile, refer to the [wiki pages](https://github.com/JetBrains/teamcity-azure-agent/wiki).

## Common problems

### TeamCity starts virtual machine but agent is not connected

To retrieve server configuration details, the TeamCity build agent needs to read the following files:

* `%SYSTEMDRIVE%\AzureData\CustomData.bin` on Windows
* `/var/lib/waagent/ovf-env.xml` in Linux

Please check that user under which TeamCity build agent is running has read access permissions to the mentioned files.

### TeamCity starts virtual machine instance but agent is Unauthorized

To resolve it please add the following line in the [buildagent.properties](https://confluence.jetbrains.com/display/TCDL/Build+Agent+Configuration) file:
```
azure.instance.name=<agent_name>
```

### Problems investigation

To investigate the problems it worth look at the `teamcity-agent.log` [agent log file](https://confluence.jetbrains.com/display/TCDL/Viewing+Build+Agent+Logs) and `teamcity-clouds.log` [server log file](https://confluence.jetbrains.com/display/TCDL/TeamCity+Server+Logs).
You could file an issue in the [TeamCity tracker](#feedback) and privately attach these file for investigation by TeamCity team. 

## License

Apache 2.0

## Feedback

Please feel free to send a PR or file an issue in the [TeamCity issue tracker](https://youtrack.jetbrains.com/newIssue?project=TW&clearDraft=true&summary=Azure+Cloud%3A&c=Assignee+Dmitry.Tretyakov&c=Subsystem+plugins%3A+other&c=tag+Azure+Resource+Manager).
