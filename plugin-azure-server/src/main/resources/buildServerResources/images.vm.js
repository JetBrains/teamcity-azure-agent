/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

function ArmImagesViewModel($, ko, dialog, config) {
  var self = this;

  self.loadingSubscriptions = ko.observable(false);
  self.loadingRegions = ko.observable(false);
  self.loadingResources = ko.observable(false);
  self.loadingOsType = ko.observable(false);
  self.errorRegions = ko.observable("");
  self.errorResources = ko.observable("");

  // Credentials
  var credentialsTypes = {
    msi: 'msi',
    service: 'service'
  };

  self.credentialsType = ko.observable();
  var requiredForServiceCredentials = {
    required: {
      onlyIf: function () {
        return self.credentialsType() === credentialsTypes.service
      }
    }
  };

  self.credentials = ko.validatedObservable({
    environment: ko.observable().extend({required: true}),
    type: self.credentialsType,
    tenantId: ko.observable('').trimmed().extend(requiredForServiceCredentials),
    clientId: ko.observable('').trimmed().extend(requiredForServiceCredentials),
    clientSecret: ko.observable('').trimmed().extend(requiredForServiceCredentials),
    subscriptionId: ko.observable().extend({required: true}),
    region: ko.observable()
  });

  self.isValidClientData = ko.pureComputed(function () {
    if (!self.credentials().type()) return false;
    return self.credentials().type() === credentialsTypes.msi ||
      self.credentials().tenantId.isValid() &&
      self.credentials().clientId.isValid() &&
      self.credentials().clientSecret.isValid();
  });

  self.isValidCredentials = ko.pureComputed(function () {
    return self.credentials.isValid() && !self.errorRegions();
  });

  // Image details
  var maxLength = 50;

  // Note container can create a storage share with folders like <container-name>-plugins, etc. Max length of such name should be less than 63 characters
  var containerVmPrefixMaxLength = 50;

  var priceDivider = 100000;
  var spotPriceDefault = 0.00001;

  var deployTargets = {
    specificGroup: 'SpecificGroup',
    newGroup: 'NewGroup',
    instance: 'Instance'
  };
  var imageTypes = {
    container: 'Container',
    image: 'Image',
    template: 'Template',
    vhd: 'Vhd',
    galleryImage: 'GalleryImage',
  };
  var osTypes = {
    linux: 'Linux',
    windows: 'Windows'
  };

  self.deployTarget = ko.observable();
  self.imageType = ko.observable();
  self.osType = ko.observable();
  self.spotVm = ko.observable(false);
  self.enableSpotPrice = ko.observable(false);

  var requiredForDeployment = {
    required: {
      onlyIf: function () {
        return self.deployTarget() !== deployTargets.instance
      }
    }
  };

  self.image = ko.validatedObservable({
    deployTarget: self.deployTarget.extend(requiredForDeployment),
    region: ko.observable().extend(requiredForDeployment),
    groupId: ko.observable().extend({
      required: {
        onlyIf: function () {
          return self.deployTarget() === deployTargets.specificGroup
        }
      }
    }),
    imageType: self.imageType.extend(requiredForDeployment),
    imageUrl: ko.observable('').trimmed().extend({rateLimit: 500}).extend({
      required: {
        onlyIf: function () {
          return self.deployTarget() !== deployTargets.instance && self.imageType() === imageTypes.vhd;
        }
      }
    }),
    imageId: ko.observable().extend({
      required: {
        onlyIf: function () {
          return self.deployTarget() !== deployTargets.instance &&
            (self.imageType() === imageTypes.image || self.imageType() === imageTypes.galleryImage || self.imageType() === imageTypes.container);
        }
      }
    }),
    instanceId: ko.observable().extend({
      required: {
        onlyIf: function () {
          return self.deployTarget() === deployTargets.instance;
        }
      }
    }),
    template: ko.observable().extend({
      required: {
        onlyIf: function () {
          return self.deployTarget() !== deployTargets.instance &&
            self.imageType() === imageTypes.template;
        }
      }
    }).extend({
      validation: {
        validator: function (value) {
          if (!value) return true;
          var root;
          try {
            root = JSON.parse(value);
          } catch (error) {
            console.log("Unable to parse template: " + error);
            return false;
          }

          if (!root) {
            console.log("Invalid template object");
            return false;
          }

          if (!root.parameters || !root.parameters.vmName) {
            console.log("No 'vmName' parameter defined");
            return false;
          }

          if (!root.resources || !ko.utils.arrayFirst(root.resources, function (resource) {
              return resource.name === "[parameters('vmName')]";
            })) {
            console.log("No virtual machine resource with name set to vmName parameter");
            return false;
          }

          return true;
        },
        message: "Invalid template value"
      }
    }),
    networkId: ko.observable().extend({
      required: {
        onlyIf: function () {
          return self.deployTarget() !== deployTargets.instance &&
            self.imageType() !== imageTypes.template &&
            self.imageType() !== imageTypes.container
        }
      }
    }),
    subnetId: ko.observable().extend({
      required: {
        onlyIf: function () {
          return self.deployTarget() !== deployTargets.instance &&
            self.imageType() !== imageTypes.template &&
            self.imageType() !== imageTypes.container
        }
      }
    }),
    osType: self.osType.extend({
      required: {
        onlyIf: function () {
          return self.imageType() !== imageTypes.template &&
            self.deployTarget() !== deployTargets.instance
        }
      }
    }),
    maxInstances: ko.observable(1).extend({required: true, min: 0}),
    vmSize: ko.observable().extend({
      required: {
        onlyIf: function () {
          return self.deployTarget() !== deployTargets.instance &&
            self.imageType() !== imageTypes.template &&
            self.imageType() !== imageTypes.container
        }
      }
    }),
    numberCores: ko.observable(1).extend({min: 0}).extend({
      required: {
        onlyIf: function () {
          return self.imageType() === imageTypes.container
        }
      }
    }),
    memory: ko.observable(1).extend({min: 0}).extend({
      required: {
        onlyIf: function () {
          return self.imageType() === imageTypes.container
        }
      }
    }),
    registryUsername: ko.observable(),
    registryPassword: ko.observable(),
    storageAccount: ko.observable(),
    storageAccountType: ko.observable(),
    vmNamePrefix: ko.observable('').trimmed().extend({
      required: {
        onlyIf: function () {
          return self.imageType() !== imageTypes.image ||
            ((self.imageType() === imageTypes.image || self.imageType() === imageTypes.galleryImage) && self.osType() != null)
        }
      }
    }).extend({
      validation: {
        validator: function (value) {
          return self.originalImage && self.originalImage.vmNamePrefix === value || !self.passwords[value];
        },
        message: 'Name prefix should be unique within subscription'
      }
    }).extend({
      validation: {
        validator: function (value) {
          return ko.validation.rules['pattern'].validator(value, /^[a-z0-9]([-a-z0-9]*[a-z0-9])?$/i) && self.imageType() === imageTypes.container ||
            self.imageType() !== imageTypes.container;
        },
        message: 'Name can contain alphanumeric characters and hyphen'
      }
    }).extend({
      validation: {
        validator: function (value) {
          var namePattern = self.osType() === osTypes.linux ? /^[a-z0-9][\.-a-z0-9]*?$/i : /^[a-z0-9][-a-z0-9]*?$/i;
          return ko.validation.rules['pattern'].validator(value, namePattern) && self.imageType() !== imageTypes.container ||
            self.imageType() === imageTypes.container;
        },
        message: function(params, observable) {
          return self.osType() === osTypes.linux
                 ? 'Name can contain alphanumeric characters, hyphen and period'
                 : 'Name can contain alphanumeric characters and hyphen';
        }
      }
    }).extend({
      validation: {
        validator: function (value) {
          return !value || value.length <= maxLength ||
            self.deployTarget() === deployTargets.instance ||
            self.imageType() === imageTypes.container;
        },
        message: 'Please enter no more than ' + maxLength + ' characters.'
      }
    }).extend({
      validation: {
        validator: function (value) {
          return !value || value.length <= containerVmPrefixMaxLength ||
            self.imageType() != imageTypes.container;
        },
        message: 'Please enter no more than ' + containerVmPrefixMaxLength + ' characters.'
      }
    }),
    vmPublicIp: ko.observable(false),
    vmUsername: ko.observable('').trimmed().extend({
      required: {
        onlyIf: function () {
          return self.deployTarget() !== deployTargets.instance &&
            self.imageType() !== imageTypes.template &&
            self.imageType() !== imageTypes.container
        }
      }
    })
      .extend({minLength: 3, maxLength: maxLength}),
    vmPassword: ko.observable('').trimmed().extend({
      required: {
        onlyIf: function () {
          return self.deployTarget() !== deployTargets.instance &&
            self.imageType() !== imageTypes.template &&
            self.imageType() !== imageTypes.container
        }
      }
    })
      .extend({minLength: 8}),
    reuseVm: ko.observable(false),
    agentPoolId: ko.observable().extend({required: true}),
    profileId: ko.observable(),
    customEnvironmentVariables: ko.observable('').extend({
      pattern: {
        message: 'Incorrect environment variables format',
        params: /^(((([a-z_][a-z0-9_]*?)=.*?)|\s*))*$/i
      }
    }),
    customTags: ko.observable('').extend({
      pattern: {
        message: 'Incorrect custom tags format',
        params: /^(((([^<>%&\\\\?/]*?)=.*?)|\s*))*$/i
      }
    }),
    spotVm: self.spotVm,
    enableSpotPrice: self.enableSpotPrice,
    spotPrice: ko.observable().extend({
      required: {
        onlyIf: function () {
          return (self.imageType() === imageTypes.image || self.imageType() === imageTypes.galleryImage) && self.spotVm() && self.enableSpotPrice()
        }
      }
    })
    .extend({min: 0.00001, max: 20000}),
    enableAcceleratedNetworking: ko.observable(false)
  });

  // Data from Azure APIs
  self.subscriptions = ko.observableArray([]);
  self.resourceGroups = ko.observableArray([]);
  self.regions = ko.observableArray([]);
  self.regionName = ko.observable("");
  self.sourceImages = ko.observableArray([]);
  self.instances = ko.observableArray([]);
  self.networks = ko.observableArray([]);
  self.subNetworks = ko.observableArray([]);
  self.vmSizes = ko.observableArray([]);
  self.storageAccounts = ko.observableArray([]);
  self.agentPools = ko.observableArray([]);
  self.osTypes = ko.observableArray([osTypes.linux, osTypes.windows]);
  self.osTypeImage = {
    "Linux": "/img/os/lin-small-bw.png",
    "Windows": "/img/os/win-small-bw.png"
  };
  self.environments = ko.observableArray([
    {id: "AZURE", text: "Azure"},
    {id: "AZURE_CHINA", text: "Azure China"},
    {id: "AZURE_GERMANY", text: "Azure Germany"},
    {id: "AZURE_US_GOVERNMENT", text: "Azure US Government"}
  ]);
  self.storageAccountTypes = ko.observableArray([
    {id: "Standard_LRS", text: "HDD"},
    {id: "Premium_LRS", text: "SSD"}
  ]);

  self.deployTargets = ko.observableArray([
    {id: deployTargets.specificGroup, text: "Specific resource group"},
    {id: deployTargets.newGroup, text: "New resource group"},
    {id: deployTargets.instance, text: "Use existing virtual machine"}
  ]);

  self.imageTypes = ko.observableArray([
    {id: imageTypes.container, text: "Container"},
    {id: imageTypes.image, text: "Image"},
    {id: imageTypes.galleryImage, text: "Shared Image Gallery"},
    {id: imageTypes.template, text: "Template"},
    {id: imageTypes.vhd, text: "VHD"}
  ]);

  self.filteredSourceImages = ko.pureComputed(function() {
    return ko.utils.arrayFilter(self.sourceImages(), function(sourceImage) {
      return sourceImage.isGalleryImage === (self.imageType() === imageTypes.galleryImage);
    });
  });

  // Hidden fields for serialized values
  self.images_data = ko.observable();
  self.passwords_data = ko.observable();

  // Deserialized values
  self.images = ko.observableArray();
  self.instances = ko.observableArray();
  self.nets = {};
  self.passwords = {};

  self.credentialsType.subscribe(function () {
    self.loadSubscriptions();
  });

  self.credentials().environment.subscribe(function () {
    self.loadSubscriptions();
  });

  self.credentials().tenantId.subscribe(function () {
    self.loadSubscriptions();
  });

  self.credentials().clientId.subscribe(function () {
    self.loadSubscriptions();
  });

  self.credentials().clientSecret.subscribe(function () {
    self.loadSubscriptions();
  });

  self.credentials().subscriptionId.subscribe(function (subscriptionId) {
    if (!subscriptionId) return;

    var subscription = ko.utils.arrayFirst(self.subscriptions(), function (item) {
      return item.id === subscriptionId;
    });

    if (!subscription) {
      self.subscriptions([{id: subscriptionId, text: subscriptionId}]);
    }

    self.loadRegions();
  });

  self.image().deployTarget.subscribe(function (deployTarget) {
    if (deployTarget !== deployTargets.specificGroup) {
      self.image().groupId(null);
    }
    if (deployTarget === deployTargets.instance) {
      self.image().registryUsername("");
      self.image().registryPassword("");
      self.image().vmPassword("");
      self.image().vmUsername("");
    }
  });

  self.image().groupId.subscribe(function (groupId) {
    if (!groupId) return;

    var group = ko.utils.arrayFirst(self.resourceGroups(), function (item) {
      return item.text === groupId;
    });

    if (group) {
      self.image().region(group.region);
    }
  });

  self.image().region.subscribe(function (value) {
    if (!value) return;

    var region = ko.utils.arrayFirst(self.regions(), function (item) {
      return item.id === value;
    });

    if (region) {
      self.regionName(region.text)
    }

    loadResourcesByRegion();

    if (self.imageType() === imageTypes.vhd) {
      loadOsType();
    }
  });

  self.image().imageUrl.subscribe(function (url) {
    if (!url) return;

    loadOsType(url);

    if (self.image().vmNamePrefix()) return;

    // Fill vm name prefix from url
    var fileName = self.getFileName(url);
    var vmName = getVmNamePrefix(fileName);
    self.image().vmNamePrefix(vmName);
  });

  self.image().imageId.subscribe(function (value) {
    if (!value) return;

    if (self.image().imageType() === imageTypes.container) {
      if (!self.image().osType()) {
        if (value.indexOf("nanoserver") > 0 || value.indexOf("windowsservercore") > 0) {
          self.image().osType(osTypes.windows);
        }
      }

      if (self.image().vmNamePrefix()) return;

      var groupName = getGroupName(value);
      self.image().vmNamePrefix(groupName);
    } else {
      var image = ko.utils.arrayFirst(self.sourceImages(), function (item) {
        return item.id === value;
      });

      if (image) {
        var osType = image.osType;
        self.osType(osType);
        self.image().osType(osType);
      }

      if (self.image().vmNamePrefix()) return;

      // Fill vm prefix from image ID
      var imageName = self.getFileName(value);
      var vmName = getVmNamePrefix(imageName);
      self.image().vmNamePrefix(vmName);
    }
  });

  self.image().instanceId.subscribe(function (value) {
    if (!value) return;

    // Fill vm prefix from instance ID
    var imageName = self.getFileName(value);
    self.image().vmNamePrefix(imageName);
    self.image().maxInstances(1)
  });

  self.image().vmSize.subscribe(function (value) {
    if (!value) return;

    var storageTypes = [{id: "Standard_LRS", text: "HDD"}];
    if (/^(Basic|Standard)_.+s.*$/i.test(value)) {
      storageTypes.push({id: "Premium_LRS", text: "SSD"});
    }

    self.storageAccountTypes(storageTypes);
  });

  self.image().networkId.subscribe(function (networkId) {
    var subNetworks = self.nets[networkId] || [];
    self.subNetworks(subNetworks);
  });

  ko.pureComputed(function() {
    self.filteredSourceImages();
    return self.imageType();
  })
  .extend({deferred : true, notify: 'always'})
  .subscribe(function (imageType) {
    if (imageType === imageTypes.image || imageType === imageTypes.galleryImage) {
      try {
        var jImageId = $j("#" + config.imageListControlId);
        if (!jImageId.length) return;
        if (jImageId.ufd("instance") === undefined) {
          BS.enableJQueryDropDownFilter(jImageId, {addEmphasis: true});
        } else {
          BS.jQueryDropdown(jImageId, {addEmphasis: true});
        }
      } catch(e) {
        BS.Log.warn(e);
      }
    }
  });

  self.images_data.subscribe(function (data) {
    var images = ko.utils.parseJson(data || "[]");
    var saveValue = false;

    images.forEach(function (image) {
      if (image["source-id"]) {
        image.vmNamePrefix = image["source-id"];
      } else {
        saveValue = true;
      }
      if (image.agent_pool_id) {
        image.agentPoolId = image.agent_pool_id;
      } else {
        saveValue = true;
      }
      image.reuseVm = JSON.parse(image.reuseVm || "false");
      image.vmPublicIp = JSON.parse(image.vmPublicIp || "false");
      image.deployTarget = image.deployTarget || deployTargets.newGroup;
      if (image.deployTarget === deployTargets.newGroup) {
        image.region = image.region || self.credentials().region();
      }
      image.imageType = image.imageType || imageTypes.vhd;
      image.spotVm = JSON.parse(image.spotVm || "false");
      image.enableSpotPrice = JSON.parse(image.enableSpotPrice || "false");
      image.enableAcceleratedNetworking = JSON.parse(image.enableAcceleratedNetworking || "false");
    });

    self.images(images);
    if (saveValue) saveImages();
  });

  self.passwords_data.subscribe(function (data) {
    self.passwords = ko.utils.parseJson(data || "{}");
  });

  self.enableSpotPrice.subscribe(function(data) {
    self.image().spotPrice(spotPriceDefault);
  });

  self.spotVm.subscribe(function(data) {
    self.image().enableSpotPrice(false);
  });

  // Dialogs
  self.originalImage = null;

  self.showDialog = function (data) {
    if (!self.isValidCredentials() || self.loadingSubscriptions() || self.loadingRegions()) {
      return false;
    }

    self.originalImage = data;

    var model = self.image();
    var image = data || {
      deployTarget: deployTargets.specificGroup,
      groupId: self.getResourceGroup(),
      imageType: imageTypes.container,
      imageId: "jetbrains/teamcity-agent",
      vmNamePrefix: "tc-agent",
      osType: osTypes.linux,
      maxInstances: 1,
      numberCores: 2,
      memory: 2,
      customEnvironmentVariables: "",
      customTags: "",
      spotVm: false,
      enableSpotPrice: false,
      spotPrice: spotPriceDefault * priceDivider
    };

    // Pre-fill collections while loading resources
    var imageId = image.imageId;
    if (imageId
      && (image.imageType === imageTypes.image || image.imageType === imageTypes.galleryImage)
      && !ko.utils.arrayFirst(self.sourceImages(), function (item) {
        return item.id === imageId;
      })) {
      self.sourceImages([{
        id: imageId,
        text: image.imageType === imageTypes.image ? self.getFileName(imageId) : self.getGalleryImageName(imageId),
        osType: image.osType,
        isGalleryImage: image.imageType === imageTypes.galleryImage
      }]);
    }

    var instanceId = image.instanceId;
    if (instanceId && !ko.utils.arrayFirst(self.instances(), function (item) {
        return item.id === instanceId;
      })) {
      self.instances([{id: instanceId, text: self.getFileName(instanceId)}]);
    }

    var vmSize = image.vmSize;
    if (vmSize && self.vmSizes.indexOf(vmSize) < 0) {
      self.vmSizes([vmSize]);
    }

    var networkId = image.networkId;
    if (networkId && self.networks.indexOf(networkId) < 0) {
      self.networks([networkId]);
      var subNetworks = [image.subnetId];
      self.nets[networkId] = subNetworks;
      self.subNetworks(subNetworks);
    }

    var storageAccount = image.storageAccount;
    if (storageAccount && self.storageAccounts.indexOf(storageAccount) < 0) {
      self.storageAccounts([storageAccount]);
    }

    model.deployTarget(image.deployTarget || deployTargets.newGroup);
    model.region(image.region);
    model.groupId(image.groupId);
    model.imageType(image.imageType || imageTypes.vhd);
    model.imageUrl(image.imageUrl);
    model.imageId(imageId);
    model.instanceId(instanceId);
    model.osType(image.osType);
    model.networkId(networkId);
    model.subnetId(image.subnetId);
    model.vmSize(vmSize);
    model.numberCores(image.numberCores);
    model.memory(image.memory);
    model.registryUsername(image.registryUsername);
    model.maxInstances(image.maxInstances);
    model.vmNamePrefix(image.vmNamePrefix);
    model.vmPublicIp(image.vmPublicIp);
    model.vmUsername(image.vmUsername);
    model.reuseVm(image.reuseVm);
    model.storageAccount(storageAccount);
    model.storageAccountType(image.storageAccountType);
    model.template(image.template);
    model.agentPoolId(image.agentPoolId);
    model.profileId(image.profileId);
    model.customEnvironmentVariables(image.customEnvironmentVariables);
    model.customTags(image.customTags);
    model.spotVm(image.spotVm);
    model.enableSpotPrice(image.enableSpotPrice);
    model.spotPrice(image.spotPrice != null ? image.spotPrice/priceDivider : undefined);
    model.enableAcceleratedNetworking(image.enableAcceleratedNetworking);

    model.registryPassword("");
    model.vmPassword("");
    if (image.deployTarget !== deployTargets.instance) {
      var key = image.vmNamePrefix;
      var password = Object.keys(self.passwords).indexOf(key) >= 0 ? self.passwords[key] : undefined;
      if (image.imageType === imageTypes.container) {
        model.registryPassword(password);
      } else {
        model.vmPassword(password);
      }
    }

    self.image.errors.showAllMessages(false);
    ko.validation.group(self.image().vmNamePrefix).showAllMessages();

    dialog.showDialog(!self.originalImage);

    return false;
  };

  self.closeDialog = function () {
    dialog.close();
    return false;
  };

  self.saveImage = function () {
    var model = self.image();
    var image = {
      deployTarget: model.deployTarget(),
      groupId: model.groupId(),
      region: model.region(),
      imageType: model.imageType(),
      imageUrl: model.imageUrl(),
      imageId: model.imageId(),
      instanceId: model.instanceId(),
      osType: model.osType(),
      networkId: model.networkId(),
      subnetId: model.subnetId(),
      maxInstances: model.maxInstances(),
      vmNamePrefix: model.vmNamePrefix(),
      vmPublicIp: model.vmPublicIp(),
      vmSize: model.vmSize(),
      numberCores: model.numberCores(),
      memory: model.memory(),
      registryUsername: model.registryUsername(),
      vmUsername: model.vmUsername(),
      reuseVm: model.reuseVm(),
      storageAccount: model.storageAccount(),
      storageAccountType: model.storageAccountType(),
      template: model.template(),
      agentPoolId: model.agentPoolId(),
      profileId: model.profileId(),
      customEnvironmentVariables: model.customEnvironmentVariables(),
      customTags: model.customTags(),
      spotVm: model.spotVm(),
      enableSpotPrice: model.enableSpotPrice(),
      spotPrice: model.spotPrice() != null ? Math.trunc(parseFloat(model.spotPrice())*priceDivider) : undefined,
      enableAcceleratedNetworking: model.enableAcceleratedNetworking()
    };

    var originalImage = self.originalImage;
    if (originalImage) {
      self.images.replace(originalImage, image);
      var originalKey = originalImage.vmNamePrefix;
      delete self.passwords[originalKey];
    } else {
      self.images.push(image);
    }
    self.images_data(JSON.stringify(self.images()));

    var key = image.vmNamePrefix;
    if (image.imageType === imageTypes.container) {
      self.passwords[key] = model.registryPassword();
    } else {
      self.passwords[key] = model.vmPassword();
    }
    self.passwords_data(JSON.stringify(self.passwords));

    dialog.close();
    return false;
  };

  self.deleteImage = function (image) {
    var imageName = "";
    if (image.deployTarget !== deployTargets.instance) {
      switch (image.imageType) {
        case imageTypes.image:
          imageName = 'source image ' + self.getFileName(image.imageId);
          break;
        case imageTypes.template:
          imageName = 'template';
          break;
        case imageTypes.vhd:
          imageName = 'VHD ' + image.imageUrl;
          break;
        case imageTypes.container:
          imageName = 'container ' + image.imageId;
          break;
      }
    } else {
      imageName = 'virtual machine ' + self.getFileName(image.instanceId);
    }

    var message = "Do you really want to delete agent image based on " + imageName + "?";
    var remove = confirm(message);
    if (!remove) {
      return false;
    }

    self.images.remove(image);
    saveImages();

    var key = image.vmNamePrefix;
    delete self.passwords[key];
    self.passwords_data(JSON.stringify(self.passwords));

    return false;
  };

  self.getContextPath = function() {
    return config.contextPath;
  };

  self.getOsImage = function (data) {
    if (!data) return "";

    var image;
    if (ko.unwrap(data.imageType) === imageTypes.template) {
      image = "/img/buildTypeTemplate.png";
    } else if (ko.unwrap(data.deployTarget) === deployTargets.instance) {
      return ko.computed(function() {
        var instance = ko.utils.arrayFirst(self.instances(), function(instance) {
          return instance.id == data.instanceId
        });
        return "url('" + self.getContextPath() +
          (instance !== null && instance.osType && self.osTypeImage[instance.osType] || "/img/buildType.png") +
          "')";
      });
    } else {
      image = self.osTypeImage[ko.unwrap(data.osType)];
    }

    return ko.observable("url('" + self.getContextPath() + image + "')");
  };

  self.loadSubscriptions = function () {
    if (!self.isValidClientData() || self.loadingSubscriptions()) return;

    self.loadingSubscriptions(true);

    var url = getBasePath() + "&resource=subscriptions";
    $.post(url, getCredentials()).then(function (response) {
      var $response = $j(response);
      var errors = getErrors($response);
      if (errors) {
        self.credentials().subscriptionId.setError(errors);
        return;
      } else {
        self.credentials().subscriptionId.clearError();
      }

      var subscriptions = getSubscriptions($response);
      self.subscriptions(subscriptions);
    }, function (error) {
      self.credentials().subscriptionId.setError("Failed to load subscriptions: " + error.message);
      console.log(error);
    }).always(function () {
      self.loadingSubscriptions(false);
    });
  };

  self.loadRegions = function (types) {
    types = types || ['regions', 'resourceGroups', 'instances'];

    var url = types.reduce(function (prev, element) {
      return prev + "&resource=" + element;
    }, getBasePath());

    self.loadingRegions(true);

    $.post(url, getCredentials()).then(function (response) {
      var $response = $j(response);
      var errors = getErrors($response);
      if (errors) {
        self.errorRegions(errors);
        return;
      } else {
        self.errorRegions("");
      }

      if (types.indexOf('regions') >= 0) {
        var regions = getRegions($response);
        self.regions(regions);
      }

      if (types.indexOf('resourceGroups') >= 0) {
        var groups = getResourceGroups($response);
        self.resourceGroups(groups);
      }

      if (types.indexOf('instances') >= 0) {
        var instances = getInstances($response);
        self.instances(instances);
      }
    }, function (error) {
      self.errorRegions("Failed to data: " + error.message);
      console.log(error);
    }).always(function () {
      self.loadingRegions(false);
    });
  };

  self.getResourceGroup = function() {
    var resourceGroups = self.resourceGroups();
    if (resourceGroups.length !== 1) return null;
    return resourceGroups[0].text;
  };

  self.getFileName = function (url) {
    var slashIndex = url.lastIndexOf('/');
    var dotIndex = url.lastIndexOf('.');
    if (dotIndex < slashIndex) {
      dotIndex = url.length;
    }

    if (slashIndex > 0 && dotIndex > 0) {
      return url.substring(slashIndex + 1, dotIndex);
    }

    return "";
  };

  self.getGalleryImageName = function(imageId) {
    if (!imageId) return "N/A";
    var parts = imageId.split("/");
    var getPart = function(partName) {
      partName = partName.toUpperCase();

      for(var index = 0; index < parts.length; index++) {
        var part = parts[index];
        if (partName === part.toUpperCase()) return (index + 1) < parts.length ? parts[index + 1] : null;
      }
      return null;
    };
    return getPart("galleries") + "/" + getPart("images") + "/" + (getPart("versions") || "latest");
  };

  self.setDefaultTemplate = function () {
    self.image().template('{\n' +
      '  "$schema": "https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#",\n' +
      '  "contentVersion": "1.0.0.0",\n' +
      '  "parameters": {\n' +
      '    "vmName": {\n' +
      '      "type": "string",\n' +
      '      "metadata": {\n' +
      '        "description": "This is the Virtual Machine name."\n' +
      '      }\n' +
      '    }\n' +
      '  },\n' +
      '  "variables": {\n' +
      '    "location": "[resourceGroup().location]",\n' +
      '    "nicName": "[concat(parameters(\'vmName\'), \'-net\')]",\n' +
      '    "subnetRef": "..."\n' +
      '  },\n' +
      '  "resources": [\n' +
      '    {\n' +
      '      "apiVersion": "2016-09-01",\n' +
      '      "type": "Microsoft.Network/networkInterfaces",\n' +
      '      "name": "[variables(\'nicName\')]",\n' +
      '      "location": "[variables(\'location\')]",\n' +
      '      "properties": {\n' +
      '        "ipConfigurations": [\n' +
      '          {\n' +
      '            "name": "[concat(parameters(\'vmName\'), \'-config\')]",\n' +
      '            "properties": {\n' +
      '              "privateIPAllocationMethod": "Dynamic",\n' +
      '              "subnet": {\n' +
      '                "id": "[variables(\'subnetRef\')]"\n' +
      '              }\n' +
      '            }\n' +
      '          }\n' +
      '        ]\n' +
      '      }\n' +
      '    },\n' +
      '    {\n' +
      '      "apiVersion": "2016-04-30-preview",\n' +
      '      "type": "Microsoft.Compute/virtualMachines",\n' +
      '      "name": "[parameters(\'vmName\')]",\n' +
      '      "location": "[variables(\'location\')]",\n' +
      '      "dependsOn": [\n' +
      '        "[concat(\'Microsoft.Network/networkInterfaces/\', variables(\'nicName\'))]"\n' +
      '      ],\n' +
      '      "properties": {\n' +
      '        "hardwareProfile": {\n' +
      '          "vmSize": "Standard_A2"\n' +
      '        },\n' +
      '        "osProfile": {\n' +
      '          "computerName": "[parameters(\'vmName\')]",\n' +
      '          "adminUsername": "...",\n' +
      '          "adminPassword": "..."\n' +
      '        },\n' +
      '        "storageProfile": {\n' +
      '          "osDisk": {\n' +
      '            "name": "[concat(parameters(\'vmName\'), \'-os\')]",\n' +
      '            "osType": "...",\n' +
      '            "caching": "ReadWrite",\n' +
      '            "createOption": "FromImage"\n' +
      '          },\n' +
      '          "imageReference": {\n' +
      '            "id": "..."\n' +
      '          }\n' +
      '        },\n' +
      '        "networkProfile": {\n' +
      '          "networkInterfaces": [\n' +
      '            {\n' +
      '              "id": "[resourceId(\'Microsoft.Network/networkInterfaces\', variables(\'nicName\'))]"\n' +
      '            }\n' +
      '          ]\n' +
      '        }\n' +
      '      }\n' +
      '    }\n' +
      '  ]\n' +
      '}\n');
  };

  function saveImages() {
    var images = self.images();
    images.forEach(function (image) {
      image["source-id"] = image.vmNamePrefix;
      delete image.vmNamePrefix;
      image["agent_pool_id"] = image.agentPoolId;
      delete image.agentPoolId;
    });
    self.images_data(JSON.stringify(images));
  }

  function getBasePath() {
    return config.baseUrl +
      "?prop%3Aenvironment=" + encodeURIComponent(self.credentials().environment());
  }

  function getCredentials() {
    var credentials = self.credentials();
    var type = credentials.type();
    if (type === credentialsTypes.msi) {
      return {
        "prop:credentialsType": type,
        "prop:subscriptionId": credentials.subscriptionId()
      };
    } else {
      return {
        "prop:credentialsType": type,
        "prop:subscriptionId": credentials.subscriptionId(),
        "prop:tenantId": credentials.tenantId(),
        "prop:clientId": credentials.clientId(),
        "prop:secure:clientSecret": credentials.clientSecret()
      };
    }
  }

  function loadResourcesByRegion() {
    var region = self.image().region();
    if (!region) return;

    var url = getBasePath() +
      "&resource=vmSizes" +
      "&resource=networks" +
      "&resource=images" +
      "&resource=storageAccounts" +
      "&region=" + region;

    self.loadingResources(true);

    $.post(url, getCredentials()).then(function (response) {
      var $response = $j(response);
      var errors = getErrors($response);
      if (errors) {
        self.errorResources(errors);
        return;
      } else {
        self.errorResources("");
      }

      var images = getImages($response);
      self.sourceImages(images);

      var vmSizes = getVmSizes($response);
      self.vmSizes(vmSizes);

      var storageAccounts = getStorageAccounts($response);
      self.storageAccounts(storageAccounts);

      var networks = getNetworks($response);
      self.networks(networks);
      self.image().networkId.valueHasMutated();
    }, function (error) {
      self.errorResources("Failed to load data: " + error.message);
      console.log(error);
    }).always(function () {
      self.loadingResources(false);
    });
  }

  function loadOsType(newUrl) {
    var imageUrl = newUrl || self.image().imageUrl();
    if (!imageUrl) {
      return;
    }

    var url = getBasePath() +
      "&resource=osType" +
      "&imageUrl=" + encodeURIComponent(imageUrl) +
      "&region=" + self.image().region();

    self.loadingOsType(true);

    $.post(url, getCredentials()).then(function (response) {
      var $response = $j(response);
      var errors = getErrors($response);
      if (errors) {
        self.image().imageUrl.setError(errors);
        return;
      } else {
        self.image().imageUrl.clearError();
      }

      var osType = $response.find("osType").text();
      self.osType(osType);
      self.image().osType(osType);
    }, function (error) {
      self.errorResources("Failed to load data: " + error.message);
      console.log(error);
    }).always(function () {
      self.loadingOsType(false);
    });
  }

  function getErrors($response) {
    var $errors = $response.find("errors:eq(0) error");
    if ($errors.length) {
      return $errors.text();
    }

    return "";
  }

  function getSubscriptions($response) {
    return $response.find("subscriptions:eq(0) subscription").map(function () {
      return {id: $(this).attr("id"), text: $(this).text()};
    }).get();
  }

  function getResourceGroups($response) {
    return $response.find("resourceGroups:eq(0) resourceGroup").map(function () {
      return {region: $(this).attr("region"), text: $(this).text()};
    }).get();
  }

  function getRegions($response) {
    return $response.find("regions:eq(0) region").map(function () {
      return {id: $(this).attr("id"), text: $(this).text()};
    }).get();
  }

  function getInstances($response) {
    return $response.find("instances:eq(0) instance").map(function () {
      return {id: $(this).attr("id"), text: $(this).text(), osType: $(this).attr("osType")};
    }).get();
  }

  function getVmSizes($response) {
    return $response.find("vmSizes:eq(0) vmSize").map(function () {
      return $(this).text();
    }).get();
  }

  function getImages($response) {
    return $response.find("images:eq(0) image").map(function () {
      return {
        id: $(this).attr("id"),
        osType: $(this).attr("osType"),
        isGalleryImage: JSON.parse($(this).attr("isGalleryImage")),
        text: $(this).text()
      };
    }).get();
  }

  function getStorageAccounts($response) {
    return $response.find("storageAccounts:eq(0) storageAccount").map(function () {
      return $(this).text();
    }).get();
  }

  function getNetworks($response) {
    self.nets = {};

    return $response.find("networks:eq(0) network").map(function () {

      var id = $(this).attr("id");
      self.nets[id] = $(this).find("subnet").map(function () {
        return $(this).text();
      }).get();

      return id;
    }).get();
  }

  function getVmNamePrefix(name) {
    if (!name) return "";
    var vhdSuffix = name.indexOf("-osDisk.");
    if (vhdSuffix > 0) name = name.substring(0, vhdSuffix);
    return cleanupVmName(cleanupVmName(name).slice(-maxLength));
  }

  function cleanupVmName(name) {
    return name.replace(/^([^a-z])*|([^\w])*$/g, '');
  }

  function getGroupName(name) {
    return name.toLowerCase()
      .replace(/[^a-z0-9]/g, '-')
      .replace(/-+/g, '-')
      .replace(/(^-|-$)/g, '');
  }

  (function loadAgentPools() {
    var url = config.baseUrl + "?resource=agentPools&projectId=" + encodeURIComponent(config.projectId);

    $.post(url).then(function (response) {
      var $response = $j(response);
      var errors = getErrors($response);
      if (errors) {
        self.errorResources(errors);
        return;
      } else {
        self.errorResources("");
      }

      var agentPools = $response.find("agentPools:eq(0) agentPool").map(function () {
        return {
          id: $(this).attr("id"),
          text: $(this).text()
        };
      }).get();

      self.agentPools(agentPools);
      self.image().agentPoolId.valueHasMutated();
    }, function (error) {
      self.errorResources("Failed to load data: " + error.message);
      console.log(error);
    }).always(function () {
      self.loadingOsType(false);
    });
  })();
}
