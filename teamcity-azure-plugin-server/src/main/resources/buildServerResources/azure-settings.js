/*
 *
 *  * Copyright 2000-2014 JetBrains s.r.o.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

/**
 * Created by Sergey.Pak on 9/11/2014.
 */

BS.Clouds.Azure = BS.Clouds.Azure || {
  data: [],
  selectors: {
    sourcesSelect: '#sourceName',
    behaviourRadio: '.behaviourRadio',
    rmImageLink: '.removeImageLink',
    editImageLink: '.editImageLink',
    imagesTableRow: '.imagesTableRow'
  },
  _imageData: {},
  _displayedErrors: {},
  _errorIds: ['sourceName', 'serviceName', 'maxInstances', 'vmNamePrefix', 'vmSize'],
  init: function (refreshOptionsUrl) {
    this.$response = null;
    this.refreshOptionsUrl = refreshOptionsUrl;
    this.$cert = $j(BS.Util.escapeId('secure:managementCertificate'));
    this.$subscrId = $j('#subscriptionId');

    this.$sourceNameDataElem = $j('#sourceName');
    this.$serviceNameDataElem = $j('#serviceName');
    this.$osTypeDataElem = $j('#osType');
    this.$vmSizeDataElem = $j('#vmSize');
    this.$vmNamePrefixDataElem = $j('#vmNamePrefix');
    this.$usernameDataElem = $j('#username');
    this.$passwordDataElem = $j('#password');
    this.$maxInstancesDataElem = $j('#maxInstances');

    this.$showDialogButton = $j('#azureShowDialogButton');
    this.$dialogSubmitButton = $j('#addImageButton');
    this.$cancelButton = $j('#azureCancelDialogButton');

    this.$imagesDataElem = $j('#images_data');
    this.$imagesTable = $j('#azureImagesTable');
    this.$imagesTableWrapper = $j('.imagesTableWrapper');
    this.$emptyImagesListMessage = $j('.emptyImagesListMessage');
    this.$fetchOptionsError = $j("#error_fetch_options");

    this.loaders = {
      options: $j('.options-loader')
    };

    this._lastImageId = this._imagesDataLength = 0;
    this._toggleDialogShowButton();
    this._bindHandlers();
    this._fetchOptionsClickHandler();
    this._initData();
    this._showImageOsType();
    this.renderImagesTable();
  },
  _bindHandlers: function () {
    var self = this;

    this.$showDialogButton.on('click', function () {
      if (! this.$showDialogButton.attr('disabled')) {
        this.showDialog();
      }
      return false;
    }.bind(this));

    this.$imagesTable.on('click', this.selectors.rmImageLink, function () {
      var $this = $j(this),
        id = $this.data('imageId'),
        name = self.data[id].sourceName;

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
    this.$cert.add(this.$subscrId).on('keypress', function (e) {
      return e.which !== 13 || this._fetchOptionsClickHandler();
    }.bind(this));

    /** Image Dialog Props Handlers **/
    this.$sourceNameDataElem.on('change', this._nameChangeHandler.bind(this));

    // toggle clone options and image name label on clone behaviour change
    $j(this.selectors.behaviourRadio).on('change', this._behaviourChangeHandler.bind(this));

    this.$serviceNameDataElem.on('change', function (e, data) {
      if (arguments.length === 1) {
        this._imageData.serviceName = this.$serviceNameDataElem.val();
      } else {
        if (this._getSourceType() !== 'instance') {
          this.$serviceNameDataElem.prop('disabled', false).children().prop('disabled', false);
        } else {
          this.$serviceNameDataElem.prop('disabled', true)
            .find('option[value="' + data + '"]').prop('disabled', false).end()
            .find('option[value!="' + data + '"]').prop('disabled', true);
        }
        this.$serviceNameDataElem.val(data);
      }
    }.bind(this));

    this.$vmNamePrefixDataElem
      .add(this.$vmSizeDataElem)
      .add(this.$usernameDataElem)
      .add(this.$maxInstancesDataElem)
      .on('change', function (e, data) {
        if (arguments.length === 1) {
          self._imageData[this.getAttribute('id')] = this.value;
        } else {
          this.value = data;
        }
      });

    this.$passwordDataElem.on('change', function (e, data) {
      if (arguments.length === 1) {
        self._imageData.password = BS.Encrypt.encryptData(this.value, $('publicKey').value);
      } else {
        this.value = data;
      }
    });
  },
  _initData: function () {
    var self = this,
      rawImagesData = this.$imagesDataElem.val() || '[]',
      imagesData = JSON.parse(rawImagesData);

    this.data = imagesData.reduce(function (accumulator, imageDataStr) {
      // drop images without sourceName
      if (imageDataStr.sourceName) {
        accumulator[self._lastImageId++] = imageDataStr;
        self._imagesDataLength++;
      }

      return accumulator;
    }, {});
    this.fetchOptionsDeferred && this.fetchOptionsDeferred.done(function () {
      Object.keys(this.data).forEach(function (i, key) {
        var $image = this._getSourceByName(this.data[key].sourceName);
        this.data[key].$image = $image;
      }.bind(this))
    }.bind(this));
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
  validateServerSettings: function () {
    this.clearErrors();
    return !!(this.$cert.val().length && this.$subscrId.val().length);
  },
  _fetchOptionsInProgress: function () {
    return this.fetchOptionsDeferred ?
      this.fetchOptionsDeferred.state() === 'pending' :
      false;
  },
  fetchOptions: function () {
    if (this._fetchOptionsInProgress() || !this.validateServerSettings()) {
      return false;
    }

    this.loaders.options.removeClass('invisible');
    this._toggleEditLinks();
    this.fetchOptionsDeferred = $j.Deferred()
      .done(function (response) {
        this.$response = $j(response.responseXML).find('response');

        if (this.$response.children().length) {
          this._toggleDialogShowButton(true);
          this._toggleEditLinks(true);
          this._fillImages();
          this._fillSelect([
            {
              selector: 'Services:eq(0) Service',
              $target: this.$serviceNameDataElem
            },
            {
              selector: 'VmSizes:eq(0) VmSize',
              $target: this.$vmSizeDataElem,
              addLabel: true
            }
          ]);
        } else {
          this.addError('Empty response received, it is somewhat suspicious — check your credentials');
        }
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
  showDialog: function (action, imageId) {
    action = action ? 'Edit' : 'Add';
    $j('#AzureDialogTitle').text(action + ' Image');

    this.$dialogSubmitButton.val(action === 'Edit' ? 'Save' : action).data('imageId', imageId);

    var usedMachines = Object.keys(this.data).reduce(function (acc, key) {
      if (this.data[key].behaviour === 'START_STOP' && this._imageData.sourceName !== this.data[key].sourceName) {
        acc.push(this.data[key].sourceName);
      }

      return acc;
    }.bind(this), []);

    this.$sourceNameDataElem.find('optgroup[label="Machines"] option').each(function () {
      $j(this).prop('disabled', usedMachines.indexOf(this.value) !== -1);
    });

    BS.AzureImageDialog.showCentered();
  },
  showEditDialog: function ($elem) {
    var imageId = $elem.data('imageId'),
      image = this.data[imageId];

    this._imageData = image;

    this._triggerDialogChange();
    this.showDialog('edit', imageId);
  },
  removeImage: function ($elem) {
    delete this.data[$elem.data('imageId')];
    this._imagesDataLength -= 1;
    $elem.parents(this.selectors.imagesTableRow).remove();
    this._saveImagesData();
    this._toggleImagesTable();
  },
  _fetchOptionsClickHandler: function () {
    if (this.validateServerSettings()) {
      this.fetchOptions();
    }
    return false;
  },
  _nameChangeHandler: function (event, data) {
    if (arguments.length >= 2) {
      this.$sourceNameDataElem.val(data);
    } else {
      var sourceName = this.$sourceNameDataElem.val(),
        $image = this._getSourceByName(sourceName);

      if ($image.length) {
        $j.extend(true, this._imageData, {
          $image: $image,
          sourceName: sourceName,
          osType: $image.attr('osType')
        });

        if (this._getSourceType() === 'image') {
          this._imageData.behaviour = 'FRESH_CLONE';
          $j('#cloneBehaviour_START_STOP').prop('disabled', true);
          $j('#cloneBehaviour_FRESH_CLONE').prop('disabled', false).prop('checked', true);
          if (! this._imageData.maxInstances) {
            this._imageData.maxInstances = 1;
            this.$maxInstancesDataElem.trigger('change', this._imageData.maxInstances);
          }
        } else if (this._getSourceType() === 'instance') {
          this._imageData.behaviour = 'START_STOP';
          $j('#cloneBehaviour_START_STOP').prop('disabled', false).prop('checked', true);
          $j('#cloneBehaviour_FRESH_CLONE').prop('disabled', true);
          ['maxInstances', 'vmNamePrefix', 'vmSize'].forEach(function (key) {
            delete this._imageData[key];
            this['$' + key + 'DataElem'].val('')
          }.bind(this));
        }

        this._imageData.serviceName = $image.parents('Service').attr('name') || '';
        this.$serviceNameDataElem.trigger('change', this._imageData.serviceName);
      }
    }

    this._showImageOsType();
    this._toggleCloneOptions();
    this._toggleProvisionCredentials();
    BS.AzureImageDialog.recenterDialog();
  },
  _behaviourChangeHandler: function () {
    this._toggleCloneOptions()
  },
  _showImageOsType: function () {
    /**
     * @type {String}
     * @private
     */
    var _osType = this._imageData.osType;

    this.$osTypeDataElem.children().hide();

    if (_osType) {
      this.$osTypeDataElem.find('[title="' + _osType.toLowerCase() + '"]').show();
    }
  },
  _toggleCloneOptions: function () {
    $j('.clone').toggleClass('hidden', !this._isClone());
  },
  _toggleProvisionCredentials: function () {
    var isGeneralized = !!(this._imageData.$image &&
      this._imageData.$image.attr('generalized') == 'true');

    $j('.provision').toggle(isGeneralized);

    if(! isGeneralized) {
      delete this._imageData.username;
      delete this._imageData.password;
      this.$usernameDataElem.add(this.$passwordDataElem).val('');
    }
  },
  _saveImagesData: function () {
    var imageData = Object.keys(this.data).reduce(function (accumulator, id) {
      var _val = $j.extend({}, this.data[id]);

      delete _val.$image;
      accumulator.push(_val);

      return accumulator;
    }.bind(this), []);

    this.$imagesDataElem.val(JSON.stringify(imageData));
  },
  _fillImages: function () {
    if (!this.$response) {
      return;
    }

    this._clearSelectAndAddDefault(this.$sourceNameDataElem);

    var $images = this.$response.find('Images:eq(0) Image'),
      $instances = this.$response.find('Instance'),
      $machinesOptgroup = $j('<optgroup label="Machines"></optgroup>'),
      $imagesOptgroup = $j('<optgroup label="Images"></optgroup>'),
      self = this;

    $images.each(function () {
      self._appendOption($imagesOptgroup, $j(this).attr('name'));
    });

    $instances.each(function () {
      self._appendOption($machinesOptgroup, $j(this).attr('name'));
    });

    this.$sourceNameDataElem
      .append($imagesOptgroup)
      .append($machinesOptgroup);
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
    return this._imageData.behaviour === 'FRESH_CLONE';
  },
  _updateDataAndView: function (imageId) {
    this.data[imageId] = this._imageData;
    this._saveImagesData();
    this.renderImagesTable();
  },
  _submitDialogHandler: function () {
    if (this.validateOptions()) {
      if (this.$dialogSubmitButton.val().toLowerCase() === 'save') {
        this._updateDataAndView(this.$dialogSubmitButton.data('imageId'));
      } else {
        this._imagesDataLength += 1;
        this._updateDataAndView(this._lastImageId++);
      }

      BS.AzureImageDialog.close();
      this._toggleEditLinks(true);
    }

    return false;
  },
  _clearSelectAndAddDefault: function ($select) {
    $select.find('option, optgroup').remove();
    this._appendOption($select, '', '<Please select a value>');
  },
  _appendOption: function ($target, value, text, type) {
    $target.append($j('<option>').attr('value', value).text(text || value).attr('data-type', type));
  },
  _imagesTableRowTemplate: $j('<tr class="imagesTableRow">\
<td class="imageName"><div class="sourceIcon"></div><span class="sourceName"></span></td>\
<td class="serviceName"></td>\
<td class="vmNamePrefix hidden"></td>\
<td class="behaviour hidden"></td>\
<td class="maxInstances"></td>\
<td class="edit"><span class="editImageLink_disabled" title="Editing is available after successful retrieval of data">edit</span><a href="#" class="editImageLink hidden">edit</a></td>\
<td class="remove"><a href="#" class="removeImageLink">delete</a></td>\
    </tr>'),
  _renderImageRow: function (data, id) {
    var $row = this._imagesTableRowTemplate.clone();

    Object.keys(data).forEach(function (className) {
      typeof data[className] === 'string' && $row.find('.' + className).text(data[className]);
    });

    $row.find('.sourceIcon')
      .text(data.behaviour === 'START_STOP' ? 'M' : 'I')
      .attr('title', data.behaviour === 'START_STOP' ? 'Machine' : 'Image');

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
  _toggleDialogShowButton: function (enable) {
    this.$showDialogButton.attr('disabled', !enable);
  },
  _toggleEditLinks: function (enable) {
    $j(this.selectors.editImageLink).toggleClass('hidden', !enable);
    $j(this.selectors.editImageLink + '_disabled').toggleClass('hidden', !!enable);
  },
  /**
   * @param {jQuery} [errorId]
   */
  clearErrors: function (errorId) {
    var target = errorId ? $j('.option-error_' + errorId) : this.$fetchOptionsError;

    if (errorId) {
      this._displayedErrors[errorId] = [];
    }

    target.empty();
  },
  /**
   * @param {html|String} errorHTML
   * @param {jQuery} [target]
   */
  addError: function (errorHTML, target) {
    (target || this.$fetchOptionsError)
      .append($j("<div>").html(errorHTML));
  },
  addOptionError: function (errorKey, optionName) {
    var html;
    this._displayedErrors[optionName] = this._displayedErrors[optionName] || [];

    if (typeof errorKey !== 'string') {
      html = this._errors[errorKey.key];
      Object.keys(errorKey.props).forEach(function(key) {
        html = html.replace('%%'+key+'%%', errorKey.props[key]);
      });
      errorKey = errorKey.key;
    } else {
      html = this._errors[errorKey];
    }

    if (this._displayedErrors[optionName].indexOf(errorKey) === -1) {
      this._displayedErrors[optionName].push(errorKey)
      this.addError(html, $j('.option-error_' + optionName));
    }
  },
  /**
   * @param {string[]} [options]
   */
  clearOptionsErrors: function (options) {
    (options || this._errorIds).forEach(function (optionName) {
      this.clearErrors(optionName);
    }.bind(this));
  },
  resetDataAndDialog: function () {
    this._imageData = {};
    this.clearOptionsErrors();

    if (this.$response) {
      this._triggerDialogChange();
    }
  },
  _triggerDialogChange: function () {
    var image = this._imageData;

    this.$sourceNameDataElem.trigger('change', image.sourceName || '');
    this.$serviceNameDataElem.trigger('change', image.serviceName || '');
    this.$osTypeDataElem.trigger('change', image.osType || '');
    this.$vmSizeDataElem.trigger('change', image.vmSize || '');
    this.$vmNamePrefixDataElem.trigger('change', image.vmNamePrefix || '');
    this.$maxInstancesDataElem.trigger('change', image.maxInstances || '1');
    this.$usernameDataElem.trigger('change', image.username || '');
    this.$passwordDataElem.trigger('change', image.password || '');
  },
  _getSourceByName: function (sourceName) {
    var $image = this.$response.find('Images:eq(0) Image[name="' + sourceName + '"]');

    if (!$image.length) {
      $image = this.$response.find('Instance[name="' + sourceName + '"]');
    }

    return $image;
  },
  _getSourceType: function () {
    return this._imageData.$image ?
      this._imageData.$image.get(0).nodeName.toLowerCase() :
      null;
  },
  _errors: {
    required: 'Required field cannot be left blank',
    imageStart: 'START_STOP behaviour cannot be selected for images',
    positiveNumber: 'Must be positive number',
    nonexistent: 'The %%elem%% &laquo;%%val%%&raquo; does not exist'
  },
  validateOptions: function (options) {
    var maxInstances = this._imageData.maxInstances,
      isValid = true,
      requiredForImage = function (prop) {
        return function () {
          if (this._getSourceType() === 'image' && ! this._imageData[prop]) {
            this.addOptionError('required', prop);
            isValid = false;
          }
        };
      },
      validators = {
        sourceName: function () {
          if ( ! this._imageData.sourceName) {
            this.addOptionError('required', 'sourceName');
            isValid = false;
          } else {
            var $source = this._getSourceByName(this._imageData.sourceName);
            if (! $source.length) {
              this.addOptionError({ key: 'nonexistent', props: { elem: 'source', val: this._imageData.sourceName}}, 'sourceName');
              isValid = false;
            } else {
              if (this._getSourceType() === 'image' && this._imageData.behaviour === 'START_STOP') {
                this.addOptionError('imageStart', 'sourceName');
                isValid = false;
              }
            }
          }
        }.bind(this),
        serviceName: function () {
          if ( ! this._imageData.serviceName) {
            this.addOptionError('required', 'serviceName');
            isValid = false;
          }
        }.bind(this),
        vmNamePrefix: requiredForImage('vmNamePrefix').bind(this),
        vmSize: requiredForImage('vmSize').bind(this),
        maxInstances: function () {
          if (this._getSourceType() === 'image') {
            if (!maxInstances || !$j.isNumeric(maxInstances) || maxInstances < 1) {
              this.addOptionError('positiveNumber', 'maxInstances');
              isValid = false;
            }
          }
        }.bind(this)
      };

    if (options && ! $j.isArray(options)) {
      options = [options];
    }

    this.clearOptionsErrors(options);

    (options || Object.keys(validators)).forEach(function(option) {
      validators[option](); // validators are already bound to parent object
    });

    return isValid;
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
