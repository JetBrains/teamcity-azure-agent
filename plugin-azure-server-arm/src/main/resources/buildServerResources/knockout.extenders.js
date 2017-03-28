/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
