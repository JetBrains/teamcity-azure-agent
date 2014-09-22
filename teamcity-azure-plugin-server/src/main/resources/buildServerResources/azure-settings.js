/**
 * Created by Sergey.Pak on 9/11/2014.
 */
BS.UploadManagementCertificate = OO.extend(BS.AbstractWebForm, OO.extend(BS.AbstractModalDialog, OO.extend(BS.FileBrowse, {
  getContainer: function() {
    return $('addManagementCertificate');
  },

  formElement: function() {
    return $('uploadCertificateForm');
  },

  refresh: function() {
    BS.reload();
  }
})));

BS.Clouds.Azure = BS.Clouds.Azure || {
  optionsFetched: false,
  _dataKeys: ['serviceName', 'deploymentName', 'imageName', 'namePrefix', 'vmSize' ],
  _extDataKeys: ['osType', 'provisionUsername', 'provisionPassword'],
  _cloneDataKeys: ['namePrefix', 'vmSize'],
  selectors: {
    imagesSelect: '#imageName',
    cloneBehaviourRadio: ".cloneBehaviourRadio"
  },
  init: function(refreshOptionsUrl){
    this.$response = null;
    this.refreshOptionsUrl = refreshOptionsUrl;
    this.$cert = $j('#managementCertificate');
    this.$subscrId = $j('#subscriptionId');
    this.$imageNameDataElem = $j('#imageName');
    this.$serviceNameDataElem = $j('#serviceName');
    this.$vmSizeDataElem = $j('#vmSize');
    this.$deploymentNameDataElem = $j('#deploymentName');
    this.$osTypeDataElem = $j('#osType');
    this.$fetchOptionsButton = $j('#azureFetchOptionsButton');
    this.$fetchOptionsButton.on('click', this._fetchOptionsClickHandler.bind(this));
    this.$imageNameLabel = $j('#label_imageName');
    this.$addImageButton = $j('#addImageButton');

    this.$addImageButton.on('click', this._addImage.bind(this));
    this.$imagesDataElem = $j('#images_data');

    this.loaders = {
      options: $j('.options-loader')
    };

    $j('.' + (this.$imagesDataElem.val().split(';X;')[0].length ? 'imagesTable' : 'emptyImagesListMessage'))
      .removeClass('hidden');

    this._fetchOptionsClickHandler();
    this.$cert.add(this.$subscrId).change(this._fetchOptionsClickHandler.bind(this));

    this.selectors.activeCloneBehaviour = this.selectors.cloneBehaviourRadio + ':checked';

    var $self = this;
    this.$serviceNameDataElem.change(function(){
      $self._fillDeployments($self.$serviceNameDataElem.val());
    }.bind(this));

    this.$deploymentNameDataElem.change(function(){
      $self._fillInstances($self.$serviceNameDataElem.val(), $self.$deploymentNameDataElem.val());
    }.bind(this));

    this.$imageNameDataElem.change(function() {
      var $find = this.$response.find('Images:eq(0) Image[name="' + this.$imageNameDataElem.val() + '"]');

      if ($find) {
        this._toggleProvisionCredentials($find);
        this._showImageOsType($find);
      }
    }.bind(this));

    $j(this.selectors.cloneBehaviourRadio).on('change', function(e, val){
      this._cloneDataKeys.forEach(function(key, index){
        $j('.clone').each(function(){
          this.toggle($self._isClone());
        });
      });
      $self._fillImages();
      //debugger;
      $self.$imageNameLabel.text($self._isClone() ? "Image name:" : "Instance name:");
    }.bind(this));


  },
  validateServerSettings: function () {
    return true;
  },
  fetchOptions: function(){
/*
    this.loaders.options.removeClass('invisible');
    this.$response = $j($j.parseXML('<response><Services>'
                   + '<Service name="paksv-lnx-agent"><Deployment name="Ubuntu"><Instance name="Ubuntu"/></Deployment></Service>'
                   + '<Service name="paksv-win-agent"><Deployment name="win"><Instance name="win" /></Deployment></Service>'
                   + '<Service name="tc-srv"><Deployment name="tc-srv"><Instance name="tc-srv" /></Deployment></Service></Services>'
                   + '<Images><Image name="linux-agent-cleaned" generalized="true" osType="Linux" /><Image name="win-image-cleaned" generalized="true" osType="Windows" /></Images><VmSizes><VmSize name="A5" label="A5 (2 cores, 14336 MB)" /><VmSize name="A6" label="A6 (4 cores, 28672 MB)" /><VmSize name="A7" label="A7 (8 cores, 57344 MB)" /><VmSize name="A8" label="A8 (8 cores, 57344 MB)" /><VmSize name="A9" label="A9 (16 cores, 114688 MB)" /><VmSize name="Basic_A0" label="Basic_A0 (1 cores, 768 MB)" /><VmSize name="Basic_A1" label="Basic_A1 (1 cores, 1792 MB)" /><VmSize name="Basic_A2" label="Basic_A2 (2 cores, 3584 MB)" /><VmSize name="Basic_A3" label="Basic_A3 (4 cores, 7168 MB)" /><VmSize name="Basic_A4" label="Basic_A4 (8 cores, 14336 MB)" /><VmSize name="ExtraLarge" label="ExtraLarge (8 cores, 14336 MB)" /><VmSize name="ExtraSmall" label="ExtraSmall (1 cores, 768 MB)" /><VmSize name="Large" label="Large (4 cores, 7168 MB)" /><VmSize name="Medium" label="Medium (2 cores, 3584 MB)" /><VmSize name="Small" label="Small (1 cores, 1792 MB)" /></VmSizes></response>'));
    this._fillServices();
    this._fillVmSizes();
    this.loaders.options.addClass('invisible');
*/
    var _fetchOptionsInProgress = function () {
      return this.fetchOptionsDeferred ?
        this.fetchOptionsDeferred.state() === 'pending' :
        false;
    };

    if ( _fetchOptionsInProgress() || !this.validateServerSettings()) {
      return false;
    }

    this.fetchOptionsDeferred = $j.Deferred()
      .then(function (response) {
        this.$response = $j(response);

        this._fillServices();
        this._fillVmSizes();
        this._fillImages();
      }.bind(this))
      .fail(function (errorText) {
        this.addError("Unable to fetch options: " + errorText);
        BS.VMWareImageDialog.close();
      }.bind(this));
      .done(function() {
        this.loaders.options.addClass('invisible');
      }.bind(this));

    BS.ajaxRequest(this.refreshOptionsUrl, {
      parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
      onFailure: function (response) {
        this.fetchOptionsDeferred.reject(response.getStatusText());
      }.bind(this),
      onSuccess: function (response){
        var $response = $j(response.responseXML),
          $errors = $response.find("errors:eq(0) error");

        if ($errors) {
          this.fetchOptionsDeferred.reject($errors.text());
        } else {
          this.fetchOptionsDeferred.resolve(response);
        }
      }.bind(this)
    });

    return false;
  },
  _fetchOptionsClickHandler: function(){
    if (this.$cert.val().length && this.$subscrId.val().length) {
      this.fetchOptions();
    }
    return false;
  },
  _appendOption: function ($target, value, text) {
    $target.append($j('<option>').attr('value', value).text(text || value));
  },
  _fillServices: function(){
    if (!this.$response)
      return;
    var self = this,
        $services = this.$response.find('Services:eq(0) Service');

    this._clearSelectAndAddDefault(this.$serviceNameDataElem);

    $services.each(function(){
      self._appendOption(self.$serviceNameDataElem, $j(this).attr('name'));
    });
  },
  _fillVmSizes: function(){
    if (!this.$response)
      return;

    var self = this,
        $vmSizes = this.$response.find('VmSizes:eq(0) VmSize');

    this._clearSelectAndAddDefault(this.$vmSizeDataElem);

    $vmSizes.each(function(){
      self._appendOption(self.$vmSizeDataElem, $j(this).attr('name'), $j(this).attr('label') );
    });
  },
  _fillDeployments: function($serviceName) {
    if (!this.$response)
      return;

    var self = this,
        $deployments = this.$response.find('Service[name="'+$serviceName+'"] Deployment');

    this._clearSelectAndAddDefault(this.$deploymentNameDataElem);

    $deployments.each(function(){
      self._appendOption(self.$deploymentNameDataElem, $j(this).attr('name'));
    });
  },
  _fillInstances: function($serviceName, $deploymentName) {
    if (!this.$response)
      return;

    this._clearSelectAndAddDefault(this.$imageNameDataElem);
    if (!this._isClone()) {
      var self = this,
          $instances = this.$response.find('Service[name="' + $serviceName + '"] Deployment[name="' + $deploymentName + '"] Instance');

      $instances.each(function () {
        self._appendOption(self.$imageNameDataElem, $j(this).attr('name'));
      });
    }
  },
  _fillImages: function() {
    if (!this.$response)
      return;

    this._clearSelectAndAddDefault(this.$imageNameDataElem);
    debugger;
    if (this._isClone()){
      var $images = this.$response.find('Images:eq(0) Image');
      var self = this;

      $images.each(function(){
        self._appendOption(self.$imageNameDataElem, $j(this).attr('name'));
      });
    }
  },
  _isClone: function(){
    return $j(this.selectors.activeCloneBehaviour).val() !== 'START_STOP';
  },
  _showImageOsType: function($imageElem){
    this.$osTypeDataElem.text($imageElem.attr('osType'));
  },
  _toggleProvisionCredentials: function($findElem){
    $j('.provision').toggle($findElem && $findElem.attr('generalized') == 'true');
  },
  _clearSelectAndAddDefault: function($select){
    $select.find('option').remove();
    this._appendOption($select, '', '<Please select a value>');
  },
  _addImage: function(){
    var $imageData,
      reduceFn = function (collector, item) {
        return collector + this['$' + item + 'DataElem'].val() + ';';
      }.bind(this);

    $imageData = this._dataKeys.reduce(reduceFn, '');

    if (this.$osTypeDataElem.is(':visible')) {
      $imageData +=  this._extDataKeys.reduce(reduceFn, '');
    }

    $imageData += 'X;';

    this.$imagesDataElem.val(this.$imagesDataElem.val() + $imageData);
  }

}
