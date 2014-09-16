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
  _dataKeys: ['serviceName', 'deploymentName', 'imageName', 'namePrefix', 'vmSize' ,'osType', 'provisionUsername', 'provisionPassword'],
  selectors: {
    imagesSelect: '#imageName'
  },
  init: function(refreshOptionsUrl){
    this.refreshOptionsUrl = refreshOptionsUrl;
    this.$imageNameElem = $j('#imageName');
    this.$serviceNameDataElem = $j('#serviceName');
    this.$vmSizeDataElem = $j('#vmSize');
    this.$deploymentsDataElem = $j('#deploymentName');
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

    var $self = this;
    this.$serviceNameDataElem.change(function(){
      $self._fillDeployments($self.$serviceNameDataElem.val());
    });
    this.$imageNameElem.change(function(){
      var $find = $response.find('Images:eq(0) Image[name="' + $self.$imageNameElem.val() + '"]');
      if (!$find)
        return;
      $self._toggleProvisionCredentials($find);
      $self._showImageOsType($find);
    });
    $j('.provision').each(function(){
      this.hide();
    });
  },
  fetchOptions: function(){
    BS.ajaxRequest(this.refreshOptionsUrl, {
      parameters: BS.Clouds.Admin.CreateProfileForm.serializeParameters(),
      onComplete: function () {
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
    this.fetchOptions();
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

    this._clearSelectAndAddDefault(this.$deploymentsDataElem);

    $deployments.each(function(){
      self._appendOption(self.$deploymentsDataElem, $j(this).attr('name'));
    });
  },
  _fillImages: function() {
    if (!$response)
      return;

    this._clearSelectAndAddDefault(this.$imageNameElem);

    var self = this,
        $images = $response.find('Images:eq(0) Image');

    $images.each(function(){
      self._appendOption(self.$imageNameElem, $j(this).attr('name'));
    });
  },
  _showImageOsType: function($find){
    var $osType = $find.attr('osType');
    this.$osTypeDataElem.html($osType);
  },
  _toggleProvisionCredentials: function($find){
    if ($find.attr('generalized') == 'true') {
      $j('.provision').each(function(){
        this.show(100);
      });
    } else {
      $j('.provision').each(function(){
        this.hide(100);
      });
    }
  },
  _clearSelectAndAddDefault: function($select){
    $select.find('option').remove();
    this._appendOption($select, '', '<Please select a value>');
  },
  _addImage: function(){
    var $imageData = this.$serviceNameDataElem.val() + ';' + this.$deploymentsDataElem.val() + ';'
        + this.$imageNameElem.val() + ';' + this.$namePrefixDataElem.val() + ';' + this.$vmSizeDataElem.val() + ';';
    if (this.$osTypeDataElem.is(':visible')){
      $imageData = $imageData + this.$osTypeDataElem.html() + ';'
      + this.$provisionUsernameDataElem.val() + ';' + this.$provisionPasswordDataElem.val() + ';';
    }
    $imageData = $imageData + 'X;';

    this.$imagesDataElem.val(this.$imagesDataElem.val() + $imageData);
  }

}
