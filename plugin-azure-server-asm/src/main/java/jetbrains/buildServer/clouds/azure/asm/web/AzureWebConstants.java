/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
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

package jetbrains.buildServer.clouds.azure.asm.web;

import jetbrains.buildServer.clouds.CloudImageParameters;

/**
 * @author Sergey.Pak
 *         Date: 9/11/2014
 *         Time: 4:04 PM
 */
public class AzureWebConstants {

  public static final String SUBSCRIPTION_ID = "subscriptionId";
  public static final String MANAGEMENT_CERTIFICATE = "managementCertificate";
  public static final String SERVICE_NAME = "serviceName";
  public static final String SOURCE_NAME = "sourceName";
  public static final String NAME_PREFIX = "vmNamePrefix";
  public static final String VM_SIZE = "vmSize";
  public static final String OS_TYPE = "osType";
  public static final String PROVISION_USERNAME = "username";
  public static final String PROVISION_PASSWORD = "password";
  public static final String MAX_INSTANCES_COUNT = "maxInstances";
  public static final String VNET_NAME = "vnetName";
  public static final String PUBLIC_IP = "publicIp";
  public static final String BEHAVIOUR = "behaviour";
  public static final String PROFILE_ID = "profileId";

  public String getVnetName() {
    return VNET_NAME;
  }

  public String getSubscriptionId() {
    return SUBSCRIPTION_ID;
  }

  public String getManagementCertificate() {
    return MANAGEMENT_CERTIFICATE;
  }

  public String getImagesData() {
    return CloudImageParameters.SOURCE_IMAGES_JSON;
  }

  public String getServiceName() {
    return SERVICE_NAME;
  }

  public String getSourceName() {
    return CloudImageParameters.SOURCE_ID_FIELD;
  }

  public String getNamePrefix() {
    return NAME_PREFIX;
  }

  public String getVmSize() {
    return VM_SIZE;
  }

  public String getOsType() {
    return OS_TYPE;
  }

  public String getProvisionUsername() {
    return PROVISION_USERNAME;
  }

  public String getProvisionPassword() {
    return PROVISION_PASSWORD;
  }

  public String getMaxInstancesCount() {
    return MAX_INSTANCES_COUNT;
  }

  public String getPublicIp() {
    return PUBLIC_IP;
  }

  public String getAgentPoolId() {
    return CloudImageParameters.AGENT_POOL_ID_FIELD;
  }

  public String getProfileId() {
    return PROFILE_ID;
  }
}
