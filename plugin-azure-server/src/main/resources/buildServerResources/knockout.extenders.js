

ko.bindingHandlers.initializeValue = {
  init: function (element, valueAccessor) {
    var value = valueAccessor();
    if (ko.isObservable(value)) {
      value(element.value);
    }
  }
};

ko.subscribable.fn.trimmed = function () {
  return ko.computed({
    read: function () {
      var value = this() || '';
      return value.trim();
    },
    write: function (value) {
      value = value || '';
      this(value.trim());
      this.valueHasMutated();
    },
    owner: this
  }).extend({notify: 'always'});
};

if (!String.prototype.trim) {
  String.prototype.trim = function () {
    return this.replace(/^[\s\uFEFF\xA0]+|[\s\uFEFF\xA0]+$/g, '');
  };
}
