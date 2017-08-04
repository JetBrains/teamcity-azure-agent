/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

function ArmImagesViewModel($, ko, baseUrl, dialog) {
  var self = this;

  self.loadingLocations = ko.observable(false);
  self.loadingResources = ko.observable(false);
  self.loadingOsType = ko.observable(false);
  self.errorLocations = ko.observable("");
  self.errorResources = ko.observable("");

  // Credentials
  self.credentials = ko.validatedObservable({
    tenantId: ko.observable('').trimmed().extend({required: true}),
    clientId: ko.observable('').trimmed().extend({required: true}),
    clientSecret: ko.observable('').trimmed().extend({required: true}),
    subscriptionId: ko.observable().extend({required: true}),
    location: ko.observable().extend({required: true})
  });

  self.isValidClientData = ko.pureComputed(function () {
    return self.credentials().tenantId.isValid() &&
      self.credentials().clientId.isValid() &&
      self.credentials().clientSecret.isValid();
  });

  self.isValidCredentials = ko.pureComputed(function () {
    return self.credentials.isValid() && !self.errorLocations();
  });

  // Image details
  var requiredField = 'This field is required.';
  var maxLength = 12;
  var imageTypes = {
    image: 'Image',
    vhd: 'Vhd'
  };

  self.imageType = ko.observable();
  self.image = ko.validatedObservable({
    imageType: self.imageType.extend({required: true}),
    imageUrl: ko.observable('').trimmed().extend({rateLimit: 500}).extend({
      validation: {
        validator: function (value, imageType) {
          return imageType !== imageTypes.vhd || value;
        },
        message: requiredField,
        params: self.imageType
      }
    }),
    imageId: ko.observable().extend({
      validation: {
        validator: function (value, imageType) {
          return imageType !== imageTypes.image || value;
        },
        message: requiredField,
        params: self.imageType
      }
    }),
    networkId: ko.observable().extend({required: true}),
    subnetId: ko.observable().extend({required: true}),
    osType: ko.observable().extend({required: true}),
    maxInstances: ko.observable(1).extend({required: true, min: 1}),
    vmSize: ko.observable().extend({required: true}),
    vmNamePrefix: ko.observable('').trimmed().extend({required: true, maxLength: maxLength}).extend({
      validation: {
        validator: function (value) {
          return self.originalImage && self.originalImage.vmNamePrefix === value || !self.passwords[value];
        },
        message: 'Name prefix should be unique within subscription'
      }
    }).extend({
      pattern: {
        message: 'Name can contain alphanumeric characters, underscore and hyphen',
        params: /^[a-z][a-z0-9_-]*$/i
      }
    }),
    vmPublicIp: ko.observable(false),
    vmUsername: ko.observable('').trimmed().extend({required: true, minLength: 3, maxLength: maxLength}),
    vmPassword: ko.observable('').trimmed().extend({required: true, minLength: 8}),
    reuseVm: ko.observable(false),
    agentPoolId: ko.observable().extend({required: true}),
    profileId: ko.observable()
  });

  // Data from Azure APIs
  self.subscriptions = ko.observableArray([]);
  self.locations = ko.observableArray([]);
  self.sourceImages = ko.observableArray([]);
  self.networks = ko.observableArray([]);
  self.subNetworks = ko.observableArray([]);
  self.vmSizes = ko.observableArray([]);
  self.agentPools = ko.observableArray([]);
  self.osTypes = ko.observableArray(["Linux", "Windows"]);
  self.osType = ko.observable();
  self.osTypeImage = {
    "Linux": "/img/os/lin-small-bw.png",
    "Windows": "/img/os/win-small-bw.png"
  };

  self.imageTypes = ko.observableArray([
    {id: imageTypes.image, text: "Image"},
    {id: imageTypes.vhd, text: "VHD"}
  ]);

  // Hidden fields for serialized values
  self.images_data = ko.observable();
  self.passwords_data = ko.observable();

  // Deserialized values
  self.images = ko.observableArray();
  self.nets = {};
  self.passwords = {};

  // Reload subscriptions on credentials change
  ko.computed(function () {
    if (!self.credentials().tenantId() || !self.credentials().clientId() || !self.credentials().clientSecret()) {
      return;
    }

    self.loadSubscriptions();
  });

  self.credentials().subscriptionId.subscribe(function (subscriptionId) {
    if (!subscriptionId) return;

    var match = ko.utils.arrayFirst(self.subscriptions(), function (item) {
      return item.id === subscriptionId;
    });
    if (!match) {
      self.subscriptions([{id: subscriptionId, text: subscriptionId}]);
    }

    self.loadLocations();
  });

  self.credentials().location.subscribe(function (location) {
    if (!location) return;

    var match = ko.utils.arrayFirst(self.locations(), function (item) {
      return item.id === location;
    });
    if (!match) {
      self.locations([{id: location, text: location}]);
    }

    loadResourcesByLocation();
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

  self.image().imageId.subscribe(function (imageId) {
    if (!imageId) return;

    var images = self.sourceImages().filter(function (image) {
      return image.id === imageId;
    });

    if (images.length) {
      var osType = images[0].osType;
      self.osType(osType);
      self.image().osType(osType);
    }

    if (self.image().vmNamePrefix()) return;

    // Fill vm imageId prefix from image imageId
    var imageName = self.getFileName(imageId);
    var vmName = getVmNamePrefix(imageName);
    self.image().vmNamePrefix(vmName);
  });

  self.image().networkId.subscribe(function (networkId) {
    var subNetworks = self.nets[networkId] || [];
    self.subNetworks(subNetworks);
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
      image.reuseVm = JSON.parse(image.reuseVm);
      image.vmPublicIp = JSON.parse(image.vmPublicIp);
      image.imageType = image.imageType || imageTypes.vhd
    });

    self.images(images);
    if (saveValue) saveImages();
  });

  self.passwords_data.subscribe(function (data) {
    self.passwords = ko.utils.parseJson(data || "{}");
  });

  // Dialogs
  self.originalImage = null;

  self.showDialog = function (data) {
    self.originalImage = data;

    var model = self.image();
    var image = data || {
      imageType: imageTypes.image,
      maxInstances: 1,
      vmPublicIp: false,
      reuseVm: true
    };

    model.imageType(image.imageType || imageTypes.vhd);
    model.imageUrl(image.imageUrl);
    model.imageId(image.imageId);
    model.osType(image.osType);
    model.networkId(image.networkId);
    model.subnetId(image.subnetId);
    model.vmSize(image.vmSize);
    model.maxInstances(image.maxInstances);
    model.vmNamePrefix(image.vmNamePrefix);
    model.vmPublicIp(image.vmPublicIp);
    model.vmUsername(image.vmUsername);
    model.reuseVm(image.reuseVm);
    model.agentPoolId(image.agent_pool_id);
    model.profileId(image.profileId);

    var key = image.vmNamePrefix;
    var password = Object.keys(self.passwords).indexOf(key) >= 0 ? self.passwords[key] : undefined;
    model.vmPassword(password);

    self.image.errors.showAllMessages(false);
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
      imageType: model.imageType(),
      imageUrl: model.imageUrl(),
      imageId: model.imageId(),
      osType: model.osType(),
      networkId: model.networkId(),
      subnetId: model.subnetId(),
      maxInstances: model.maxInstances(),
      vmNamePrefix: model.vmNamePrefix(),
      vmPublicIp: model.vmPublicIp(),
      vmSize: model.vmSize(),
      vmUsername: model.vmUsername(),
      reuseVm: model.reuseVm(),
      agentPoolId: model.agentPoolId(),
      profileId: model.profileId()
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
    self.passwords[key] = model.vmPassword();
    self.passwords_data(JSON.stringify(self.passwords));

    dialog.close();
    return false;
  };

  self.deleteImage = function (image) {
    var imageName = image.imageType === imageTypes.image
      ? 'source image ' + self.getFileName(image.imageId)
      : 'VHD ' + image.imageUrl;
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

  self.getOsImage = function (osType) {
    return "url('" + self.osTypeImage[osType] + "')";
  };

  self.loadSubscriptions = function () {
    if (!self.isValidClientData()) return;

    self.loadingLocations(true);

    var url = getBasePath() + "&resource=subscriptions";
    $.post(url).then(function (response) {
      var $response = $j(response);
      var errors = getErrors($response);
      if (errors) {
        self.errorLocations(errors);
        return;
      } else {
        self.errorLocations("");
      }

      var subscriptions = getSubscriptions($response);
      self.subscriptions(subscriptions);
    }, function (error) {
      self.errorLocations("Failed to load data: " + error.message);
      console.log(error);
    }).always(function () {
      self.loadingLocations(false);
    });
  };

  self.loadLocations = function () {
    var subscription = self.credentials().subscriptionId();
    if (!subscription) return;

    self.loadingLocations(true);

    var url = getBasePath() +
      "&resource=locations" +
      "&subscription=" + subscription;
    $.post(url).then(function (response) {
      var $response = $j(response);
      var errors = getErrors($response);
      if (errors) {
        self.errorLocations(errors);
        return;
      } else {
        self.errorLocations("");
      }

      var locations = getLocations($response);
      self.locations(locations);
    }, function (error) {
      self.errorLocations("Failed to load data: " + error.message);
      console.log(error);
    }).always(function () {
      self.loadingLocations(false);
    });
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
    var credentials = self.credentials();
    return baseUrl +
      "?prop%3AtenantId=" + encodeURIComponent(credentials.tenantId()) +
      "&prop%3AclientId=" + encodeURIComponent(credentials.clientId()) +
      "&prop%3Asecure%3AclientSecret=" + encodeURIComponent(credentials.clientSecret()) +
      "&prop%3AsubscriptionId=" + encodeURIComponent(credentials.subscriptionId()) +
      "&prop%3Alocation=" + encodeURIComponent(credentials.location());
  }

  function loadResourcesByLocation() {
    self.loadingResources(true);
    var url = getBasePath() +
      "&resource=vmSizes" +
      "&resource=networks" +
      "&resource=images";

    var request = $.post(url).then(function (response) {
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

      var networks = getNetworks($response);
      self.networks(networks);
      self.image().networkId.valueHasMutated();
    }, function (error) {
      self.errorResources("Failed to load data: " + error.message);
      console.log(error);
    });

    request.always(function () {
      self.loadingResources(false);
    });

    return request;
  }

  function loadOsType(imageUrl) {
    self.loadingOsType(true);

    var url = getBasePath() +
      "&resource=osType" +
      "&imageUrl=" + encodeURIComponent(imageUrl);

    var request = $.post(url).then(function (response) {
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
    });

    request.always(function () {
      self.loadingOsType(false);
    });

    return request;
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

  function getLocations($response) {
    return $response.find("locations:eq(0) location").map(function () {
      return {id: $(this).attr("id"), text: $(this).text()};
    }).get();
  }

  function getVmSizes($response) {
    return $response.find("vmSizes:eq(0) vmSize").map(function () {
      return $(this).text();
    }).get();
  }

  function getImages($response) {
    return $response.find("images:eq(0) image").map(function () {
      return {id: $(this).attr("id"), osType: $(this).attr("osType"), text: $(this).text()};
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
    return cleanupVmName(cleanupVmName(name).substr(0, maxLength));
  }

  function cleanupVmName(name) {
    return name.replace(/^([^a-z])*|([^\w])*$/g, '');
  }

  (function loadAgentPools() {
    var url = baseUrl + "?resource=agentPools";

    var request = $.post(url).then(function (response) {
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
    });

    request.always(function () {
      self.loadingOsType(false);
    });

    return request;
  })();
}
