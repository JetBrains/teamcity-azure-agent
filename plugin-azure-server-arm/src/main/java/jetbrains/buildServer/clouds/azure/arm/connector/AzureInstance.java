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

import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

/**
 * Azure cloud instance.
 */
public class AzureInstance implements AbstractInstance {
    private static Map<String, InstanceStatus> PROVISIONING_STATES;
    private static Map<String, InstanceStatus> POWER_STATES;
    private final String myName;
    private String myProvisioningState;
    private Date myStartDate = null;
    private String myPowerState = null;
    private String myIpAddress = null;

    AzureInstance(@NotNull final String name) {
        myName = name;
    }

    @NotNull
    @Override
    public String getName() {
        return myName;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public Date getStartDate() {
        return myStartDate;
    }

    void setStartDate(@NotNull final Date startDate) {
        myStartDate = startDate;
    }

    @Override
    public String getIpAddress() {
        return myIpAddress;
    }

    void setIpAddress(@NotNull final String ipAddress) {
        myIpAddress = ipAddress;
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

    void setProvisioningState(@NotNull final String provisioningState) {
        myProvisioningState = provisioningState;
    }

    void setPowerState(@NotNull final String powerState) {
        myPowerState = powerState;
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
