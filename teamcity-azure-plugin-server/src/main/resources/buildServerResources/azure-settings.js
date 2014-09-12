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