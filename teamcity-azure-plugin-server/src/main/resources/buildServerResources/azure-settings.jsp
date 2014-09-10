<%--
  ~ Copyright 2000-2012 JetBrains s.r.o.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/include.jsp" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="forms" tagdir="/WEB-INF/tags/forms" %>
<%@ taglib prefix="util" uri="/WEB-INF/functions/util" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>


  <tr>
    <th><label for="serverUrl">Server URL: <l:star/></label></th>
    <td><props:textProperty name="serverUrl" className="longField"/></td>
  </tr>

  <tr>
    <th><label for="managementCertificate">Management certificate: <l:star/></label></th>
    <td><props:textProperty name="managementCertificate" className="longField"/></td>
  </tr>

  <tr>
    <th><label for="subscriptionId">Subscription Id: <l:star/></label></th>
    <td><props:textProperty name="subscriptionId" className="longField"/></td>
  </tr>

  <tr>
    <th><label for="images_data">Images (serviceName;deploymentName;imageName;vmPrefix;instanceType;osType;vmSize):</label> </th>
    <td><props:multilineProperty name="images_data"  cols="100" linkTitle="bbbb" rows="10"/> </td>
  </tr>
