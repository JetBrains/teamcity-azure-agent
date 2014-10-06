/**
 * Created by Sergey.Pak on 9/11/2014.
 */

BS.Clouds.Azure = BS.Clouds.Azure || {
  data: [],
  dataKeys: [ 'cloneType', 'service', 'deployment', 'name', 'namePrefix',
    'vmSize', 'maxInstancesCount', 'os', 'provisionUsername', 'provisionPassword'
  ],
  selectors: {
    imagesSelect: '#imageName',
    cloneBehaviourRadio: ".cloneBehaviourRadio",
    rmImageLink: '.removeImageLink',
    editImageLink: '.editImageLink',
    imagesTableRow: '.imagesTableRow'
  },
  _newImageData: {},
  init: function (refreshOptionsUrl) {
    this.$response = null;
    this.refreshOptionsUrl = refreshOptionsUrl;
    this.$cert = $j('#managementCertificate');
    this.$subscrId = $j('#subscriptionId');

    this.$imageNameDataElem = $j('#imageName');
    this.$serviceNameDataElem = $j('#serviceName');
    this.$deploymentNameDataElem = $j('#deployment');
    this.$osTypeDataElem = $j('#osType');
    this.$vmSizeDataElem = $j('#vmSize');
    this.$namePrefixDataElem = $j('#namePrefix');
    this.$usernameDataElem = $j('#provisionUsername');
    this.$passwordDataElem = $j('#provisionPassword');
    this.$maxInstancesCountdDataElem = $j('#maxInstancesCount');

    this.$fetchOptionsButton = $j('#azureFetchOptionsButton');
    this.$showDialogButton = $j('#azureShowDialogButton');
    this.$dialogSubmitButton = $j('#addImageButton');
    this.$cancelButton = $j('#azureCancelDialogButton');

    this.$imagesDataElem = $j('#images_data');
    this.$imagesTable = $j('#azureImagesTable');
    this.$imagesTableWrapper = $j('.imagesTableWrapper');
    this.$emptyImagesListMessage = $j('.emptyImagesListMessage');
    this.$fetchOptionsError = $j("#error_fetch_options");

    this.selectors.activeCloneBehaviour = this.selectors.cloneBehaviourRadio + ':checked';
    this.loaders = {
      options: $j('.options-loader')
    };

    this._lastImageId = this._imagesDataLength = 0;
    this._bindHandlers();
    this._fetchOptionsClickHandler();
    this._initData();
    this.renderImagesTable();
    console.log(this.data);
  },
  validateServerSettings: function () {
    this.clearErrors();
    return true;
  },
  fetchOptions: function () {
    var _fetchOptionsInProgress = function () {
      return this.fetchOptionsDeferred ?
        this.fetchOptionsDeferred.state() === 'pending' :
        false;
    }.bind(this);

    if ( _fetchOptionsInProgress() || !this.validateServerSettings()) {
      return false;
    }

    this.loaders.options.removeClass('invisible');
    this.fetchOptionsDeferred = $j.Deferred()
      .done(function (response) {
        this.$response = $j(response.responseXML);

        this._fillImages();
        this._fillSelect([
          {
            selector: 'Services:eq(0) Service',
            $target: this.$serviceNameDataElem
          },
          {
            selector: 'Service Deployment',
            $target: this.$deploymentNameDataElem
          },
          {
            selector: 'VmSizes:eq(0) VmSize',
            $target: this.$vmSizeDataElem,
            addLabel: true
          }
        ]);
      }.bind(this))
      .fail(function (errorText) {
        this.addError("Unable to fetch options: " + errorText);
        BS.AzureImageDialog.close();
      }.bind(this))
      .always(function () {
        this.loaders.options.addClass('invisible');
      }.bind(this));

    BS.ajaxRequest(this.refreshOptionsUrl, {
      parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
      onFailure: function (response) {
        this.fetchOptionsDeferred.reject(response.getStatusText());
      }.bind(this),
      onSuccess: function (response) {
        var $response = $j(response.responseXML),
          $errors = $response.find("errors:eq(0) error");

        if ($errors.length) {
          this.fetchOptionsDeferred.reject($errors.text());
        } else {
          this.fetchOptionsDeferred.resolve(response);
        }
      }.bind(this)
    });

    return false;
  },
  _bindHandlers: function () {
    var self = this;

    this.$fetchOptionsButton.on('click', this._fetchOptionsClickHandler.bind(this));

    this.$showDialogButton.on('click', function () {
      if (! this.$showDialogButton.attr('disabled')) {
        this.showDialog();
      }
      return false;
    }.bind(this));

    this.$imagesTable.on('click', this.selectors.rmImageLink, function () {
      var $this = $j(this),
        id = $this.data('imageId'),
        name = self.data[id].name;

      if (confirm('Are you sure you want to remove the image "' + name + '"?')) {
        self.removeImage($this);
      }
      return false;
    });
    this.$imagesTable.on('click', this.selectors.editImageLink, function () {
      self.showEditDialog($j(this));

      return false;
    });

    this.$dialogSubmitButton.on('click', this._submitDialogHandler.bind(this));

    this.$cancelButton.on('click', function () {
      BS.AzureImageDialog.close();

      return false;
    }.bind(this));

    // fetch options if credentials were changed
    this.$cert.add(this.$subscrId).on('change', this._fetchOptionsClickHandler.bind(this));

    /** Image Dialog Props Handlers **/
    this.$imageNameDataElem.on('change', function(e, data) {
      this._nameChangeHandler(e, data);
    }.bind(this));

    // toggle clone options and image name label on clone behaviour change
    $j(this.selectors.cloneBehaviourRadio).on('change', function () {
      this._toggleCloneOptions();
    }.bind(this));

    // filter deployments if service was changed
    this.$serviceNameDataElem.on('change', function (e, data) {
      if (typeof data !== 'undefined') {
        this.$serviceNameDataElem.val(data);
      }

      var service = this.$serviceNameDataElem.val(),
        $deployments = this.$response.find('Service[name="' + service + '"] Deployment'),
        deployments = [];

      $deployments.each(function () {
        deployments.push(this.getAttribute('name'));
      });

      this._newImageData.service = service;
      this._newImageData.deployment = null;

      this.$deploymentNameDataElem.prop('disabled', false).val('').find('option').each(function () {
        $j(this).prop('disabled', deployments.indexOf(this.getAttribute('value')) === -1);
      });

    }.bind(this));

    this.$deploymentNameDataElem
      .add(this.$namePrefixDataElem)
      .add(this.$vmSizeDataElem)
      .add(this.$usernameDataElem)
      .add(this.$passwordDataElem)
      .add(this.$maxInstancesCountdDataElem)
      .on('change', function (e, data) {
        if (typeof data === 'undefined') {
          self._newImageData[this.getAttribute('id')] = this.value;
        } else {
          this.value = data;
        }
      });

  },
  removeImage: function ($elem) {
    delete this.data[$elem.data('imageId')];
    this._imagesDataLength -= 1;
    $elem.parents(this.selectors.imagesTableRow).remove();
    this._saveImagesData();
    this._toggleImagesTable();
  },
  showEditDialog: function ($elem) {
    var imageId = $elem.data('imageId');

    this.showDialog('edit', imageId);

    this.fetchOptionsDeferred
      .then(function () {
        var image = this.data[imageId];

        this.$imageNameDataElem.trigger('change', image.name);
        this.$serviceNameDataElem.trigger('change', image.service);
        this.$deploymentNameDataElem.trigger('change', image.deployment);
        this.$osTypeDataElem.trigger('change', image.os);
        this.$vmSizeDataElem.trigger('change', image.vmSize);
        this.$namePrefixDataElem.trigger('change', image.namePrefix);
        this.$usernameDataElem.trigger('change', image.provisionUsername);
        this.$passwordDataElem.trigger('change', image.provisionPassword);
        this.$maxInstancesCountdDataElem.trigger('change', image.maxInstancesCount);

      }.bind(this));
  },
  showDialog: function (action, imageId) {
    action = action ? 'Edit' : 'Add';
    $j('#AzureDialogTitle').text(action + ' Image');

    this.$dialogSubmitButton.val(action).data('imageId', imageId);

    BS.AzureImageDialog.showCentered();
  },
  _fetchOptionsClickHandler: function () {
    if (this.$cert.val().length && this.$subscrId.val().length) {
      this.fetchOptions();
    }
    return false;
  },
  _nameChangeHandler: function (event, data) {
    if (typeof data !== 'undefined') {
      this.$imageNameDataElem.val(data);
    }

    var imageName = this.$imageNameDataElem.val(),
      type = $j(event.target).find('option:checked').attr('data-type'),
      $image = this.$response.find('Images:eq(0) Image[name="' + imageName + '"]');

    if (! $image.length) {
      $image = this.$response.find('Instance[name="' + imageName + '"]');
    }

    if (! $image.length) {
      return;
    }

    this._newImageData = {
      $image: $image,
      name: imageName,
      type: type,
      os: $image.attr('osType')
    };

    if (type === 'image') {
      this._newImageData.cloneType = 'FRESH_CLONE';
      $j('#cloneBehaviour_START_STOP').prop('disabled', true);
      $j('#cloneBehaviour_FRESH_CLONE').prop('disabled', false).prop('checked', true);

      this.$serviceNameDataElem.prop('disabled', false).children().prop('disabled', false);
      this.$deploymentNameDataElem.prop('disabled', true).children().prop('disabled', false);
    } else if (type === 'instance') {
      this._newImageData.cloneType = 'START_STOP';
      $j('#cloneBehaviour_START_STOP').prop('disabled', false).prop('checked', true);
      $j('#cloneBehaviour_FRESH_CLONE').prop('disabled', true);
      this._newImageData.service = $image.parents('Service').attr('name');
      this.$serviceNameDataElem.prop('disabled', true)
        .find('option[value=' + this._newImageData.service + ']').prop('disabled', false).attr('selected', true).end()
        .find('option[value!=' + this._newImageData.service + ']').prop('disabled', true);

      this._newImageData.deployment = $image.parents('Deployment').attr('name');
      this.$deploymentNameDataElem.prop('disabled', true)
        .find('option[value=' + this._newImageData.deployment + ']').prop('disabled', false).attr('selected', true).end()
        .find('option[value!=' + this._newImageData.deployment + ']').prop('disabled', true);
    }

    $j(this.selectors.cloneBehaviourRadio).trigger('change');
    this._toggleProvisionCredentials();
    this._showImageOsType();
  },
  _fillImages: function () {
    if (!this.$response)
      return;

    this._clearSelectAndAddDefault(this.$imageNameDataElem);

    var $images = this.$response.find('Images:eq(0) Image'),
      $instances = this.$response.find('Instance'),
      self = this;

    $images.each(function () {
      self._appendOption(self.$imageNameDataElem, $j(this).attr('name'), null, 'image');
    });

    $instances.each(function () {
      self._appendOption(self.$imageNameDataElem, $j(this).attr('name'), null, 'instance');
    });

    this.$imageNameDataElem.trigger('change');
  },

  _fillSelect: function (optionsArray) {
    var self = this;

    if (this.$response) {
      optionsArray.forEach(function (options) {
        var $items = self.$response.find(options.selector);

        self._clearSelectAndAddDefault(options.$target);

        $items.each(function () {
          self._appendOption(options.$target, this.getAttribute('name'), options.addLabel && this.getAttribute('label') || null);
        });
      });
    }
  },

  _isClone: function () {
    return $j(this.selectors.activeCloneBehaviour).val() !== 'START_STOP';
  },
  _showImageOsType: function () {
    /**
     * @type {String}
     * @private
     */
    var _osType = this._newImageData.os;

    this.$osTypeDataElem.children().hide();

    if (_osType) {
      this.$osTypeDataElem.find('[title="' + _osType.toLowerCase() + '"]').show();
    }
  },
  _toggleCloneOptions: function () {
    $j('.clone').toggleClass('hidden', !this._isClone());
  },
  _toggleProvisionCredentials: function () {
    $j('.provision').toggle(this._newImageData &&
      this._newImageData.$image &&
      this._newImageData.$image.attr('generalized') == 'true');
  },
  _saveImagesData: function () {
    var imageData = Object.keys(this.data).map(function (imageId) {
      return this.dataKeys.map(function (key) {
        return this.data[imageId][key];
      }.bind(this)).join(';');
    }.bind(this)).join(';X;');

    if (imageData.length) {
      imageData+= ';X;';
    }

    this.$imagesDataElem.val(imageData);
  },
  _updateDataAndView: function (imageId) {
    this.data[imageId] = this._newImageData;
    this._newImageData = {};
    this._saveImagesData();
    this.renderImagesTable();
  },
  _submitDialogHandler: function () {
    if (this.$dialogSubmitButton.val().toLowerCase() === 'edit') {
      this._updateDataAndView(this.$dialogSubmitButton.data('imageId'));
    } else {
      this._imagesDataLength += 1;
      this._updateDataAndView(this._lastImageId++);
    }

    BS.AzureImageDialog.close();

    return false;
  },

  _clearSelectAndAddDefault: function ($select) {
    $select.find('option').remove();
    this._appendOption($select, '', '<Please select a value>');
  },
  _appendOption: function ($target, value, text, type) {
    $target.append($j('<option>').attr('value', value).text(text || value).attr('data-type', type));
  },

  _initData: function () {
    var self = this,
      rawImagesData = this.$imagesDataElem.val() || '',
      imagesData = rawImagesData && rawImagesData.replace(/;X;$/, '').split(';X;') || [];

    this.data = imagesData.reduce(function (accumulator, imageDataStr) {
      var props = imageDataStr.split(';'),
        id;

      // drop images without vmName
      if (props[0].length) {
        id = self._lastImageId++;
        accumulator[id] = {};
        self.dataKeys.forEach(function(key, index) {
          accumulator[id][key] = props[index];
        });
        self._imagesDataLength++;
      }

      return accumulator;
    }, {});
  },
  renderImagesTable: function () {
    this._clearImagesTable();

    if (this._imagesDataLength) {
      Object.keys(this.data).forEach(function (imageId) {
        this._renderImageRow(this.data[imageId], imageId);
      }.bind(this));
    }

    this._toggleImagesTable();
  },
  _imagesTableRowTemplate: $j('<tr class="imagesTableRow">\
<td class="imageName"><div class="sourceIcon"></div><span class="name"></span></td>\
<td class="service hidden"></td>\
<td class="deployment hidden"></td>\
<td class="namePrefix"></td>\
<td class="cloneType hidden"></td>\
<td class="maxInstancesCount"></td>\
<td class="edit"><a href="#" class="editImageLink">edit</a></td>\
<td class="remove"><a href="#" class="removeImageLink">delete</a></td>\
    </tr>'),
  _renderImageRow: function (data, id) {
    var $row = this._imagesTableRowTemplate.clone();

    this.dataKeys.forEach(function (className) {
      $row.find('.' + className).text(data[className]);
    });
    $row.find('.sourceIcon')
      .text(data.cloneType === 'START_STOP' ? 'M' : 'I')
      .attr('title', data.cloneType === 'START_STOP' ? 'Machine' : 'Image');
    $row.find(this.selectors.rmImageLink).data('imageId', id);
    $row.find(this.selectors.editImageLink).data('imageId', id);
    this.$imagesTable.append($row);
  },
  _clearImagesTable: function () {
    this.$imagesTable.find(this.selectors.imagesTableRow).remove();
  },
  _toggleImagesTable: function () {
    var toggle = !!this._imagesDataLength;

    this.$imagesTableWrapper.show();
    this.$emptyImagesListMessage.toggle(!toggle);
    this.$imagesTable.toggle(toggle);
  },
  /**
   * @param {jQuery} [target]
   */
  clearErrors: function (target) {
    (target || this.$fetchOptionsError).empty();
  },
  /**
   * @param {html|String} errorHTML
   * @param {jQuery} [target]
   */
  addError: function (errorHTML, target) {
    (target || this.$fetchOptionsError)
      .append($j("<div>").html(errorHTML));
  },
  resetDataAndDialog: function () {
    this._newImageData = {};

    this.$imageNameDataElem.trigger('change', '');
    this.$serviceNameDataElem.trigger('change', '');
    this.$deploymentNameDataElem.trigger('change', '');
    this.$osTypeDataElem.trigger('change', '');
    this.$vmSizeDataElem.trigger('change', '');
    this.$namePrefixDataElem.trigger('change', '');
    this.$usernameDataElem.trigger('change', '');
    this.$passwordDataElem.trigger('change', '');
    this.$maxInstancesCountdDataElem.trigger('change', '');

    this._toggleCloneOptions();
  }
};

BS.AzureImageDialog = OO.extend(BS.AbstractModalDialog, {
  getContainer: function() {
    return $('AzureImageDialog');
  },
  close: function () {
    BS.Clouds.Azure.resetDataAndDialog();
    BS.AbstractModalDialog.close.apply(this);
  }
});
