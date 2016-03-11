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

import com.microsoft.azure.management.compute.models.NetworkInterfaceReference;
import com.microsoft.azure.management.compute.models.VirtualMachine;
import jetbrains.buildServer.clouds.InstanceStatus;
import jetbrains.buildServer.clouds.base.connector.AbstractInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Azure cloud instance.
 */
public class AzureInstance extends AbstractInstance {

    private final static Map<String, InstanceStatus> STATUS_MAP;
    private final VirtualMachine myMachine;

    public AzureInstance(VirtualMachine machine) {
        super(machine.getName());
        myMachine = machine;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public Date getStartDate() {
        return null;
    } //TODO fix, when API will allow this

    @Override
    public String getIpAddress() {
        final List<NetworkInterfaceReference> networkInterfaces = myMachine.getNetworkProfile().getNetworkInterfaces();
        if (networkInterfaces.size() == 0) {
            return null;
        }

        return networkInterfaces.get(0).getId();
    }

    @Override
    @NotNull
    public InstanceStatus getInstanceStatus() {
        final String state = myMachine.getProvisioningState();
        if (STATUS_MAP.containsKey(state)) {
            return STATUS_MAP.get(state);
        }

        return InstanceStatus.UNKNOWN;
    }

    @Nullable
    @Override
    public String getProperty(final String name) {
        return null;
    }

    static {
        STATUS_MAP = new HashMap<String, InstanceStatus>();
        STATUS_MAP.put("Succeeded", InstanceStatus.RUNNING);
        STATUS_MAP.put("InProgress", InstanceStatus.STARTING);
        STATUS_MAP.put("Creating", InstanceStatus.STARTING);
        STATUS_MAP.put("Failed", InstanceStatus.ERROR);
        STATUS_MAP.put("Canceled", InstanceStatus.ERROR);
    }
}
