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
    this.$deploymentNameDataElem = $j('#deploymentName');
    this.$osTypeDataElem = $j('#osType');
    this.$vmSizeDataElem = $j('#vmSize');
    this.$namePrefixDataElem = $j('#namePrefix');
    this.$usernameDataElem = $j('#provisionUsername');
    this.$passwordDataElem = $j('#provisionPassword');
    this.$maxInstancesCountdDataElem = $j('#maxInstancesCount');

    this.$fetchOptionsButton = $j('#azureFetchOptionsButton');
    this.$addImageButton = $j('#addImageButton');

    this.$imagesDataElem = $j('#images_data');
    this.$imagesTable = $j('#azureImagesTable');
    this.$imagesTableWrapper = $j('.imagesTableWrapper');
    this.$emptyImagesListMessage = $j('.emptyImagesListMessage');

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
    return true;
  },
  fetchOptions: function () {
/*
    this.$response = $j($j.parseXML('<response><Services>'
                   + '<Service name="paksv-lnx-agent"><Deployment name="Ubuntu"><Instance name="Ubuntu"/></Deployment></Service>'
                   + '<Service name="paksv-win-agent"><Deployment name="win"><Instance name="win" /></Deployment></Service>'
                   + '<Service name="tc-srv"><Deployment name="tc-srv"><Instance name="tc-srv" /></Deployment></Service></Services>'
                   + '<Images><Image name="linux-agent-cleaned" generalized="true" osType="Linux" /><Image name="win-image-cleaned" generalized="true" osType="Windows" /></Images><VmSizes><VmSize name="A5" label="A5 (2 cores, 14336 MB)" /><VmSize name="A6" label="A6 (4 cores, 28672 MB)" /><VmSize name="A7" label="A7 (8 cores, 57344 MB)" /><VmSize name="A8" label="A8 (8 cores, 57344 MB)" /><VmSize name="A9" label="A9 (16 cores, 114688 MB)" /><VmSize name="Basic_A0" label="Basic_A0 (1 cores, 768 MB)" /><VmSize name="Basic_A1" label="Basic_A1 (1 cores, 1792 MB)" /><VmSize name="Basic_A2" label="Basic_A2 (2 cores, 3584 MB)" /><VmSize name="Basic_A3" label="Basic_A3 (4 cores, 7168 MB)" /><VmSize name="Basic_A4" label="Basic_A4 (8 cores, 14336 MB)" /><VmSize name="ExtraLarge" label="ExtraLarge (8 cores, 14336 MB)" /><VmSize name="ExtraSmall" label="ExtraSmall (1 cores, 768 MB)" /><VmSize name="Large" label="Large (4 cores, 7168 MB)" /><VmSize name="Medium" label="Medium (2 cores, 3584 MB)" /><VmSize name="Small" label="Small (1 cores, 1792 MB)" /></VmSizes></response>'));
*/
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
        BS.VMWareImageDialog.close();
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
    var $self = this;

    this.$fetchOptionsButton.on('click', this._fetchOptionsClickHandler.bind(this));
    this.$addImageButton.on('click', this._addImage.bind(this));
    // fetch options if credentials were changed
    this.$cert.add(this.$subscrId).on('change', this._fetchOptionsClickHandler.bind(this));

    this.$imageNameDataElem.on('change', this._nameChangeHandler.bind(this));

    // toggle clone options and image name label on clone behaviour change
    $j(this.selectors.cloneBehaviourRadio).on('change', function () {
      $j('.clone').toggleClass('hidden', ! $self._isClone());
    }.bind(this));

    // filter deployments if service was changed
    this.$serviceNameDataElem.change(function () {
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

    this.$deploymentNameDataElem.on('change', function () {
      $self._newImageData.deployment = this.value;
    });

    this.$namePrefixDataElem
      .add(this.$vmSizeDataElem)
      .add(this.$usernameDataElem)
      .add(this.$passwordDataElem)
      .add(this.$maxInstancesCountdDataElem)
      .on('change', function () {
        $self._newImageData[this.getAttribute('id')] = this.value;
      });

  },
  _fetchOptionsClickHandler: function () {
    if (this.$cert.val().length && this.$subscrId.val().length) {
      this.fetchOptions();
    }
    return false;
  },
  _nameChangeHandler: function () {
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
    var _osType = this._newImageData.os;

    this.$osTypeDataElem.children().hide();

    if (_osType) {
      this.$osTypeDataElem.find('[title="' + _osType.toLowerCase() + '"]').show();
    }
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
  _addImage: function () {
    var self = this,
      newImageId = this._lastImageId++;

    this.data[newImageId] = this._newImageData;
    this._imagesDataLength += 1;
    this._newImageData = {};
    this._saveImagesData();
    this.renderImagesTable();

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
  }
};

BS.AzureImageDialog = OO.extend(BS.AbstractModalDialog, {
  getContainer: function() {
    return $('AzureImageDialog');
  }
});
