/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
        tenantId: ko.observable().extend({required: true}),
        clientId: ko.observable().extend({required: true}),
        clientSecret: ko.observable().extend({required: true}),
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
    var maxLength = 12;
    self.image = ko.validatedObservable({
        imageUrl: ko.observable().extend({required: true}),
        networkId: ko.observable().extend({required: true}),
        subnetId: ko.observable().extend({required: true}),
        osType: ko.observable().extend({required: true}),
        maxInstances: ko.observable(1).extend({required: true, min: 1}),
        vmSize: ko.observable().extend({required: true}),
        vmNamePrefix: ko.observable().extend({required: true, maxLength: maxLength}).extend({
            validation: {
                validator: function (value) {
                    return self.originalImage && self.originalImage.vmNamePrefix == value || !self.passwords[value];
                },
                message: 'Name prefix should be unique within subscription'
            }
        }),
        vmPublicIp: ko.observable(false),
        vmUsername: ko.observable().extend({required: true, maxLength: maxLength}),
        vmPassword: ko.observable().extend({required: true}),
        reuseVm: ko.observable(false)
    });

    // Data from Azure APIs
    self.subscriptions = ko.observableArray([]);
    self.locations = ko.observableArray([]);
    self.networks = ko.observableArray([]);
    self.subNetworks = ko.observableArray([]);
    self.vmSizes = ko.observableArray([]);
    self.osTypes = ko.observableArray(["Linux", "Windows"]);
    self.osType = ko.observable();
    self.osTypeImage = {
        "Linux": "/img/os/lin-small-bw.png",
        "Windows": "/img/os/win-small-bw.png"
    };

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
            return item.id == subscriptionId;
        });
        if (!match) {
            self.subscriptions([{id: subscriptionId, text: subscriptionId}]);
        }

        self.loadLocations();
    });

    self.credentials().location.subscribe(function (location) {
        if (!location) return;

        var match = ko.utils.arrayFirst(self.locations(), function (item) {
            return item.id == location;
        });
        if (!match) {
            self.locations([{id: location, text: location}]);
        }

        loadResourcesByLocation();
    });

    self.image().imageUrl.subscribe(function (url) {
        if (!url) return;

        loadOsType(url);

        // Fill vm name prefix from url
        if (self.image().vmNamePrefix()) return;
        var vmName = getFileName(url).slice(-maxLength);
        self.image().vmNamePrefix(vmName);
    });

    self.image().networkId.subscribe(function (networkId) {
        var subNetworks = self.nets[networkId] || [];
        self.subNetworks(subNetworks);
    });

    self.images_data.subscribe(function (data) {
        var images = ko.utils.parseJson(data || "[]");
        self.images(images);
    });

    self.passwords_data.subscribe(function (data) {
        self.passwords = ko.utils.parseJson(data || "{}");
    });

    // Dialogs
    self.originalImage = null;

    self.showDialog = function (data) {
        self.originalImage = data;

        var model = self.image();
        var image = data || {maxInstances: 1, vmPublicIp: false, reuseVm: true};

        model.imageUrl(image.imageUrl);
        model.osType(image.osType);
        model.networkId(image.networkId);
        model.subnetId(image.subnetId);
        model.vmSize(image.vmSize);
        model.maxInstances(image.maxInstances);
        model.vmNamePrefix(image.vmNamePrefix);
        model.vmPublicIp(image.vmPublicIp);
        model.vmUsername(image.vmUsername);
        model.reuseVm(image.reuseVm);

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
            imageUrl: model.imageUrl(),
            osType: model.osType(),
            networkId: model.networkId(),
            subnetId: model.subnetId(),
            maxInstances: model.maxInstances(),
            vmNamePrefix: model.vmNamePrefix(),
            vmPublicIp: model.vmPublicIp(),
            vmSize: model.vmSize(),
            vmUsername: model.vmUsername(),
            reuseVm: model.reuseVm()
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
        var message = "Do you really want to delete agent image based on VHD " + image.imageUrl + "?";
        var remove = confirm(message);
        if (!remove) {
            return false;
        }

        self.images.remove(image);
        self.images_data(JSON.stringify(self.images()));

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
            "&resource=networks";

        var request = $.post(url).then(function (response) {
            var $response = $j(response);
            var errors = getErrors($response);
            if (errors) {
                self.errorResources(errors);
                return;
            } else {
                self.errorResources("");
            }

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

    function getFileName(url) {
        var slashIndex = url.lastIndexOf('/');
        var dotIndex = url.lastIndexOf('.');
        if (dotIndex < slashIndex) {
            dotIndex = url.length;
        }

        if (slashIndex > 0 && dotIndex > 0) {
            return url.substring(slashIndex + 1, dotIndex);
        }

        return "";
    }
}