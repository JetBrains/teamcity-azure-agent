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

    self.loadingGroups = ko.observable(false);
    self.loadingResources = ko.observable(false);
    self.loadingOsType = ko.observable(false);
    self.errorLoadingGroups = ko.observable("");
    self.errorLoadingResources = ko.observable("");

    // Credentials
    self.credentials = ko.validatedObservable({
        tenantId: ko.observable().extend({required: true}),
        clientId: ko.observable().extend({required: true}),
        clientSecret: ko.observable().extend({required: true}),
        subscriptionId: ko.observable().extend({required: true})
    });

    self.isValidCredentials = ko.pureComputed(function () {
        return self.credentials.isValid() && !self.errorLoadingGroups();
    });

    // Image details
    self.image = ko.validatedObservable({
        groupId: ko.observable().extend({required: true}),
        storageId: ko.observable().extend({required: true}),
        imagePath: ko.observable().extend({required: true}),
        osType: ko.observable().extend({required: true}),
        maxInstances: ko.observable(1).extend({required: true, min: 1}),
        vmSize: ko.observable().extend({required: true}),
        vmNamePrefix: ko.observable().extend({required: true, maxLength: 12}),
        vmPublicIp: ko.observable(false),
        vmUsername: ko.observable().extend({required: true, maxLength: 12}),
        vmPassword: ko.observable().extend({required: true})
    });

    // Data from Azure APIs
    self.groups = ko.observableArray([]);
    self.subscriptions = ko.observableArray([]);
    self.storages = ko.observableArray([]);
    self.vmSizes = ko.observableArray([]);
    self.osTypes = ko.observableArray(["Linux", "Windows"]);

    // Hidden fields for serialized values
    self.images_data = ko.observable();
    self.passwords_data = ko.observable();

    // Deserialized values
    self.images = ko.observableArray();
    self.passwords = {};

    // Reload subscriptions on credentials change
    ko.computed(function () {
        if (!self.credentials().tenantId() || !self.credentials().clientId() || !self.credentials().clientSecret()) {
            return;
        }

        self.loadSubscriptions();
    });

    // Reload groups on credentials change
    ko.computed(function () {
        if (!self.credentials.isValid()) {
            return;
        }

        self.credentials().tenantId();
        self.credentials().clientId();
        self.credentials().clientSecret();
        self.credentials().subscriptionId();

        loadGroups();
    });

    self.image().groupId.subscribe(function (group) {
        if (!group || self.originalImage) return;
        loadResourcesByGroup(group);
    });

    self.image().imagePath.subscribe(function (path) {
        var groupId = self.image().groupId();
        var storageId = self.image().storageId();
        if (!path || !groupId || !storageId) return;

        loadOsType(groupId, storageId, path);
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
        var image = data || {maxInstances: 1, publicId: false};

        model.groupId(image.groupId);
        loadResourcesByGroup(model.groupId()).then(function () {
            model.storageId(image.storageId);
            model.vmSize(image.vmSize);
        });

        model.imagePath(image.imagePath);
        model.osType(image.osType);
        model.maxInstances(image.maxInstances);
        model.vmNamePrefix(image.vmNamePrefix);
        model.vmPublicIp(image.vmPublicIp);
        model.vmUsername(image.vmUsername);

        var key = getSourceKey(image.groupId, image.vmNamePrefix);
        var password = Object.keys(self.passwords).indexOf(key) >= 0 ? self.passwords[key] : undefined;
        model.vmPassword(password);

        self.image.errors.showAllMessages(false);
        dialog.showDialog(!self.originalImage);

        return false;
    };

    self.reloadResources = function (group) {
        loadResourcesByGroup(group);
        return false;
    };

    self.closeDialog = function () {
        dialog.close();
        return false;
    };

    self.saveImage = function () {
        var model = self.image();
        var image = {
            groupId: model.groupId(),
            storageId: model.storageId(),
            imagePath: model.imagePath(),
            osType: model.osType(),
            maxInstances: model.maxInstances(),
            vmNamePrefix: model.vmNamePrefix(),
            vmPublicIp: model.vmPublicIp(),
            vmSize: model.vmSize(),
            vmUsername: model.vmUsername()
        };

        var originalImage = self.originalImage;
        if (originalImage) {
            self.images.replace(originalImage, image);
            var originalKey = getSourceKey(originalImage.groupId, originalImage.vmNamePrefix);
            delete self.passwords[originalKey];
        } else {
            self.images.push(image);
        }
        self.images_data(JSON.stringify(self.images()));

        var key = getSourceKey(image.groupId, image.vmNamePrefix);
        self.passwords[key] = model.vmPassword();
        self.passwords_data(JSON.stringify(self.passwords));

        dialog.close();
        return false;
    };

    self.deleteImage = function (image) {
        var message = "Do you really want to delete agent image based on VHD " + self.getSourceName(image.storageId, image.imagePath) + "?";
        var remove = confirm(message);
        if (!remove) {
            return false;
        }

        self.images.remove(image);
        self.images_data(JSON.stringify(self.images()));

        var key = getSourceKey(image.groupId, image.vmNamePrefix);
        delete self.passwords[key];
        self.passwords_data(JSON.stringify(self.passwords));

        return false;
    };

    self.getSourceName = function (storageId, imagePath) {
        return "https://" + storageId + ".blob.core.windows.net/" + imagePath;
    };

    self.loadSubscriptions = function () {
        self.loadingGroups(true);

        var url = getBasePath() + "&resource=subscriptions";
        $.post(url).then(function (response) {
            var $response = $j(response);
            var errors = getErrors($response);
            if (errors) {
                self.errorLoadingGroups(errors);
                return;
            } else {
                self.errorLoadingGroups("");
            }

            var subscriptions = $response.find("subscriptions:eq(0) subscription").map(function () {
                return {id: $(this).attr("id"), text: $(this).text()};
            }).get();

            self.subscriptions(subscriptions);
        }, function (error) {
            self.errorLoadingGroups("Failed to load data: " + error.message);
            console.log(error);
        }).always(function () {
            self.loadingGroups(false);
        });
    };

    function getSourceKey(groupId, namePrefix) {
        return groupId + "/" + namePrefix;
    }

    function getBasePath() {
        var credentials = self.credentials();
        return baseUrl +
            "?prop%3AtenantId=" + encodeURIComponent(credentials.tenantId()) +
            "&prop%3AclientId=" + encodeURIComponent(credentials.clientId()) +
            "&prop%3Asecure%3AclientSecret=" + encodeURIComponent(credentials.clientSecret()) +
            "&prop%3AsubscriptionId=" + encodeURIComponent(credentials.subscriptionId());
    }

    function loadGroups() {
        self.loadingGroups(true);

        var url = getBasePath() + "&resource=groups";
        $.post(url).then(function (response) {
            var $response = $j(response);
            var errors = getErrors($response);
            if (errors) {
                self.errorLoadingGroups(errors);
                return;
            } else {
                self.errorLoadingGroups("");
            }

            var groups = $response.find("groups:eq(0) group").map(function () {
                return $(this).text();
            }).get();

            self.groups(groups);
        }, function (error) {
            self.errorLoadingGroups("Failed to load data: " + error.message);
            console.log(error);
        }).always(function () {
            self.loadingGroups(false);
        });
    }

    function loadResourcesByGroup(group) {
        self.loadingResources(true);

        var url = getBasePath() + "&resource=storages&resource=vmSizes&group=" + group;
        var request = $.post(url).then(function (response) {
            var $response = $j(response);
            var errors = getErrors($response);
            if (errors) {
                self.errorLoadingResources(errors);
                return;
            } else {
                self.errorLoadingResources("");
            }

            var storages = $response.find("storages:eq(0) storage").map(function () {
                return $(this).text();
            }).get();
            self.storages(storages);

            var vmSizes = $response.find("vmSizes:eq(0) vmSize").map(function () {
                return $(this).text();
            }).get();
            self.vmSizes(vmSizes);
        }, function (error) {
            self.errorLoadingResources("Failed to load data: " + error.message);
            console.log(error);
        });

        request.always(function () {
            self.loadingResources(false);
        });

        return request;
    }

    function loadOsType(group, storage, path) {
        self.loadingOsType(true);

        var url = getBasePath() +
            "&resource=osType&group=" + group +
            "&storage=" + storage +
            "&path=" + encodeURIComponent(path);

        var request = $.post(url).then(function (response) {
            var $response = $j(response);
            var errors = getErrors($response);
            if (errors) {
                self.image().imagePath.setError(errors);
                return;
            } else {
                self.image().imagePath.clearError();
            }

            var osType = $response.find("osType").text();
            self.image().osType(osType);
        }, function (error) {
            self.errorLoadingResources("Failed to load data: " + error.message);
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
}