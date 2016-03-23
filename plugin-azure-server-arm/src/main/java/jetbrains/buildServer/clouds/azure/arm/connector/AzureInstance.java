/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.azure.arm.connector;

import com.microsoft.azure.management.compute.models.InstanceViewStatus;
import com.microsoft.azure.management.compute.models.VirtualMachine;
import com.microsoft.azure.management.compute.models.VirtualMachineInstanceView;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImage;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joda.time.DateTime;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

/**
 * Azure cloud instance.
 */
public class AzureInstance implements AbstractInstance {
    private static Map<String, InstanceStatus> PROVISIONING_STATES;
    private static Map<String, InstanceStatus> POWER_STATES;
    private static final String PROVISIONING_STATE = "ProvisioningState/";
    private static final String POWER_STATE = "PowerState/";
    private final VirtualMachine myMachine;
    private final AzureCloudImage myImage;
    private final AzureApiConnector myConnector;
    private String myProvisioningState;
    private Date myProvisioningDate;
    private String myPowerState;

    AzureInstance(@NotNull final VirtualMachine machine,
                  @NotNull final AzureCloudImage image,
                  @NotNull final AzureApiConnector connector) {
        myMachine = machine;
        myImage = image;
        myConnector = connector;
        myProvisioningState = myMachine.getProvisioningState();

        final VirtualMachineInstanceView instanceView = myMachine.getInstanceView();
        if (instanceView != null) {
            for (InstanceViewStatus status : instanceView.getStatuses()) {
                final String code = status.getCode();
                if (code.startsWith(PROVISIONING_STATE)) {
                    myProvisioningState = code.substring(PROVISIONING_STATE.length());
                    final DateTime dateTime = status.getTime();
                    if (dateTime != null) {
                        myProvisioningDate = dateTime.toDate();
                    }
                }
                if (code.startsWith(POWER_STATE)) {
                    myPowerState = code.substring(POWER_STATE.length());
                }
            }
        }
    }

    @NotNull
    @Override
    public String getName() {
        return myMachine.getName();
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public Date getStartDate() {
        return myProvisioningDate;
    }

    @Override
    public String getIpAddress() {
        final String groupId = myImage.getImageDetails().getGroupId();
        return myConnector.getIpAddress(groupId, myMachine.getName());
    }

    @Override
    @NotNull
    public InstanceStatus getInstanceStatus() {
        if (!StringUtil.isEmpty(myProvisioningState) && PROVISIONING_STATES.containsKey(myProvisioningState)) {
            return PROVISIONING_STATES.get(myProvisioningState);
        }

        if (!StringUtil.isEmpty(myPowerState) && POWER_STATES.containsKey(myPowerState)) {
            return POWER_STATES.get(myPowerState);
        }

        return InstanceStatus.UNKNOWN;
    }

    @Nullable
    @Override
    public String getProperty(final String name) {
        return null;
    }

    static {
        PROVISIONING_STATES = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        PROVISIONING_STATES.put("InProgress", InstanceStatus.SCHEDULED_TO_START);
        PROVISIONING_STATES.put("Creating", InstanceStatus.SCHEDULED_TO_START);
        PROVISIONING_STATES.put("Deleting", InstanceStatus.SCHEDULED_TO_STOP);
        PROVISIONING_STATES.put("Failed", InstanceStatus.ERROR);
        PROVISIONING_STATES.put("Canceled", InstanceStatus.ERROR);

        POWER_STATES = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        POWER_STATES.put("Starting", InstanceStatus.STARTING);
        POWER_STATES.put("Running", InstanceStatus.RUNNING);
        POWER_STATES.put("Restarting", InstanceStatus.RESTARTING);
        POWER_STATES.put("Stopping", InstanceStatus.STOPPING);
        POWER_STATES.put("Deallocating", InstanceStatus.STOPPING);
        POWER_STATES.put("Stopped", InstanceStatus.STOPPED);
        POWER_STATES.put("Deallocated", InstanceStatus.STOPPED);
    }
}
