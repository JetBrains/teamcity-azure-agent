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
  selectors: {
    imagesSelect: '#imageName'
  },
  init: function(refreshOptionsUrl){
    this.refreshOptionsUrl = refreshOptionsUrl;
    this.$cert = $j('#managementCertificate');
    this.$subscrId = $j('#subscriptionId');
    this.$imageNameDataElem = $j('#imageName');
    this.$serviceNameDataElem = $j('#serviceName');
    this.$vmSizeDataElem = $j('#vmSize');
    this.$deploymentNameDataElem = $j('#deploymentName');
    this.$osTypeDataElem = $j('#osType');
    $response = null;
    this.$fetchOptionsButton = $j('#azureFetchOptionsButton');
    this.$fetchOptionsButton.on('click', this._fetchOptionsClickHandler.bind(this));

    this.$addImageButton = $j('#addImageButton');
    this.$addImageButton.on('click', this._addImage.bind(this));

    this.$imagesDataElem = $j('#images_data');
    this.$namePrefixDataElem = $j("#namePrefix");
    this.$provisionUsernameDataElem = $j("#provisionUsername");
    this.$provisionPasswordDataElem = $j("#provisionPassword");
    this.loaders = {
      options: $j('.options-loader')
    };

    $j('.' + (this.$imagesDataElem.val().split(';X;')[0].length ? 'imagesTable' : 'emptyImagesListMessage'))
      .removeClass('hidden');

    this._fetchOptionsClickHandler();
    this.$cert.add(this.$subscrId).change(this._fetchOptionsClickHandler.bind(this));

    this.$serviceNameDataElem.change(function(){
      this._fillDeployments(this.$serviceNameDataElem.val());
    }.bind(this));

    this.$imageNameDataElem.change(function() {
      var $find = $response.find('Images:eq(0) Image[name="' + this.$imageNameDataElem.val() + '"]');

      if ($find) {
        this._toggleProvisionCredentials($find);
        this._showImageOsType($find);
      }
    }.bind(this));
  },
  fetchOptions: function(){
    this.loaders.options.removeClass('invisible');

    BS.ajaxRequest(this.refreshOptionsUrl, {
      parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
      onComplete: function () {
        this.loaders.options.addClass('invisible');
      }.bind(this),
      onFailure: function (response) {
        alert('Failure: ' + response.getStatusText());
      }.bind(this),
      onSuccess: function (response){
        $response = $j(response.responseXML);
        this._fillServices();
        this._fillVmSizes();
        this._fillImages();
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
    if (!$response)
      return;
    var self = this,
        $services = $response.find('Services:eq(0) Service');

    this._clearSelectAndAddDefault(this.$serviceNameDataElem);

    $services.each(function(){
      self._appendOption(self.$serviceNameDataElem, $j(this).attr('name'));
    });
  },
  _fillVmSizes: function(){
    if (!$response)
      return;

    var self = this,
        $vmSizes = $response.find('VmSizes:eq(0) VmSize');

    this._clearSelectAndAddDefault(this.$vmSizeDataElem);

    $vmSizes.each(function(){
      self._appendOption(self.$vmSizeDataElem, $j(this).attr('name'), $j(this).attr('label') );
    });
  },
  _fillDeployments: function($serviceName) {
    if (!$response)
      return;

    var self = this,
        $deployments = $response.find('Service[name="'+$serviceName+'"] Deployment');

    this._clearSelectAndAddDefault(this.$deploymentNameDataElem);

    $deployments.each(function(){
      self._appendOption(self.$deploymentNameDataElem, $j(this).attr('name'));
    });
  },
  _fillImages: function() {
    if (!$response)
      return;

    this._clearSelectAndAddDefault(this.$imageNameDataElem);

    var self = this,
        $images = $response.find('Images:eq(0) Image');

    $images.each(function(){
      self._appendOption(self.$imageNameDataElem, $j(this).attr('name'));
    });
  },
  _showImageOsType: function($find){
    this.$osTypeDataElem.text($find.attr('osType'));
  },
  _toggleProvisionCredentials: function($find){
    $j('.provision').toggle($find && $find.attr('generalized') == 'true');
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
