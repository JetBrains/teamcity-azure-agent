## Teamcity azure cloud plugin
#### Enables Teamcity cloud integration with Microsoft Azure cloud and allows to automatically create/start/stop/delete virtual machines.

Before you can start using integration, you need to create a Virtual Machine instance (both Windows and Linux instances are supported). Teamcity Build Agent must be installed and
set to start automatically. Also, you need to manually point the agent to existing TC server with Azure plugin installed to let the buildagent download the plugins.

If you'd like to use _Fresh Clone_ behaviour, you need to create an image from this instance (depending on whether you checked
"I have run the Windows Azure Linux Agent on the virtual machine"/" I have run Sysprep on the virtual machine" checkbox) the image will be Generalized (checked) or Specialized
(unchecked) and behaviour would slightly differ).

If you'd like to use _Start/Stop_ behaviour, you just need to stop the Virtual Machine Instance before the configuring it in TC.
#### Supported behaviour

- **Fresh clone** - when teamcity realizes it needs more agents, it starts a new instance from the image and deletes it
after it becomes unnecessary (a defined timeout elapsed). Depending on the image details, the behaviour would vary
  - Deprovisioned image ([Windows](http://azure.microsoft.com/en-us/documentation/articles/virtual-machines-capture-image-windows-server/),
  [Linux](http://azure.microsoft.com/en-us/documentation/articles/virtual-machines-linux-capture-image/))teamcity will bypass server URL directly into the created image,
  so you can use the same image with several TC servers. You also need to provide a username/password to let Azure create a new user on newly created VM. The credentials must match
  the Azure credentials requirements: username >= 5 symbols. Password: [see details](http://msdn.microsoft.com/en-us/library/ms161959.aspx).
  - Specialized image (User didn't prepare image before the capturing - see above) requires the serverUrl
  property in buildAgent.properties to point to the TC server. If the server address changes, the image becomes invalid and you need to create a new one
- **Start/Stop** - works with currently existing Virtual Machine Instances. TC starts the instance before the build and stops after a build or idle timeout
(depending on profile settings). Machine state is saved. When TC Server url changes, you need to change the serverUrl parameter in buildAgent.properties to point to the new server


