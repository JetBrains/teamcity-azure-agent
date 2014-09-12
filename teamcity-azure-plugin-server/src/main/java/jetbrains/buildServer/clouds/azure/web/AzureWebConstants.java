package jetbrains.buildServer.clouds.azure.web;

/**
 * @author Sergey.Pak
 *         Date: 9/11/2014
 *         Time: 4:04 PM
 */
public class AzureWebConstants {
  public static final String SUBSCRIPTION_ID = "subscriptionId";
  public static final String MANAGEMENT_CERTIFICATE="managementCertificate";
  public static final String IMAGES_DATA="imagesData";
  public static final String SERVICE_NAME="serviceName";
  public static final String DEPLOYMENT_NAME="deploymentName";
  public static final String IMAGE_NAME="imageName";
  public static final String NAME_PREFIX="namePrefix";
  public static final String VM_SIZE="vmSize";
  public static final String OS_TYPE="osType";
  public static final String PROVISION_USERNAME="provisionUsername";
  public static final String PROVISION_PASSWORD="provisionPassword";

  public String getSubscriptionId() {
    return SUBSCRIPTION_ID;
  }

  public String getManagementCertificate() {
    return MANAGEMENT_CERTIFICATE;
  }

  public String getImagesData() {
    return IMAGES_DATA;
  }

  public String getServiceName() {
    return SERVICE_NAME;
  }

  public String getDeploymentName() {
    return DEPLOYMENT_NAME;
  }

  public String getImageName() {
    return IMAGE_NAME;
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
}
